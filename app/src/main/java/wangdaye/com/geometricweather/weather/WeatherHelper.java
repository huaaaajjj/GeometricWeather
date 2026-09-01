package wangdaye.com.geometricweather.weather;

import android.content.Context;

import androidx.annotation.NonNull;

import javax.inject.Inject;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.utils.NetworkUtils;
import wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper;
import wangdaye.com.geometricweather.db.DatabaseHelper;
import wangdaye.com.geometricweather.weather.services.WeatherService;

public class WeatherHelper {

    private final WeatherServiceSet mServiceSet;

    public interface OnRequestWeatherListener {
        void requestWeatherSuccess(@NonNull Location requestLocation);
        void requestWeatherFailed(@NonNull Location requestLocation);
    }

    @Inject
    public WeatherHelper(WeatherServiceSet weatherServiceSet) {
        mServiceSet = weatherServiceSet;
    }

    public void requestWeather(Context c, Location location, @NonNull final OnRequestWeatherListener l) {
        final WeatherService service = mServiceSet.get(location.getWeatherSource());
        if (!NetworkUtils.isAvailable(c)) {
            l.requestWeatherFailed(location);
            return;
        }

        service.requestWeather(c, location.copy(), new WeatherService.RequestWeatherCallback() {

            @Override
            public void requestWeatherSuccess(@NonNull Location requestLocation) {
                Weather weather = requestLocation.getWeather();
                // An empty daily list is not usable weather: ~76 call sites across the UI, widgets
                // and notifications read getDailyForecast().get(0) unguarded. Treating it as a
                // failure here keeps the previously cached weather instead of crashing downstream.
                if (weather != null && !weather.getDailyForecast().isEmpty()) {
                    // Days and hours are formatted by consumers that never see the location, so
                    // hand them the place's zone here, where both are still together.
                    weather.setTimeZone(requestLocation.getTimeZone());
                    AsyncHelper.runOnIO(() -> {
                        DatabaseHelper.getInstance(c).writeWeather(requestLocation, weather);
                        if (weather.getYesterday() == null) {
                            weather.setYesterday(
                                    DatabaseHelper.getInstance(c).readHistory(requestLocation, weather)
                            );
                        }
                        AsyncHelper.delayRunOnUI(() -> l.requestWeatherSuccess(requestLocation), 0);
                    });
                } else {
                    requestWeatherFailed(requestLocation);
                }
            }

            @Override
            public void requestWeatherFailed(@NonNull Location requestLocation) {
                AsyncHelper.runOnIO(() -> {
                    Location result = Location.copy(
                            requestLocation,
                            DatabaseHelper.getInstance(c).readWeather(requestLocation)
                    );
                    AsyncHelper.delayRunOnUI(() -> l.requestWeatherFailed(result), 0);
                });
            }
        });
    }

    public void cancel() {
        for (WeatherService s : mServiceSet.getAll()) {
            s.cancel();
        }
    }
}
