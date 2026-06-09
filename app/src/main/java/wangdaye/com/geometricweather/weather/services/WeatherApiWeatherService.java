package wangdaye.com.geometricweather.weather.services;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import wangdaye.com.geometricweather.BuildConfig;
import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.rxjava.BaseObserver;
import wangdaye.com.geometricweather.common.rxjava.ObserverContainer;
import wangdaye.com.geometricweather.common.rxjava.SchedulerTransformer;
import wangdaye.com.geometricweather.weather.apis.WeatherApiApi;
import wangdaye.com.geometricweather.weather.converters.WeatherApiResultConverter;
import wangdaye.com.geometricweather.weather.json.weatherapi.WeatherApiResult;

/**
 * WeatherAPI.com weather service.
 * https://www.weatherapi.com/docs/
 */

public class WeatherApiWeatherService extends WeatherService {

    private final WeatherApiApi mApi;
    private final CompositeDisposable mCompositeDisposable;

    @Inject
    public WeatherApiWeatherService(WeatherApiApi api, CompositeDisposable disposable) {
        mApi = api;
        mCompositeDisposable = disposable;
    }

    @Override
    public void requestWeather(Context context, @NonNull Location location,
                               @NonNull RequestWeatherCallback callback) {
        String query = location.getLatitude() + "," + location.getLongitude();

        mApi.getForecast(
                BuildConfig.WEATHERAPI_KEY,
                query,
                14, // 14 days forecast
                "yes", // include air quality
                "yes"  // include alerts
        ).compose(SchedulerTransformer.create())
                .subscribe(new ObserverContainer<>(mCompositeDisposable, new BaseObserver<WeatherApiResult>() {
                    @Override
                    public void onSucceed(WeatherApiResult result) {
                        Weather weather = WeatherApiResultConverter.convert(context, location, result);
                        if (weather != null) {
                            Location newLocation = Location.copy(location, weather, location.isCurrentPosition(), location.isResidentPosition());
                            callback.requestWeatherSuccess(newLocation);
                        } else {
                            callback.requestWeatherFailed(location);
                        }
                    }

                    @Override
                    public void onFailed() {
                        callback.requestWeatherFailed(location);
                    }
                }));
    }

    @NonNull
    @Override
    public List<Location> requestLocation(Context context, String query) {
        // WeatherAPI location search requires API call
        // For now, return empty list
        return new ArrayList<>();
    }

    @Override
    public void requestLocation(Context context, @NonNull Location location,
                                @NonNull RequestLocationCallback callback) {
        // Use the location as-is
        List<Location> locationList = new ArrayList<>();
        locationList.add(location);
        callback.requestLocationSuccess(location.getCityName(context), locationList);
    }

    @Override
    public void cancel() {
        mCompositeDisposable.clear();
    }
}
