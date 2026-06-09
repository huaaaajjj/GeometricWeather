package wangdaye.com.geometricweather.weather.converters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Date;
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
import wangdaye.com.geometricweather.common.basic.models.weather.WindDegree;
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
                    convertCurrent(context, result),
                    convertDailyList(context, result),
                    convertHourlyList(context, result),
                    new ArrayList<>(), // minutely
                    convertAlertList(result),
                    null  // yesterday
            );
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    private static Base convertBase(WeatherApiResult result) {
        return new Base(
                result.location != null ? result.location.name : "",
                System.currentTimeMillis(),
                new Date(),
                System.currentTimeMillis(),
                new Date(),
                System.currentTimeMillis()
        );
    }

    @Nullable
    private static Current convertCurrent(Context context, WeatherApiResult result) {
        if (result.current == null) {
            return null;
        }

        WeatherApiResult.Current current = result.current;

        return new Current(
                current.condition != null ? current.condition.text : "Unknown",
                convertWeatherCode(current.condition != null ? current.condition.code : null),
                new Temperature(
                        current.tempC != null ? current.tempC.intValue() : 0,
                        current.feelslikeC != null ? current.feelslikeC.intValue() : null,
                        null, null, null, null, null
                ),
                new Precipitation(
                        current.precipMm != null ? current.precipMm.floatValue() : null,
                        null, null, null, null
                ),
                new PrecipitationProbability(null, null, null, null, null),
                new Wind(
                        current.windDir != null ? current.windDir : "N",
                        new WindDegree(current.windDegree != null ? current.windDegree : 0, false),
                        current.windKph != null ? current.windKph.floatValue() : null,
                        CommonConverter.getWindLevel(context, current.windKph != null ? current.windKph.floatValue() : 0)
                ),
                new UV(current.uv != null ? current.uv.intValue() : null, null, null),
                convertAirQuality(current.airQuality),
                current.humidity != null ? current.humidity.floatValue() : null,
                current.pressureMb != null ? current.pressureMb.floatValue() : null,
                current.visKm != null ? current.visKm.floatValue() : null,
                null, // dewPoint
                current.cloud != null ? current.cloud : null,
                null, // ceiling
                null, // dailyForecast
                null  // hourlyForecast
        );
    }

    @NonNull
    private static List<Daily> convertDailyList(Context context, WeatherApiResult result) {
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
                            day.condition != null ? day.condition.text : null,
                            convertWeatherCode(day.condition != null ? day.condition.code : null),
                            new Temperature(
                                    day.maxtempC != null ? day.maxtempC.intValue() : 0,
                                    null, null, null, null, null, null
                            ),
                            new Precipitation(
                                    day.totalprecipMm != null ? day.totalprecipMm.floatValue() : null,
                                    null, null, null, null
                            ),
                            new PrecipitationProbability(
                                    day.dailyChanceOfRain != null ? (float) day.dailyChanceOfRain : null,
                                    null, null, null, null
                            ),
                            new Wind(
                                    "N",
                                    new WindDegree(0, false),
                                    day.maxwindKph != null ? day.maxwindKph.floatValue() : null,
                                    CommonConverter.getWindLevel(context, day.maxwindKph != null ? day.maxwindKph.floatValue() : 0)
                            ),
                            null, // cloudCover
                            null  // weatherDescription
                    ),
                    new HalfDay(
                            "Night",
                            day.condition != null ? day.condition.text : null,
                            convertWeatherCode(day.condition != null ? day.condition.code : null),
                            new Temperature(
                                    day.mintempC != null ? day.mintempC.intValue() : 0,
                                    null, null, null, null, null, null
                            ),
                            new Precipitation(
                                    day.totalprecipMm != null ? day.totalprecipMm.floatValue() : null,
                                    null, null, null, null
                            ),
                            new PrecipitationProbability(
                                    day.dailyChanceOfRain != null ? (float) day.dailyChanceOfRain : null,
                                    null, null, null, null
                            ),
                            new Wind(
                                    "N",
                                    new WindDegree(0, false),
                                    day.maxwindKph != null ? day.maxwindKph.floatValue() : null,
                                    CommonConverter.getWindLevel(context, day.maxwindKph != null ? day.maxwindKph.floatValue() : 0)
                            ),
                            null, // cloudCover
                            null  // weatherDescription
                    ),
                    astro != null ? new Astro(astro.sunrise, astro.sunset) : null,
                    astro != null ? new Astro(astro.moonrise, astro.moonset) : null,
                    astro != null ? convertMoonPhase(astro.moonPhase) : null,
                    new UV(day.uv != null ? day.uv.intValue() : null, null, null),
                    convertAirQuality(day.airQuality),
                    null, // pollen
                    null  // hoursOfSun
            ));
        }

        return dailyList;
    }

    @NonNull
    private static List<Hourly> convertHourlyList(Context context, WeatherApiResult result) {
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
                        hour.condition != null ? hour.condition.text : null,
                        convertWeatherCode(hour.condition != null ? hour.condition.code : null),
                        new Temperature(
                                hour.tempC != null ? hour.tempC.intValue() : 0,
                                hour.feelslikeC != null ? hour.feelslikeC.intValue() : null,
                                null, null, null, null, null
                        ),
                        new Precipitation(
                                hour.precipMm != null ? hour.precipMm.floatValue() : null,
                                null, null, null, null
                        ),
                        new PrecipitationProbability(
                                hour.chanceOfRain != null ? (float) hour.chanceOfRain : null,
                                null, null, null, null
                        ),
                        new Wind(
                                hour.windDir != null ? hour.windDir : "N",
                                new WindDegree(hour.windDegree != null ? hour.windDegree : 0, false),
                                hour.windKph != null ? hour.windKph.floatValue() : null,
                                CommonConverter.getWindLevel(context, hour.windKph != null ? hour.windKph.floatValue() : 0)
                        ),
                        new UV(hour.uv != null ? hour.uv.intValue() : null, null, null),
                        null, // airQuality
                        hour.isDay != null && hour.isDay == 1
                ));
            }
        }

        return hourlyList;
    }

    @NonNull
    private static List<Alert> convertAlertList(WeatherApiResult result) {
        List<Alert> alertList = new ArrayList<>();

        if (result.alerts == null || result.alerts.alert == null) {
            return alertList;
        }

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
                airQuality.pm25 != null ? airQuality.pm25.floatValue() : null,
                airQuality.pm10 != null ? airQuality.pm10.floatValue() : null,
                airQuality.so2 != null ? airQuality.so2.floatValue() : null,
                airQuality.no2 != null ? airQuality.no2.floatValue() : null,
                airQuality.o3 != null ? airQuality.o3.floatValue() : null,
                airQuality.co != null ? airQuality.co.floatValue() : null,
                null // description
        );
    }

    @Nullable
    private static WeatherCode convertWeatherCode(@Nullable Integer code) {
        if (code == null) {
            return null;
        }

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
