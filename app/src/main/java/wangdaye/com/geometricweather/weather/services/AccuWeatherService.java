package wangdaye.com.geometricweather.weather.services;

import android.content.Context;

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
import wangdaye.com.geometricweather.weather.apis.AccuWeatherApi;
import wangdaye.com.geometricweather.weather.converters.AccuResultConverter;
import wangdaye.com.geometricweather.weather.json.accu.AccuAlertResult;
import wangdaye.com.geometricweather.weather.json.accu.AccuAqiResult;
import wangdaye.com.geometricweather.weather.json.accu.AccuCurrentResult;
import wangdaye.com.geometricweather.weather.json.accu.AccuDailyResult;
import wangdaye.com.geometricweather.weather.json.accu.AccuHourlyResult;
import wangdaye.com.geometricweather.weather.json.accu.AccuLocationResult;
import wangdaye.com.geometricweather.weather.json.accu.AccuMinuteResult;

public class AccuWeatherService extends WeatherService {

    private final AccuWeatherApi mApi;
    private final List<AsyncHelper.Controller> mControllers = new ArrayList<>();

    @Inject
    public AccuWeatherService(AccuWeatherApi api) {
        mApi = api;
    }

    @Override
    public void requestWeather(Context context, Location location, @NonNull RequestWeatherCallback callback) {
        String languageCode = SettingsManager.getInstance(context).getLanguage().getCode();

        CountDownLatch latch = new CountDownLatch(6);
        AtomicBoolean anyRequiredFailed = new AtomicBoolean(false);

        AtomicReference<List<AccuCurrentResult>> currentResult = new AtomicReference<>(null);
        AtomicReference<AccuDailyResult> dailyResult = new AtomicReference<>(null);
        AtomicReference<List<AccuHourlyResult>> hourlyResult = new AtomicReference<>(null);
        AtomicReference<AccuMinuteResult> minuteResult = new AtomicReference<>(null);
        AtomicReference<List<AccuAlertResult>> alertResult = new AtomicReference<>(null);
        AtomicReference<AccuAqiResult> aqiResult = new AtomicReference<>(null);

        mControllers.add(AsyncHelper.runOnIO(() -> {
            try {
                currentResult.set(mApi.getCurrent(
                        location.getCityId(),
                        SettingsManager.getInstance(context).getProviderAccuCurrentKey(),
                        languageCode, true
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
                dailyResult.set(mApi.getDaily(
                        location.getCityId(),
                        SettingsManager.getInstance(context).getProviderAccuWeatherKey(),
                        languageCode, true, true
                ).execute().body());
                if (dailyResult.get() == null) {
                    anyRequiredFailed.set(true);
                }
            } catch (Exception e) {
                anyRequiredFailed.set(true);
            }
            latch.countDown();
        }));

        mControllers.add(AsyncHelper.runOnIO(() -> {
            try {
                hourlyResult.set(mApi.getHourly(
                        location.getCityId(),
                        SettingsManager.getInstance(context).getProviderAccuWeatherKey(),
                        languageCode, true, true
                ).execute().body());
                if (hourlyResult.get() == null) {
                    anyRequiredFailed.set(true);
                }
            } catch (Exception e) {
                anyRequiredFailed.set(true);
            }
            latch.countDown();
        }));

        mControllers.add(AsyncHelper.runOnIO(() -> {
            try {
                minuteResult.set(mApi.getMinutely(
                        SettingsManager.getInstance(context).getProviderAccuWeatherKey(),
                        languageCode,
                        true,
                        location.getLatitude() + "," + location.getLongitude()
                ).execute().body());
            } catch (Exception ignored) {
            }
            latch.countDown();
        }));

        mControllers.add(AsyncHelper.runOnIO(() -> {
            try {
                alertResult.set(mApi.getAlert(
                        location.getCityId(),
                        SettingsManager.getInstance(context).getProviderAccuWeatherKey(),
                        languageCode, true
                ).execute().body());
            } catch (Exception ignored) {
            }
            latch.countDown();
        }));

        mControllers.add(AsyncHelper.runOnIO(() -> {
            try {
                aqiResult.set(mApi.getAirQuality(
                        location.getCityId(),
                        SettingsManager.getInstance(context).getProviderAccuAqiKey()
                ).execute().body());
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
                WeatherResultWrapper wrapper = AccuResultConverter.convert(
                        context,
                        location,
                        currentResult.get().get(0),
                        dailyResult.get(),
                        hourlyResult.get(),
                        minuteResult.get(),
                        aqiResult.get(),
                        alertResult.get()
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
        String languageCode = SettingsManager.getInstance(context).getLanguage().getCode();
        List<AccuLocationResult> resultList = null;
        try {
            resultList = mApi.callWeatherLocation(
                    "Always",
                    SettingsManager.getInstance(context).getProviderAccuWeatherKey(),
                    query,
                    languageCode
            ).execute().body();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String zipCode = query.matches("[a-zA-Z0-9]*") ? query : null;

        List<Location> locationList = new ArrayList<>();
        if (resultList != null && resultList.size() != 0) {
                for (AccuLocationResult r : resultList) {
                    Location loc = AccuResultConverter.convert(null, r, zipCode);
                    if (loc != null) locationList.add(loc);
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
                AccuLocationResult result = mApi.getWeatherLocationByGeoPosition(
                        "Always",
                        SettingsManager.getInstance(context).getProviderAccuWeatherKey(),
                        location.getLatitude() + "," + location.getLongitude(),
                        languageCode
                ).execute().body();
                if (result != null) {
                    List<Location> locationList = new ArrayList<>();
                    Location loc = AccuResultConverter.convert(location, result, null);
                    if (loc != null) locationList.add(loc);
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
        String languageCode = SettingsManager.getInstance(context).getLanguage().getCode();
        String zipCode = query.matches("[a-zA-Z0-9]") ? query : null;

        mControllers.add(AsyncHelper.runOnIO(() -> {
            try {
                List<AccuLocationResult> results = mApi.getWeatherLocation(
                        "Always",
                        SettingsManager.getInstance(context).getProviderAccuWeatherKey(),
                        query, languageCode
                ).execute().body();
                if (results != null && results.size() != 0) {
                    List<Location> locationList = new ArrayList<>();
                    for (AccuLocationResult r : results) {
                        locationList.add(AccuResultConverter.convert(null, r, zipCode));
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
