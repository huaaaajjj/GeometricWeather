package wangdaye.com.geometricweather.weather.services;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.rxjava.BaseObserver;
import wangdaye.com.geometricweather.common.rxjava.ObserverContainer;
import wangdaye.com.geometricweather.common.rxjava.SchedulerTransformer;
import wangdaye.com.geometricweather.settings.SettingsManager;
import wangdaye.com.geometricweather.weather.apis.OwmApi;
import wangdaye.com.geometricweather.weather.converters.OwmResultConverter;
import wangdaye.com.geometricweather.weather.json.owm.OwmAirPollutionResult;
import wangdaye.com.geometricweather.weather.json.owm.OwmCurrentResult;
import wangdaye.com.geometricweather.weather.json.owm.OwmForecastResult;
import wangdaye.com.geometricweather.weather.json.owm.OwmLocationResult;

public class OwmWeatherService extends WeatherService {

    private final OwmApi mApi;
    private final CompositeDisposable mCompositeDisposable;

    private static class EmptyAqiResult extends OwmAirPollutionResult {
    }

    @Inject
    public OwmWeatherService(OwmApi api, CompositeDisposable disposable) {
        mApi = api;
        mCompositeDisposable = disposable;
    }

    @Override
    public void requestWeather(Context context, Location location, @NonNull RequestWeatherCallback callback) {
        String languageCode = SettingsManager.getInstance(context).getLanguage().getCode();
        String key = SettingsManager.getInstance(context).getProviderOwmKey();
        double lat = location.getLatitude();
        double lon = location.getLongitude();

        Observable<OwmCurrentResult> current = mApi.getCurrentWeather(
                key, lat, lon, "metric", languageCode);

        Observable<OwmForecastResult> forecast = mApi.getForecast(
                key, lat, lon, "metric", languageCode, 40);

        Observable<OwmAirPollutionResult> airPollution = mApi.getAirPollutionCurrent(
                key, lat, lon
        ).onExceptionResumeNext(
                Observable.create(emitter -> emitter.onNext(new EmptyAqiResult()))
        );

        Observable.zip(current, forecast, airPollution,
                (currentResult, forecastResult, airPollutionResult) -> OwmResultConverter.convert(
                        context,
                        location,
                        currentResult,
                        forecastResult,
                        airPollutionResult instanceof EmptyAqiResult ? null : airPollutionResult
                )
        ).compose(SchedulerTransformer.create())
                .subscribe(new ObserverContainer<>(mCompositeDisposable, new BaseObserver<WeatherResultWrapper>() {
                    @Override
                    public void onSucceed(WeatherResultWrapper wrapper) {
                        if (wrapper.result != null) {
                            callback.requestWeatherSuccess(
                                    Location.copy(location, wrapper.result)
                            );
                        } else {
                            onFailed();
                        }
                    }

                    @Override
                    public void onFailed() {
                        callback.requestWeatherFailed(location);
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

        mApi.getWeatherLocationByGeoPosition(
                SettingsManager.getInstance(context).getProviderOwmKey(),
                location.getLatitude(), location.getLongitude()
        ).compose(SchedulerTransformer.create())
                .subscribe(new ObserverContainer<>(mCompositeDisposable, new BaseObserver<List<OwmLocationResult>>() {
                    @Override
                    public void onSucceed(List<OwmLocationResult> owmLocationResultList) {
                        if (owmLocationResultList != null && !owmLocationResultList.isEmpty()) {
                            List<Location> locationList = new ArrayList<>();
                            Location loc = OwmResultConverter.convert(location, owmLocationResultList.get(0), null);
                            if (loc != null) locationList.add(loc);
                            callback.requestLocationSuccess(
                                    location.getLatitude() + "," + location.getLongitude(), locationList);
                        } else {
                            onFailed();
                        }
                    }

                    @Override
                    public void onFailed() {
                        callback.requestLocationFailed(
                                location.getLatitude() + "," + location.getLongitude());
                    }
                }));
    }
    @Override
    public void cancel() {
        mCompositeDisposable.clear();
    }
}
