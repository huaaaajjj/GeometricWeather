package wangdaye.com.geometricweather.weather.converters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.weather.AirQuality;
import wangdaye.com.geometricweather.common.basic.models.weather.Alert;
import wangdaye.com.geometricweather.common.basic.models.weather.Astro;
import wangdaye.com.geometricweather.common.basic.models.weather.Base;
import wangdaye.com.geometricweather.common.basic.models.weather.Current;
import wangdaye.com.geometricweather.common.basic.models.weather.Daily;
import wangdaye.com.geometricweather.common.basic.models.weather.HalfDay;
import wangdaye.com.geometricweather.common.basic.models.weather.Hourly;
import wangdaye.com.geometricweather.common.basic.models.weather.MoonPhase;
import wangdaye.com.geometricweather.common.basic.models.weather.Precipitation;
import wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationProbability;
import wangdaye.com.geometricweather.common.basic.models.weather.Temperature;
import wangdaye.com.geometricweather.common.basic.models.weather.UV;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode;
import wangdaye.com.geometricweather.common.basic.models.weather.Wind;
import wangdaye.com.geometricweather.weather.json.weatherapi.WeatherApiResult;

/**
 * WeatherAPI result converter.
 */

public class WeatherApiResultConverter {

    @Nullable
    public static Weather convert(Context context, Location location, WeatherApiResult result) {
        if (result == null || result.current == null) {
            return null;
        }

        try {
            return new Weather(
                    convertBase(result),
                    convertCurrent(result),
                    convertDailyList(result),
                    convertHourlyList(result),
                    null, // minutely
                    convertAlertList(result),
                    convertAirQuality(result.current.airQuality),
                    null // yesterday
            );
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    private static Base convertBase(WeatherApiResult result) {
        String timezone = "UTC";
        if (result.location != null && result.location.tzId != null) {
            timezone = result.location.tzId;
        }
        return new Base(
                result.location != null ? result.location.name : "",
                timezone,
                System.currentTimeMillis()
        );
    }

    @Nullable
    private static Current convertCurrent(WeatherApiResult result) {
        if (result.current == null) {
            return null;
        }

        WeatherApiResult.Current current = result.current;

        return new Current(
                current.tempC != null ? current.tempC : 0,
                current.feelslikeC,
                new Precipitation(current.precipMm, null, null, null),
                new Wind(
                        current.windKph != null ? current.windKph : 0,
                        current.windDegree != null ? current.windDegree : 0,
                        current.gustKph
                ),
                new UV(current.uv != null ? current.uv : 0, null),
                convertAirQuality(current.airQuality),
                current.humidity,
                current.pressureMb != null ? current.pressureMb : 0,
                current.visKm,
                null, // dewPoint
                current.cloud,
                convertWeatherCode(current.condition != null ? current.condition.code : null),
                current.condition != null ? current.condition.text : null
        );
    }

    @NonNull
    private static List<Daily> convertDailyList(WeatherApiResult result) {
        List<Daily> dailyList = new ArrayList<>();

        if (result.forecast == null || result.forecast.forecastday == null) {
            return dailyList;
        }

        for (WeatherApiResult.ForecastDay forecastDay : result.forecast.forecastday) {
            if (forecastDay.day == null) {
                continue;
            }

            WeatherApiResult.Day day = forecastDay.day;
            WeatherApiResult.Astro astro = forecastDay.astro;

            dailyList.add(new Daily(
                    forecastDay.date,
                    new HalfDay(
                            "Day",
                            convertWeatherCode(day.condition != null ? day.condition.code : null),
                            day.condition != null ? day.condition.text : null,
                            new Temperature(day.maxtempC, null, null, null),
                            new Precipitation(day.totalprecipMm, null, null, null),
                            new PrecipitationProbability(
                                    (double) day.dailyChanceOfRain,
                                    null, null, null, null
                            ),
                            new Wind(day.maxwindKph != null ? day.maxwindKph : 0, 0, null),
                            null, // cloudCover
                            null  // weatherDescription
                    ),
                    new HalfDay(
                            "Night",
                            convertWeatherCode(day.condition != null ? day.condition.code : null),
                            day.condition != null ? day.condition.text : null,
                            new Temperature(null, null, day.mintempC, null),
                            new Precipitation(day.totalprecipMm, null, null, null),
                            new PrecipitationProbability(
                                    (double) day.dailyChanceOfRain,
                                    null, null, null, null
                            ),
                            new Wind(day.maxwindKph != null ? day.maxwindKph : 0, 0, null),
                            null, // cloudCover
                            null  // weatherDescription
                    ),
                    astro != null ? new Astro(astro.sunrise, astro.sunset) : null,
                    astro != null ? new Astro(astro.moonrise, astro.moonset) : null,
                    astro != null ? convertMoonPhase(astro.moonPhase) : null,
                    new UV(day.uv != null ? day.uv : 0, null),
                    convertAirQuality(day.airQuality),
                    null, // pollen
                    null  // hoursOfSun
            ));
        }

        return dailyList;
    }

    @NonNull
    private static List<Hourly> convertHourlyList(WeatherApiResult result) {
        List<Hourly> hourlyList = new ArrayList<>();

        if (result.forecast == null || result.forecast.forecastday == null) {
            return hourlyList;
        }

        for (WeatherApiResult.ForecastDay forecastDay : result.forecast.forecastday) {
            if (forecastDay.hour == null) {
                continue;
            }

            for (WeatherApiResult.Hour hour : forecastDay.hour) {
                hourlyList.add(new Hourly(
                        hour.time,
                        new Temperature(hour.tempC, hour.feelslikeC, null, null),
                        new Precipitation(hour.precipMm, null, null, null),
                        new PrecipitationProbability(
                                (double) hour.chanceOfRain,
                                null, null, null, null
                        ),
                        new Wind(
                                hour.windKph != null ? hour.windKph : 0,
                                hour.windDegree != null ? hour.windDegree : 0,
                                hour.gustKph
                        ),
                        new UV(hour.uv != null ? hour.uv : 0, null),
                        convertWeatherCode(hour.condition != null ? hour.condition.code : null),
                        hour.cloud,
                        hour.isDay != null && hour.isDay == 1
                ));
            }
        }

        return hourlyList;
    }

    @Nullable
    private static List<Alert> convertAlertList(WeatherApiResult result) {
        if (result.alerts == null || result.alerts.alert == null) {
            return null;
        }

        List<Alert> alertList = new ArrayList<>();
        for (WeatherApiResult.Alert alert : result.alerts.alert) {
            alertList.add(new Alert(
                    alert.headline,
                    alert.headline,
                    alert.effective,
                    alert.expires,
                    alert.desc,
                    alert.severity,
                    null // color
            ));
        }

        return alertList;
    }

    @Nullable
    private static AirQuality convertAirQuality(WeatherApiResult.AirQuality airQuality) {
        if (airQuality == null) {
            return null;
        }

        return new AirQuality(
                airQuality.usEpaIndex,
                airQuality.pm25,
                airQuality.pm10,
                airQuality.so2,
                airQuality.no2,
                airQuality.o3,
                airQuality.co
        );
    }

    @Nullable
    private static WeatherCode convertWeatherCode(@Nullable Integer code) {
        if (code == null) {
            return null;
        }

        // WeatherAPI condition codes
        // https://www.weatherapi.com/docs/weather-conditions.json
        if (code == 1000) {
            return WeatherCode.CLEAR;
        } else if (code >= 1003 && code <= 1009) {
            return WeatherCode.CLOUDY;
        } else if (code >= 1030 && code <= 1035) {
            return WeatherCode.FOG;
        } else if (code >= 1063 && code <= 1069) {
            return WeatherCode.RAIN;
        } else if (code >= 1087 && code <= 1087) {
            return WeatherCode.THUNDER;
        } else if (code >= 1114 && code <= 1117) {
            return WeatherCode.SNOW;
        } else if (code >= 1135 && code <= 1147) {
            return WeatherCode.FOG;
        } else if (code >= 1150 && code <= 1153) {
            return WeatherCode.RAIN;
        } else if (code >= 1168 && code <= 1171) {
            return WeatherCode.SLEET;
        } else if (code >= 1180 && code <= 1198) {
            return WeatherCode.RAIN;
        } else if (code >= 1201 && code <= 1207) {
            return WeatherCode.SLEET;
        } else if (code >= 1210 && code <= 1219) {
            return WeatherCode.SNOW;
        } else if (code >= 1222 && code <= 1225) {
            return WeatherCode.SNOW;
        } else if (code >= 1237 && code <= 1237) {
            return WeatherCode.HAIL;
        } else if (code >= 1240 && code <= 1246) {
            return WeatherCode.RAIN;
        } else if (code >= 1249 && code <= 1252) {
            return WeatherCode.SLEET;
        } else if (code >= 1255 && code <= 1264) {
            return WeatherCode.SNOW;
        } else if (code >= 1273 && code <= 1276) {
            return WeatherCode.THUNDERSTORM;
        } else if (code >= 1279 && code <= 1282) {
            return WeatherCode.THUNDERSTORM;
        }

        return WeatherCode.CLEAR;
    }

    @Nullable
    private static MoonPhase convertMoonPhase(@Nullable String moonPhase) {
        if (moonPhase == null) {
            return null;
        }

        // WeatherAPI moon phases
        // https://www.weatherapi.com/docs/astronomy-api.json
        switch (moonPhase.toLowerCase()) {
            case "new moon":
                return MoonPhase.NEW_MOON;
            case "waxing crescent":
                return MoonPhase.WAXING_CRESCENT;
            case "first quarter":
                return MoonPhase.FIRST_QUARTER;
            case "waxing gibbous":
                return MoonPhase.WAXING_GIBBOUS;
            case "full moon":
                return MoonPhase.FULL_MOON;
            case "waning gibbous":
                return MoonPhase.WANING_GIBBOUS;
            case "last quarter":
            case "third quarter":
                return MoonPhase.LAST_QUARTER;
            case "waning crescent":
                return MoonPhase.WANING_CRESCENT;
            default:
                return MoonPhase.NEW_MOON;
        }
    }
}
