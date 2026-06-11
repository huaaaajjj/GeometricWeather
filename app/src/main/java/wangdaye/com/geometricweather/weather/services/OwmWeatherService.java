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
import wangdaye.com.geometricweather.weather.apis.OwmApi;
import wangdaye.com.geometricweather.weather.converters.OwmResultConverter;
import wangdaye.com.geometricweather.weather.json.owm.OwmAirPollutionResult;
import wangdaye.com.geometricweather.weather.json.owm.OwmCurrentResult;
import wangdaye.com.geometricweather.weather.json.owm.OwmForecastResult;
import wangdaye.com.geometricweather.weather.json.owm.OwmLocationResult;

public class OwmWeatherService extends WeatherService {

    private static final String TAG = "OwmWeatherService";

    private final OwmApi mApi;
    private final List<AsyncHelper.Controller> mControllers = new ArrayList<>();

    @Inject
    public OwmWeatherService(OwmApi api) {
        mApi = api;
    }

    @Override
    public void requestWeather(Context context, Location location, @NonNull RequestWeatherCallback callback) {
        String languageCode = SettingsManager.getInstance(context).getLanguage().getCode();
        String key = SettingsManager.getInstance(context).getProviderOwmKey();
        double lat = location.getLatitude();
        double lon = location.getLongitude();

        CountDownLatch latch = new CountDownLatch(3);
        AtomicBoolean anyRequiredFailed = new AtomicBoolean(false);

        AtomicReference<OwmCurrentResult> currentResult = new AtomicReference<>(null);
        AtomicReference<OwmForecastResult> forecastResult = new AtomicReference<>(null);
        AtomicReference<OwmAirPollutionResult> airPollutionResult = new AtomicReference<>(null);

        mControllers.add(AsyncHelper.runOnIO(() -> {
            try {
                currentResult.set(mApi.getCurrentWeather(
                        key, lat, lon, "metric", languageCode
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
                forecastResult.set(mApi.getForecast(
                        key, lat, lon, "metric", languageCode, 40
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
                airPollutionResult.set(mApi.getAirPollutionCurrent(
                        key, lat, lon
                ).execute().body());
            } catch (Exception e) {
                Log.e(TAG, "Failed to fetch air pollution", e);
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
                WeatherResultWrapper wrapper = OwmResultConverter.convert(
                        context,
                        location,
                        currentResult.get(),
                        forecastResult.get(),
                        airPollutionResult.get()
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
        List<OwmLocationResult> resultList;
        try {
            resultList = mApi.callWeatherLocation(
                    SettingsManager.getInstance(context).getProviderOwmKey(), query).execute().body();
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }

        String zipCode = query.matches("[a-zA-Z0-9]*") ? query : null;

        List<Location> locationList = new ArrayList<>();
        if (resultList != null && resultList.size() != 0) {
            for (OwmLocationResult r : resultList) {
                Location loc = OwmResultConverter.convert(null, r, zipCode);
                if (loc != null) locationList.add(loc);
            }
        }
        return locationList;
    }

    @Override
    public void requestLocation(Context context, Location location,
                                @NonNull RequestLocationCallback callback) {
        mControllers.add(AsyncHelper.runOnIO(() -> {
            try {
                List<OwmLocationResult> results = mApi.getWeatherLocationByGeoPosition(
                        SettingsManager.getInstance(context).getProviderOwmKey(),
                        location.getLatitude(), location.getLongitude()
                ).execute().body();
                if (results != null && !results.isEmpty()) {
                    List<Location> locationList = new ArrayList<>();
                    Location loc = OwmResultConverter.convert(location, results.get(0), null);
                    if (loc != null) locationList.add(loc);
                    callback.requestLocationSuccess(
                            location.getLatitude() + "," + location.getLongitude(), locationList);
                } else {
                    callback.requestLocationFailed(
                            location.getLatitude() + "," + location.getLongitude());
                }
            } catch (Exception e) {
                callback.requestLocationFailed(
                        location.getLatitude() + "," + location.getLongitude());
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
