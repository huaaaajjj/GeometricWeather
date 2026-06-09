package wangdaye.com.geometricweather.weather.services;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Function5;
import wangdaye.com.geometricweather.BuildConfig;
import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.rxjava.BaseObserver;
import wangdaye.com.geometricweather.common.rxjava.ObserverContainer;
import wangdaye.com.geometricweather.common.rxjava.SchedulerTransformer;
import wangdaye.com.geometricweather.weather.apis.QWeatherApi;
import wangdaye.com.geometricweather.weather.converters.QWeatherResultConverter;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherAirResult;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherDailyResult;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherHourlyResult;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherMinutelyResult;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherNowResult;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherWarningResult;

/**
 * QWeather (和风天气) weather service.
 * https://dev.qweather.com/docs/api/
 */

public class QWeatherService extends WeatherService {

    private final QWeatherApi mApi;
    private final CompositeDisposable mCompositeDisposable;

    @Inject
    public QWeatherService(QWeatherApi api, CompositeDisposable disposable) {
        mApi = api;
        mCompositeDisposable = disposable;
    }

    @Override
    public void requestWeather(Context context, @NonNull Location location,
                               @NonNull RequestWeatherCallback callback) {
        String loc = location.getLongitude() + "," + location.getLatitude();
        String key = BuildConfig.QWEATHER_KEY;

        Observable.zip(
                mApi.getWeatherNow(loc, key),
                mApi.getWeather7d(loc, key),
                mApi.getWeather24h(loc, key),
                mApi.getMinutely(loc, key),
                mApi.getAirNow(loc, key),
                (Function5<QWeatherNowResult, QWeatherDailyResult, QWeatherHourlyResult,
                        QWeatherMinutelyResult, QWeatherAirResult, Weather>) (now, daily, hourly, minutely, air) ->
                        QWeatherResultConverter.convert(context, location, now, daily, hourly, minutely, air, null)
        ).compose(SchedulerTransformer.create())
                .subscribe(new ObserverContainer<>(mCompositeDisposable, new BaseObserver<Weather>() {
                    @Override
                    public void onSucceed(Weather weather) {
                        if (weather != null) {
                            Location newLocation = Location.copy(location, weather);
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
        // QWeather location search requires API call
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
