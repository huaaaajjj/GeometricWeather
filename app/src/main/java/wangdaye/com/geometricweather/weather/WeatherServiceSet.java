package wangdaye.com.geometricweather.weather;

import androidx.annotation.NonNull;

import javax.inject.Inject;

import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.weather.services.AccuWeatherService;
import wangdaye.com.geometricweather.weather.services.CaiYunWeatherService;
import wangdaye.com.geometricweather.weather.services.MfWeatherService;
import wangdaye.com.geometricweather.weather.services.OpenMeteoWeatherService;
import wangdaye.com.geometricweather.weather.services.OwmWeatherService;
import wangdaye.com.geometricweather.weather.services.QWeatherService;
import wangdaye.com.geometricweather.weather.services.VisualCrossingWeatherService;
import wangdaye.com.geometricweather.weather.services.WeatherApiWeatherService;
import wangdaye.com.geometricweather.weather.services.WeatherService;

public class WeatherServiceSet {

    private final WeatherService[] mWeatherServices;

    @Inject
    public WeatherServiceSet(AccuWeatherService accuWeatherService,
                             CaiYunWeatherService caiYunWeatherService,
                             MfWeatherService mfWeatherService,
                             OwmWeatherService owmWeatherService,
                             OpenMeteoWeatherService openMeteoWeatherService,
                             QWeatherService qWeatherService,
                             WeatherApiWeatherService weatherApiWeatherService,
                             VisualCrossingWeatherService visualCrossingWeatherService) {
        mWeatherServices = new WeatherService[] {
                accuWeatherService,
                caiYunWeatherService,
                mfWeatherService,
                owmWeatherService,
                openMeteoWeatherService,
                qWeatherService,
                weatherApiWeatherService,
                visualCrossingWeatherService
        };
    }

    @NonNull
    public WeatherService get(WeatherSource source) {
        switch (source) {
            case OWM:
                return mWeatherServices[3];

            case MF:
                return mWeatherServices[2];

            case CAIYUN:
                return mWeatherServices[1];

            case OPEN_METEO:
                return mWeatherServices[4];

            case QWEATHER:
                return mWeatherServices[5];

            case WEATHERAPI:
                return mWeatherServices[6];

            case VISUAL_CROSSING:
                return mWeatherServices[7];

            default: // ACCU.
                return mWeatherServices[0];
        }
    }

    @NonNull
    public WeatherService[] getAll() {
        return mWeatherServices;
    }
}
