package wangdaye.com.geometricweather.weather.services;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import javax.inject.Inject;

import okhttp3.ResponseBody;
import retrofit2.Call;
import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper;
import wangdaye.com.geometricweather.weather.apis.CmaApi;
import wangdaye.com.geometricweather.weather.converters.CmaResultConverter;
import wangdaye.com.geometricweather.weather.json.cma.CmaLocationResult;
import wangdaye.com.geometricweather.weather.json.cma.CmaNationalResult;
import wangdaye.com.geometricweather.weather.json.cma.CmaWeatherResult;

/**
 * China Meteorological Administration (weather.cma.cn) weather service.
 *
 * Station-ID based (cityId = CMA stationId). No API key. Notes:
 * - The autocomplete search matches pinyin/English, so Chinese queries are transliterated first.
 * - CMA has no coordinates->station endpoint, so a current-position (or a location re-sourced
 *   from another provider whose cityId is not a CMA station) is resolved to the geographically
 *   nearest station via the national station map. IP-based resolution is only a last resort,
 *   because a mobile IP can be far from the user (e.g. carrier egress in another province).
 */
public class CmaWeatherService extends WeatherService {

    private final CmaApi mApi;
    private final List<Call<?>> mCalls = new ArrayList<>();
    private AsyncHelper.Controller mController;

    private static final TimeZone CN_TZ = TimeZone.getTimeZone("Asia/Shanghai");

    // National station list, cached for the process lifetime (≈2400 entries).
    private static final Object STATIONS_LOCK = new Object();
    private static volatile List<Station> sStations;

    private static class Station {
        final String id;
        final String name;
        final double latitude;
        final double longitude;

        Station(String id, String name, double latitude, double longitude) {
            this.id = id;
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    @Inject
    public CmaWeatherService(CmaApi api) {
        mApi = api;
    }

    @Override
    public void requestWeather(Context context, @NonNull Location location,
                               @NonNull RequestWeatherCallback callback) {
        mController = AsyncHelper.runOnIO(() -> {
            try {
                CmaWeatherResult result = tryGetWeather(location.getCityId());

                // The stored cityId may not be a CMA station (e.g. the location was re-sourced
                // from another provider, or its weather/view returns data:"" for an unknown id).
                // Resolve a real station by coordinates / name / IP and retry.
                if (!usable(result)) {
                    Location station = resolveStation(context, location);
                    String stationId = station != null ? station.getCityId() : null;
                    if (!TextUtils.isEmpty(stationId)
                            && !stationId.equals(location.getCityId())) {
                        result = tryGetWeather(stationId);
                    }
                }

                if (!usable(result)) {
                    callback.requestWeatherFailed(location);
                    return;
                }

                // Use the station the data actually came from for the hourly scrape.
                String hourlyStationId = result.data.location != null
                        && !TextUtils.isEmpty(result.data.location.id)
                        ? result.data.location.id : location.getCityId();

                // Hourly forecast: best-effort HTML scrape; failure just yields no hourly data.
                String hourlyHtml = null;
                try {
                    Call<ResponseBody> htmlCall = mApi.getHourlyHtml(hourlyStationId);
                    mCalls.add(htmlCall);
                    ResponseBody body = htmlCall.execute().body();
                    if (body != null) {
                        hourlyHtml = body.string();
                    }
                } catch (Exception ignored) {
                }

                Weather weather = CmaResultConverter.convert(context, location, result, hourlyHtml);
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
        List<Location> locationList = new ArrayList<>();
        try {
            String q = toPinyinQuery(query);
            if (TextUtils.isEmpty(q)) {
                return locationList;
            }
            CmaLocationResult result = mApi.getLocation(q, 20).execute().body();
            if (result != null && result.data != null) {
                for (String entry : result.data) {
                    Location loc = parseSearchEntry(entry);
                    if (loc != null) {
                        locationList.add(loc);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return locationList;
    }

    @Override
    public void requestLocation(Context context, @NonNull Location location,
                                @NonNull RequestLocationCallback callback) {
        mController = AsyncHelper.runOnIO(() -> {
            try {
                Location resolved = resolveStation(context, location);

                if (resolved != null) {
                    List<Location> list = new ArrayList<>();
                    list.add(resolved);
                    callback.requestLocationSuccess(location.getFormattedId(), list);
                } else {
                    callback.requestLocationFailed(location.getFormattedId());
                }
            } catch (Exception e) {
                callback.requestLocationFailed(location.getFormattedId());
            }
        });
    }

    @Override
    public void cancel() {
        if (mController != null) {
            mController.cancel();
        }
        for (Call<?> c : mCalls) {
            c.cancel();
        }
        mCalls.clear();
    }

    // ---- station resolution ----

    // CMA returns data:"" (an empty string, not an object) for unknown station ids, which makes
    // Gson throw; swallow it so the caller can fall back to resolving a real station.
    @Nullable
    private CmaWeatherResult tryGetWeather(String stationId) {
        try {
            Call<CmaWeatherResult> call = mApi.getWeather(stationId);
            mCalls.add(call);
            return call.execute().body();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean usable(@Nullable CmaWeatherResult result) {
        return result != null && result.data != null
                && result.data.daily != null && !result.data.daily.isEmpty();
    }

    // Resolve a CMA station for a location whose cityId is not (or no longer) a CMA station:
    // nearest station by GPS (most reliable for current position), else the reverse-geocoded
    // place name via pinyin autocomplete, else CMA's IP-based resolution (last resort, since a
    // mobile IP can be far from the user). Returns null if all three fail.
    @Nullable
    private Location resolveStation(Context context, Location location) {
        Station nearest = findNearestStation(context,
                location.getLatitude(), location.getLongitude());
        if (nearest != null) {
            return buildStationLocation(nearest.id, nearest.name,
                    location.getLatitude(), location.getLongitude());
        }
        String name = firstNonEmpty(
                location.getDistrict(), location.getCity(), location.getProvince());
        if (!TextUtils.isEmpty(name)) {
            List<Location> found = requestLocation(context, name);
            if (!found.isEmpty()) {
                return found.get(0);
            }
        }
        CmaWeatherResult wr = tryGetWeather("");
        if (wr != null && wr.data != null && wr.data.location != null) {
            return buildLocationFromView(wr.data.location);
        }
        return null;
    }

    @Nullable
    private Station findNearestStation(Context context, double lat, double lon) {
        // ignore the (0,0) placeholder coordinates used for search results.
        if (Math.abs(lat) < 0.01 && Math.abs(lon) < 0.01) {
            return null;
        }
        List<Station> stations = loadStations();
        Station best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Station s : stations) {
            double dLat = s.latitude - lat;
            double dLon = s.longitude - lon;
            double d = dLat * dLat + dLon * dLon;
            if (d < bestDistance) {
                bestDistance = d;
                best = s;
            }
        }
        return best;
    }

    @NonNull
    private List<Station> loadStations() {
        List<Station> cached = sStations;
        if (cached != null) {
            return cached;
        }
        synchronized (STATIONS_LOCK) {
            if (sStations != null) {
                return sStations;
            }
            List<Station> list = new ArrayList<>();
            try {
                Call<CmaNationalResult> call = mApi.getNationalMap(1, System.currentTimeMillis());
                mCalls.add(call);
                CmaNationalResult r = call.execute().body();
                if (r != null && r.data != null && r.data.city != null) {
                    for (List<JsonElement> row : r.data.city) {
                        try {
                            if (row == null || row.size() < 6) {
                                continue;
                            }
                            String id = row.get(0).getAsString();
                            String name = row.get(1).getAsString();
                            double lat = row.get(4).getAsDouble();
                            double lon = row.get(5).getAsDouble();
                            if (!TextUtils.isEmpty(id)) {
                                list.add(new Station(id, name, lat, lon));
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            if (!list.isEmpty()) {
                sStations = list;
            }
            return list;
        }
    }

    // ---- Location builders ----

    @NonNull
    private static Location buildStationLocation(String id, String name, float lat, float lon) {
        return new Location(
                id != null ? id : "",
                lat,
                lon,
                CN_TZ,
                "中国",
                "",
                name != null ? name : "",
                "",
                null,
                WeatherSource.CMA,
                false,
                false,
                true
        );
    }

    // "stationId|中文名|英文名|国家" -> Location (cityId = stationId).
    @Nullable
    private static Location parseSearchEntry(String entry) {
        if (TextUtils.isEmpty(entry)) {
            return null;
        }
        String[] parts = entry.split("\\|");
        if (parts.length < 2 || TextUtils.isEmpty(parts[0])) {
            return null;
        }
        String stationId = parts[0];
        String cnName = parts.length > 1 ? parts[1] : "";
        String country = parts.length > 3 ? parts[3] : "";
        boolean isChina = "中国".equals(country);
        return new Location(
                stationId,
                0f,
                0f,
                isChina ? CN_TZ : TimeZone.getDefault(),
                country,
                "",
                cnName,
                "",
                null,
                WeatherSource.CMA,
                false,
                false,
                isChina
        );
    }

    // weather/view location block -> Location, used for the IP-based fallback.
    @NonNull
    private static Location buildLocationFromView(CmaWeatherResult.Location v) {
        String country = "";
        String province = "";
        String city = v.name != null ? v.name : "";
        if (!TextUtils.isEmpty(v.path)) {
            String[] p = v.path.split(",");
            if (p.length > 0) country = p[0].trim();
            if (p.length > 1) province = p[1].trim();
            if (p.length > 2) city = p[2].trim();
        }
        boolean isChina = "中国".equals(country);
        TimeZone tz = isChina ? CN_TZ : TimeZone.getDefault();
        if (v.timezone != null) {
            tz = TimeZone.getTimeZone(String.format("GMT%+d", v.timezone));
        }
        return new Location(
                v.id != null ? v.id : "",
                v.latitude != null ? v.latitude.floatValue() : 0f,
                v.longitude != null ? v.longitude.floatValue() : 0f,
                tz,
                country,
                province,
                city,
                "",
                null,
                WeatherSource.CMA,
                false,
                false,
                isChina
        );
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (!TextUtils.isEmpty(v)) {
                return v;
            }
        }
        return "";
    }

    // CMA autocomplete matches pinyin/English. Transliterate Chinese to pinyin (API 24+).
    private static String toPinyinQuery(String input) {
        if (TextUtils.isEmpty(input)) {
            return "";
        }
        String trimmed = input.trim();
        if (!containsHan(trimmed)) {
            return trimmed;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                android.icu.text.Transliterator t = android.icu.text.Transliterator.getInstance(
                        "Han-Latin/Names; Latin-ASCII; Lower");
                String r = t.transliterate(trimmed);
                if (!TextUtils.isEmpty(r)) {
                    // keep only latin letters so "bei jing" -> "beijing" matches station names.
                    String letters = r.replaceAll("[^a-zA-Z]", "");
                    if (!TextUtils.isEmpty(letters)) {
                        return letters.toLowerCase();
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return trimmed;
    }

    private static boolean containsHan(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '一' && c <= '鿿') {
                return true;
            }
        }
        return false;
    }
}
