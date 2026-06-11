package wangdaye.com.geometricweather.weather.services;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import javax.inject.Inject;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper;
import wangdaye.com.geometricweather.weather.apis.OpenMeteoApi;
import wangdaye.com.geometricweather.weather.converters.OpenMeteoResultConverter;
import wangdaye.com.geometricweather.weather.json.openmeteo.OpenMeteoResult;

public class OpenMeteoWeatherService extends WeatherService {

    private final OpenMeteoApi mApi;
    private AsyncHelper.Controller mController;

    @Inject
    public OpenMeteoWeatherService(OpenMeteoApi api) {
        mApi = api;
    }

    @Override
    public void requestWeather(Context context, @NonNull Location location,
                               @NonNull RequestWeatherCallback callback) {
        String timezone = TimeZone.getDefault().getID();

        mController = AsyncHelper.runOnIO(() -> {
            try {
                retrofit2.Response<OpenMeteoResult> response = mApi.getForecast(
                        location.getLatitude(),
                        location.getLongitude(),
                        "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m",
                        "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation_probability,precipitation,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index,visibility,is_day",
                        "weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min,sunrise,sunset,precipitation_sum,precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant,uv_index_max,sunshine_duration",
                        timezone,
                        15,
                        1
                ).execute();
                if (response.isSuccessful() && response.body() != null) {
                    OpenMeteoResult result = response.body();
                    Weather weather = OpenMeteoResultConverter.convert(context, location, result);
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
