package wangdaye.com.geometricweather.weather.services;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import javax.inject.Inject;

import wangdaye.com.geometricweather.BuildConfig;
import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper;
import wangdaye.com.geometricweather.weather.apis.ApihzApi;
import wangdaye.com.geometricweather.weather.converters.ApihzResultConverter;
import wangdaye.com.geometricweather.weather.json.apihz.ApihzWeatherResult;

/**
 * apihz.cn (中国天气网) weather service.
 *
 * Primary path is the by-place endpoint ({@code tqyb.php}, province + place), so a saved city gets
 * its own weather; the by-IP endpoint ({@code tqybip.php}) is only a fallback for a location with no
 * usable name (or whose place the API doesn't recognise). Both return the same shape.
 *
 * Name handling (from the API's quirks): a trailing 区 on a place, or 市 on a municipality province,
 * makes the lookup fail, while a province-agnostic place lookup is the most tolerant. So
 * {@link #fetchForLocation} normalises the names and tries: province+place -> place-only -> IP.
 * Search ({@link #requestLocation(Context, String)}) uses the same place-only lookup, which is why
 * Chinese city/district names resolve directly.
 */
public class ApihzWeatherService extends WeatherService {

    private final ApihzApi mApi;
    private AsyncHelper.Controller mController;

    private static final TimeZone CN_TZ = TimeZone.getTimeZone("Asia/Shanghai");

    @Inject
    public ApihzWeatherService(ApihzApi api) {
        mApi = api;
    }

    @Override
    public void requestWeather(Context context, @NonNull Location location,
                               @NonNull RequestWeatherCallback callback) {
        mController = AsyncHelper.runOnIO(() -> {
            try {
                ApihzWeatherResult result = fetchForLocation(location);
                Weather weather = usable(result)
                        ? ApihzResultConverter.convert(context, location, result) : null;
                if (weather != null) {
                    callback.requestWeatherSuccess(Location.copy(location, weather));
                } else {
                    callback.requestWeatherFailed(location);
                }
            } catch (Exception e) {
                callback.requestWeatherFailed(location);
            }
        });
    }

    @NonNull
    @Override
    public List<Location> requestLocation(Context context, String query) {
        List<Location> list = new ArrayList<>();
        try {
            String place = normPlace(query != null ? query.trim() : "");
            if (TextUtils.isEmpty(place)) {
                return list;
            }
            ApihzWeatherResult result = tryPlace(null, place);
            if (usable(result)) {
                list.add(buildLocationFromResult(result));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    @Override
    public void requestLocation(Context context, @NonNull Location location,
                                @NonNull RequestLocationCallback callback) {
        // Keep the location's identity as-is; requestWeather resolves it by province/place.
        List<Location> locationList = new ArrayList<>();
        locationList.add(location);
        callback.requestLocationSuccess(location.getCityName(context), locationList);
    }

    @Override
    public void cancel() {
        if (mController != null) {
            mController.cancel();
        }
    }

    // ---- fetching ----

    @Nullable
    private ApihzWeatherResult fetchForLocation(Location location) {
        String sheng = normSheng(location.getProvince());
        // Try the more specific district first, then the city. The API's district coverage is
        // partial (e.g. 海淀 resolves but 天河/渝中 don't), so an unknown district must fall back
        // to the city before the IP fallback.
        String[] places = {normPlace(location.getDistrict()), normPlace(location.getCity())};

        String tried = null;
        for (String place : places) {
            if (TextUtils.isEmpty(place) || place.equals(tried)) {
                continue;
            }
            tried = place;
            if (!TextUtils.isEmpty(sheng)) {
                ApihzWeatherResult r = tryPlace(sheng, place);
                if (usable(r)) {
                    return r;
                }
            }
            ApihzWeatherResult r = tryPlace(null, place);
            if (usable(r)) {
                return r;
            }
        }
        return tryIp();
    }

    @Nullable
    private ApihzWeatherResult tryPlace(@Nullable String sheng, String place) {
        try {
            return mApi.getWeatherByPlace(BuildConfig.APIHZ_ID, BuildConfig.APIHZ_KEY,
                    sheng, place, 7, 1, 1).execute().body();
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private ApihzWeatherResult tryIp() {
        try {
            return mApi.getWeatherByIp(BuildConfig.APIHZ_ID, BuildConfig.APIHZ_KEY, 7, 1, 1)
                    .execute().body();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean usable(@Nullable ApihzWeatherResult result) {
        return result != null && result.code != null && result.code == 200;
    }

    // ---- location building / name normalisation ----

    @NonNull
    private static Location buildLocationFromResult(ApihzWeatherResult r) {
        String country = !TextUtils.isEmpty(r.guo) ? r.guo : "中国";
        String province = r.sheng != null ? r.sheng : "";
        String city = !TextUtils.isEmpty(r.shi) ? r.shi : (r.name != null ? r.name : "");
        boolean isChina = "中国".equals(country);
        String cityId = province + city;
        if (TextUtils.isEmpty(cityId)) {
            cityId = city;
        }
        return new Location(
                cityId,
                parseFloat(r.lat),
                parseFloat(r.lon),
                isChina ? CN_TZ : TimeZone.getDefault(),
                country,
                province,
                city,
                "",
                null,
                WeatherSource.APIHZ,
                false,
                false,
                isChina
        );
    }

    // A trailing 区 breaks the place lookup ("海淀区" -> 400, "海淀" -> ok).
    private static String normPlace(String s) {
        if (TextUtils.isEmpty(s)) {
            return "";
        }
        String t = s.trim();
        if (t.endsWith("区")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    // A municipality province with 市 breaks the lookup ("北京市" -> 400, "北京" -> ok);
    // 省/自治区 are accepted as-is.
    private static String normSheng(String s) {
        if (TextUtils.isEmpty(s)) {
            return "";
        }
        String t = s.trim();
        if (t.endsWith("市")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private static float parseFloat(@Nullable String s) {
        if (TextUtils.isEmpty(s)) {
            return 0f;
        }
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return 0f;
        }
    }
}
