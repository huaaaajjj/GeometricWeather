package wangdaye.com.geometricweather.weather;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.utils.NetworkUtils;
import wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper;
import wangdaye.com.geometricweather.db.DatabaseHelper;
import wangdaye.com.geometricweather.weather.services.WeatherService;

public class WeatherHelper {

    private final WeatherServiceSet mServiceSet;
    private final List<AsyncHelper.Controller> mControllers = new ArrayList<>();

    public interface OnRequestWeatherListener {
        void requestWeatherSuccess(@NonNull Location requestLocation);
        void requestWeatherFailed(@NonNull Location requestLocation);
    }

    public interface OnRequestLocationListener {
        void requestLocationSuccess(String query, List<Location> locationList);
        void requestLocationFailed(String query);
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
                if (weather != null) {
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

    public void requestLocation(Context context, String query, List<WeatherSource> enabledSources,
                                @NonNull final OnRequestLocationListener l) {
        if (enabledSources == null || enabledSources.isEmpty()) {
            AsyncHelper.delayRunOnUI(() -> l.requestLocationFailed(query), 0);
            return;
        }

        final WeatherService[] services = new WeatherService[enabledSources.size()];
        for (int i = 0; i < services.length; i++) {
            services[i] = mServiceSet.get(enabledSources.get(i));
        }

        CountDownLatch latch = new CountDownLatch(services.length);
        List<List<Location>> results = new ArrayList<>();
        for (int i = 0; i < services.length; i++) {
            results.add(null);
        }

        for (int i = 0; i < services.length; i++) {
            final int index = i;
            mControllers.add(AsyncHelper.runOnIO(() -> {
                try {
                    results.set(index, services[index].requestLocation(context, query));
                } catch (Exception ignored) {
                }
                latch.countDown();
            }));
        }

        mControllers.add(AsyncHelper.runOnIO(() -> {
            try {
                latch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            List<Location> locationList = new ArrayList<>();
            for (List<Location> result : results) {
                if (result != null) {
                    locationList.addAll(result);
                }
            }
            if (!locationList.isEmpty()) {
                l.requestLocationSuccess(query, locationList);
            } else {
                l.requestLocationFailed(query);
            }
        }));
    }

    public void cancel() {
        for (WeatherService s : mServiceSet.getAll()) {
            s.cancel();
        }
        for (AsyncHelper.Controller c : mControllers) {
            c.cancel();
        }
        mControllers.clear();
    }
}
