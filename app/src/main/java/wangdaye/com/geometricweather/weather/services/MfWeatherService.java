package wangdaye.com.geometricweather.weather.services;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

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
import wangdaye.com.geometricweather.weather.json.mf.MfForecastResult;
import wangdaye.com.geometricweather.weather.json.mf.MfForecastV2Result;
import wangdaye.com.geometricweather.weather.json.mf.MfLocationResult;
import wangdaye.com.geometricweather.weather.json.mf.MfRainResult;
import wangdaye.com.geometricweather.weather.json.mf.MfWarningsResult;

public class MfWeatherService extends WeatherService {

    private static final String TAG = "MfWeatherService";
    private static final double DEFAULT_LAT = 46.5;
    private static final double DEFAULT_LON = 2.5;

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

        CountDownLatch latch = new CountDownLatch(6);
        AtomicBoolean anyRequiredFailed = new AtomicBoolean(false);

        AtomicReference<MfCurrentResult> currentResult = new AtomicReference<>(null);
        AtomicReference<MfForecastResult> forecastResult = new AtomicReference<>(null);
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
            } catch (Exception e) {
                Log.e(TAG, "Failed to fetch rain", e);
            }
            latch.countDown();
        }));

        mControllers.add(AsyncHelper.runOnIO(() -> {
            try {
                String province = location.getProvince();
                warningsResult.set(mMfApi.getWarnings(
                        province != null ? province : "", null,
                        SettingsManager.getInstance(context).getProviderMfWsftKey()
                ).execute().body());
            } catch (Exception e) {
                Log.e(TAG, "Failed to fetch warnings", e);
            }
            latch.countDown();
        }));

        mControllers.add(AsyncHelper.runOnIO(() -> {
            try {
                String province = location.getProvince();
                if (province != null && (province.equals("Auvergne-Rhône-Alpes")
                        || province.equals("01")
                        || province.equals("03")
                        || province.equals("07")
                        || province.equals("15")
                        || province.equals("26")
                        || province.equals("38")
                        || province.equals("42")
                        || province.equals("43")
                        || province.equals("63")
                        || province.equals("69")
                        || province.equals("73")
                        || province.equals("74"))) {
                    aqiResult.set(mAtmoAuraApi.getQAFull(
                            SettingsManager.getInstance(context).getProviderIqaAtmoAuraKey(),
                            location.getLatitude(),
                            location.getLongitude()
                    ).execute().body());
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to fetch AQI", e);
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
            resultList = mMfApi.callWeatherLocation(query, DEFAULT_LAT, DEFAULT_LON, SettingsManager.getInstance(context).getProviderMfWsftKey()).execute().body();
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
                MfForecastV2Result result = mMfApi.getForecastV2(
                        location.getLatitude(),
                        location.getLongitude(),
                        languageCode,
                        SettingsManager.getInstance(context).getProviderMfWsftKey()
                ).execute().body();
                if (result != null) {
                    List<Location> locationList = new ArrayList<>();
                    if (result.properties.insee != null) {
                        locationList.add(MfResultConverter.convert(null, result));
                    }
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

    public void requestLocation(Context context, String query,
                                @NonNull RequestLocationCallback callback) {
        mControllers.add(AsyncHelper.runOnIO(() -> {
            try {
                List<MfLocationResult> results = mMfApi.getWeatherLocation(
                        query, DEFAULT_LAT, DEFAULT_LON,
                        SettingsManager.getInstance(context).getProviderMfWsftKey()
                ).execute().body();
                if (results != null && results.size() != 0) {
                    List<Location> locationList = new ArrayList<>();
                    for (MfLocationResult r : results) {
                        if (r.postCode != null) {
                            locationList.add(MfResultConverter.convert(null, r));
                        }
                    }
                    callback.requestLocationSuccess(query, locationList);
                } else {
                    callback.requestLocationFailed(query);
                }
            } catch (Exception e) {
                callback.requestLocationFailed(query);
            }
        }));
    }

    @Override
    public void cancel() {
        for (AsyncHelper.Controller c : mControllers) {
            c.cancel();
        }
        mControllers.clear();
    }
}
