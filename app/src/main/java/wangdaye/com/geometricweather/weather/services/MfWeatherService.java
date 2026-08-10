package wangdaye.com.geometricweather.weather.services;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper;
import wangdaye.com.geometricweather.settings.SettingsManager;
import wangdaye.com.geometricweather.weather.apis.AtmoAuraIqaApi;
import wangdaye.com.geometricweather.weather.apis.MfWeatherApi;
import wangdaye.com.geometricweather.weather.converters.MfResultConverter;
import wangdaye.com.geometricweather.weather.json.atmoaura.AtmoAuraQAResult;
import wangdaye.com.geometricweather.weather.json.mf.MfCurrentResult;
import wangdaye.com.geometricweather.weather.json.mf.MfEphemerisResult;
import wangdaye.com.geometricweather.weather.json.mf.MfForecastV2Result;
import wangdaye.com.geometricweather.weather.json.mf.MfLocationResult;
import wangdaye.com.geometricweather.weather.json.mf.MfRainResult;
import wangdaye.com.geometricweather.weather.json.mf.MfWarningsResult;

public class MfWeatherService extends WeatherService {

    private final MfWeatherApi mMfApi;
    private final AtmoAuraIqaApi mAtmoAuraApi;
    private final List<AsyncHelper.Controller> mControllers = new ArrayList<>();

    @Inject
    public MfWeatherService(MfWeatherApi mfApi, AtmoAuraIqaApi atmoApi) {
        mMfApi = mfApi;
        mAtmoAuraApi = atmoApi;
    }

    @Override
    public void requestWeather(Context context, Location location, @NonNull RequestWeatherCallback callback) {
        String languageCode = SettingsManager.getInstance(context).getLanguage().getCode();

        CountDownLatch latch = new CountDownLatch(5);
        AtomicBoolean anyRequiredFailed = new AtomicBoolean(false);

        AtomicReference<MfCurrentResult> currentResult = new AtomicReference<>(null);
        AtomicReference<MfForecastV2Result> forecastResult = new AtomicReference<>(null);
        AtomicReference<MfEphemerisResult> ephemerisResult = new AtomicReference<>(null);
        AtomicReference<MfRainResult> rainResult = new AtomicReference<>(null);
        AtomicReference<MfWarningsResult> warningsResult = new AtomicReference<>(null);
        AtomicReference<AtmoAuraQAResult> aqiResult = new AtomicReference<>(null);

        mControllers.add(AsyncHelper.runOnIO(() -> {
            try {
                currentResult.set(mMfApi.getCurrent(
                        location.getLatitude(), location.getLongitude(),
                        languageCode, SettingsManager.getInstance(context).getProviderMfWsftKey()
                ).execute().body());
                if (currentResult.get() == null) {
                    anyRequiredFailed.set(true);
                }
            } catch (Exception e) {
                anyRequiredFailed.set(true);
            }
            latch.countDown();
        }));

        // Warnings are keyed by the French department number, which only the forecast response
        // carries (location.getProvince() holds a department name once the user is located), so
        // this call is chained after the forecast rather than fanned out with it.
        mControllers.add(AsyncHelper.runOnIO(() -> {
            try {
                forecastResult.set(mMfApi.getForecast(
                        location.getLatitude(), location.getLongitude(),
                        languageCode, SettingsManager.getInstance(context).getProviderMfWsftKey()
                ).execute().body());
                if (forecastResult.get() == null) {
                    anyRequiredFailed.set(true);
                }
            } catch (Exception e) {
                anyRequiredFailed.set(true);
            }
            try {
                String department = getDepartment(forecastResult.get(), location);
                if (department != null) {
                    warningsResult.set(mMfApi.getWarnings(
                            department, null,
                            SettingsManager.getInstance(context).getProviderMfWsftKey()
                    ).execute().body());
                }
            } catch (Exception ignored) {
            }
            latch.countDown();
        }));

        mControllers.add(AsyncHelper.runOnIO(() -> {
            try {
                ephemerisResult.set(mMfApi.getEphemeris(
                        location.getLatitude(), location.getLongitude(),
                        "en", SettingsManager.getInstance(context).getProviderMfWsftKey()
                ).execute().body());
            } catch (Exception ignored) {
            }
            latch.countDown();
        }));

        mControllers.add(AsyncHelper.runOnIO(() -> {
            try {
                rainResult.set(mMfApi.getRain(
                        location.getLatitude(), location.getLongitude(),
                        languageCode, SettingsManager.getInstance(context).getProviderMfWsftKey()
                ).execute().body());
            } catch (Exception ignored) {
            }
            latch.countDown();
        }));

        mControllers.add(AsyncHelper.runOnIO(() -> {
            try {
                if (isAtmoAuraDepartment(location.getProvince())) {
                    aqiResult.set(mAtmoAuraApi.getQAFull(
                            SettingsManager.getInstance(context).getProviderIqaAtmoAuraKey(),
                            location.getLatitude(),
                            location.getLongitude()
                    ).execute().body());
                }
            } catch (Exception ignored) {
            }
            latch.countDown();
        }));

        mControllers.add(AsyncHelper.runOnIO(() -> {
            try {
                latch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            if (anyRequiredFailed.get()) {
                callback.requestWeatherFailed(location);
            } else {
                WeatherResultWrapper wrapper = MfResultConverter.convert(
                        context,
                        location,
                        currentResult.get(),
                        forecastResult.get(),
                        ephemerisResult.get(),
                        rainResult.get(),
                        warningsResult.get(),
                        aqiResult.get()
                );
                if (wrapper != null && wrapper.result != null) {
                    callback.requestWeatherSuccess(Location.copy(location, wrapper.result));
                } else {
                    callback.requestWeatherFailed(location);
                }
            }
        }));
    }

    @Override
    @NonNull
    public List<Location> requestLocation(Context context, String query) {
        List<MfLocationResult> resultList = null;
        try {
            resultList = mMfApi.callWeatherLocation(query, 48.86d, 2.34d, SettingsManager.getInstance(context).getProviderMfWsftKey()).execute().body();
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<Location> locationList = new ArrayList<>();
        if (resultList != null && resultList.size() != 0) {
            for (MfLocationResult r : resultList) {
                if (r.postCode != null) {
                    locationList.add(MfResultConverter.convert(null, r));
                }
            }
        }
        return locationList;
    }

    @Override
    public void requestLocation(Context context, Location location,
                                @NonNull RequestLocationCallback callback) {
        String languageCode = SettingsManager.getInstance(context).getLanguage().getCode();

        mControllers.add(AsyncHelper.runOnIO(() -> {
            try {
                MfForecastV2Result result = mMfApi.getForecast(
                        location.getLatitude(),
                        location.getLongitude(),
                        languageCode,
                        SettingsManager.getInstance(context).getProviderMfWsftKey()
                ).execute().body();
                if (result != null && result.properties != null && result.properties.insee != null) {
                    List<Location> locationList = new ArrayList<>();
                    locationList.add(MfResultConverter.convert(null, result));
                    callback.requestLocationSuccess(
                            location.getLatitude() + "," + location.getLongitude(),
                            locationList
                    );
                } else {
                    callback.requestLocationFailed(
                            location.getLatitude() + "," + location.getLongitude()
                    );
                }
            } catch (Exception e) {
                callback.requestLocationFailed(
                        location.getLatitude() + "," + location.getLongitude()
                );
            }
        }));
    }

    /** MF keys warnings by department number ("75"); the forecast is the only source of it. */
    @Nullable
    private static String getDepartment(@Nullable MfForecastV2Result forecast, Location location) {
        if (forecast != null && forecast.properties != null
                && !TextUtils.isEmpty(forecast.properties.frenchDepartment)) {
            return forecast.properties.frenchDepartment;
        }
        // Fall back to the stored province only when it already looks like a department number.
        String province = location.getProvince();
        return province != null && province.matches("\\d{2,3}[AB]?") ? province : null;
    }

    /** Atmo Aura publishes air quality for the Auvergne-Rhône-Alpes departments only. */
    private static boolean isAtmoAuraDepartment(@Nullable String province) {
        if (TextUtils.isEmpty(province)) {
            return false;
        }
        switch (province) {
            case "Auvergne-Rhône-Alpes":
            case "01": case "03": case "07": case "15": case "26": case "38":
            case "42": case "43": case "63": case "69": case "73": case "74":
                return true;
            default:
                return false;
        }
    }

    @Override
    public void cancel() {
        for (AsyncHelper.Controller c : mControllers) {
            c.cancel();
        }
        mControllers.clear();
    }
}
