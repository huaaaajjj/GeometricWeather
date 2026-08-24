package wangdaye.com.geometricweather.weather;

import androidx.annotation.NonNull;

import javax.inject.Inject;

import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.weather.services.AccuWeatherService;
import wangdaye.com.geometricweather.weather.services.ApihzWeatherService;
import wangdaye.com.geometricweather.weather.services.CaiYunWeatherService;
import wangdaye.com.geometricweather.weather.services.CmaWeatherService;
import wangdaye.com.geometricweather.weather.services.CompositeWeatherService;
import wangdaye.com.geometricweather.weather.services.MetNoWeatherService;
import wangdaye.com.geometricweather.weather.services.MfWeatherService;
import wangdaye.com.geometricweather.weather.services.OpenMeteoWeatherService;
import wangdaye.com.geometricweather.weather.services.OwmWeatherService;

import wangdaye.com.geometricweather.weather.services.WeatherApiWeatherService;
import wangdaye.com.geometricweather.weather.services.WeatherService;
import wangdaye.com.geometricweather.weather.services.XiaomiWeatherService;

public class WeatherServiceSet {

    private final WeatherService[] mWeatherServices;

    @Inject
    public WeatherServiceSet(AccuWeatherService accuWeatherService,
                             CaiYunWeatherService caiYunWeatherService,
                             MfWeatherService mfWeatherService,
                             OwmWeatherService owmWeatherService,
                             OpenMeteoWeatherService openMeteoWeatherService,
                             WeatherApiWeatherService weatherApiWeatherService,
                             CmaWeatherService cmaWeatherService,
                             ApihzWeatherService apihzWeatherService,
                             CompositeWeatherService compositeWeatherService,
                             MetNoWeatherService metNoWeatherService,
                             XiaomiWeatherService xiaomiWeatherService) {
        mWeatherServices = new WeatherService[] {
                accuWeatherService,
                caiYunWeatherService,
                mfWeatherService,
                owmWeatherService,
                openMeteoWeatherService,
                weatherApiWeatherService,
                cmaWeatherService,
                apihzWeatherService,
                compositeWeatherService,
                metNoWeatherService,
                xiaomiWeatherService
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

            case WEATHERAPI:
                return mWeatherServices[5];

            case CMA:
                return mWeatherServices[6];

            case APIHZ:
                return mWeatherServices[7];

            case COMPOSITE:
                return mWeatherServices[8];

            case METNO:
                return mWeatherServices[9];

            case XIAOMI:
                return mWeatherServices[10];

            default: // ACCU.
                return mWeatherServices[0];
        }
    }

    @NonNull
    public WeatherService[] getAll() {
        return mWeatherServices;
    }
}
