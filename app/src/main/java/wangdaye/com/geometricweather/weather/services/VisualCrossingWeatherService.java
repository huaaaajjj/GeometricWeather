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
import wangdaye.com.geometricweather.weather.apis.VisualCrossingApi;
import wangdaye.com.geometricweather.weather.converters.VisualCrossingResultConverter;
import wangdaye.com.geometricweather.weather.json.visualcrossing.VisualCrossingResult;

/**
 * Visual Crossing weather service.
 * https://www.visualcrossing.com/resources/documentation/weather-api/timeline-weather-api/
 */

public class VisualCrossingWeatherService extends WeatherService {

    private final VisualCrossingApi mApi;
    private final CompositeDisposable mCompositeDisposable;

    @Inject
    public VisualCrossingWeatherService(VisualCrossingApi api, CompositeDisposable disposable) {
        mApi = api;
        mCompositeDisposable = disposable;
    }

    @Override
    public void requestWeather(Context context, @NonNull Location location,
                               @NonNull RequestWeatherCallback callback) {
        String loc = location.getLatitude() + "," + location.getLongitude();

        mApi.getTimeline(
                loc,
                BuildConfig.VISUAL_CROSSING_KEY,
                "metric", // use metric units
                "days,hours,current,alerts",
                "json"
        ).compose(SchedulerTransformer.create())
                .subscribe(new ObserverContainer<>(mCompositeDisposable, new BaseObserver<VisualCrossingResult>() {
                    @Override
                    public void onSucceed(VisualCrossingResult result) {
                        Weather weather = VisualCrossingResultConverter.convert(context, location, result);
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
        // Visual Crossing location search requires API call
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
