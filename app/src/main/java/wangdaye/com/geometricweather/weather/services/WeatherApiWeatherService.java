package wangdaye.com.geometricweather.weather.services;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import wangdaye.com.geometricweather.BuildConfig;
import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper;
import wangdaye.com.geometricweather.weather.apis.WeatherApiApi;
import wangdaye.com.geometricweather.weather.converters.WeatherApiResultConverter;
import wangdaye.com.geometricweather.weather.json.weatherapi.WeatherApiResult;

public class WeatherApiWeatherService extends WeatherService {

    private final WeatherApiApi mApi;
    private AsyncHelper.Controller mController;

    @Inject
    public WeatherApiWeatherService(WeatherApiApi api) {
        mApi = api;
    }

    @Override
    public void requestWeather(Context context, @NonNull Location location,
                               @NonNull RequestWeatherCallback callback) {
        String query = location.getLatitude() + "," + location.getLongitude();

        mController = AsyncHelper.runOnIO(() -> {
            try {
                WeatherApiResult result = mApi.getForecast(
                        BuildConfig.WEATHERAPI_KEY,
                        query,
                        14,
                        "yes",
                        "yes"
                ).execute().body();
                if (result != null) {
                    Weather weather = WeatherApiResultConverter.convert(context, location, result);
                    if (weather != null) {
                        callback.requestWeatherSuccess(Location.copy(location, weather));
                    } else {
                        callback.requestWeatherFailed(location);
                    }
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
        return new ArrayList<>();
    }

    @Override
    public void requestLocation(Context context, @NonNull Location location,
                                @NonNull RequestLocationCallback callback) {
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
}
