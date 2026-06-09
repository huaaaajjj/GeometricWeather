package wangdaye.com.geometricweather.weather.converters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import wangdaye.com.geometricweather.common.basic.models.Location;
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
import wangdaye.com.geometricweather.weather.json.visualcrossing.VisualCrossingResult;

/**
 * Visual Crossing result converter.
 */

public class VisualCrossingResultConverter {

    @Nullable
    public static Weather convert(Context context, Location location, VisualCrossingResult result) {
        if (result == null) {
            return null;
        }

        try {
            return new Weather(
                    convertBase(result),
                    convertCurrent(context, result),
                    convertDailyList(context, result),
                    convertHourlyList(context, result),
                    new ArrayList<>(), // minutely
                    new ArrayList<>(), // alerts
                    null  // yesterday
            );
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    private static Base convertBase(VisualCrossingResult result) {
        return new Base(
                result.resolvedAddress != null ? result.resolvedAddress : "",
                System.currentTimeMillis(),
                new Date(),
                System.currentTimeMillis(),
                new Date(),
                System.currentTimeMillis()
        );
    }

    @Nullable
    private static Current convertCurrent(Context context, VisualCrossingResult result) {
        if (result.currentConditions == null) {
            return null;
        }

        VisualCrossingResult.CurrentConditions current = result.currentConditions;

        return new Current(
                current.conditions != null ? current.conditions : "Unknown",
                convertWeatherCode(current.icon),
                new Temperature(
                        current.temp != null ? current.temp.intValue() : 0,
                        current.feelslike != null ? current.feelslike.intValue() : null,
                        null, null, null, null, null
                ),
                new Precipitation(
                        current.precip != null ? current.precip.floatValue() : null,
                        null, null, null, null
                ),
                new PrecipitationProbability(null, null, null, null, null),
                new Wind(
                        "N",
                        new WindDegree(current.winddir != null ? current.winddir.intValue() : 0, false),
                        current.windspeed != null ? current.windspeed.floatValue() : null,
                        CommonConverter.getWindLevel(context, current.windspeed != null ? current.windspeed.floatValue() : 0)
                ),
                new UV(current.uvindex != null ? current.uvindex.intValue() : null, null, null),
                null, // airQuality
                current.humidity != null ? current.humidity.floatValue() : null,
                current.pressure != null ? current.pressure.floatValue() : null,
                current.visibility != null ? current.visibility.floatValue() : null,
                current.dew != null ? current.dew.intValue() : null,
                current.cloudcover != null ? current.cloudcover.intValue() : null,
                null, // ceiling
                null, // dailyForecast
                null  // hourlyForecast
        );
    }

    @NonNull
    private static List<Daily> convertDailyList(Context context, VisualCrossingResult result) {
        List<Daily> dailyList = new ArrayList<>();

        if (result.days == null) {
            return dailyList;
        }

        for (VisualCrossingResult.Day day : result.days) {
            dailyList.add(new Daily(
                    day.datetime,
                    new HalfDay(
                            "Day",
                            day.description,
                            convertWeatherCode(day.icon),
                            new Temperature(
                                    day.tempmax != null ? day.tempmax.intValue() : 0,
                                    null, null, null, null, null, null
                            ),
                            new Precipitation(
                                    day.precip != null ? day.precip.floatValue() : null,
                                    null, null, null, null
                            ),
                            new PrecipitationProbability(
                                    day.precipprob != null ? day.precipprob.floatValue() : null,
                                    null, null, null, null
                            ),
                            new Wind(
                                    "N",
                                    new WindDegree(day.winddir != null ? day.winddir.intValue() : 0, false),
                                    day.windspeed != null ? day.windspeed.floatValue() : null,
                                    CommonConverter.getWindLevel(context, day.windspeed != null ? day.windspeed.floatValue() : 0)
                            ),
                            day.cloudcover != null ? day.cloudcover.intValue() : null,
                            null  // weatherDescription
                    ),
                    new HalfDay(
                            "Night",
                            day.description,
                            convertWeatherCode(day.icon),
                            new Temperature(
                                    day.tempmin != null ? day.tempmin.intValue() : 0,
                                    null, null, null, null, null, null
                            ),
                            new Precipitation(
                                    day.precip != null ? day.precip.floatValue() : null,
                                    null, null, null, null
                            ),
                            new PrecipitationProbability(
                                    day.precipprob != null ? day.precipprob.floatValue() : null,
                                    null, null, null, null
                            ),
                            new Wind(
                                    "N",
                                    new WindDegree(day.winddir != null ? day.winddir.intValue() : 0, false),
                                    day.windspeed != null ? day.windspeed.floatValue() : null,
                                    CommonConverter.getWindLevel(context, day.windspeed != null ? day.windspeed.floatValue() : 0)
                            ),
                            day.cloudcover != null ? day.cloudcover.intValue() : null,
                            null  // weatherDescription
                    ),
                    day.sunrise != null || day.sunset != null ? new Astro(day.sunrise, day.sunset) : null,
                    day.moonrise != null || day.moonset != null ? new Astro(day.moonrise, day.moonset) : null,
                    convertMoonPhase(day.moonphase),
                    new UV(day.uvindex != null ? day.uvindex.intValue() : null, null, null),
                    null, // airQuality
                    null, // pollen
                    null  // hoursOfSun
            ));
        }

        return dailyList;
    }

    @NonNull
    private static List<Hourly> convertHourlyList(Context context, VisualCrossingResult result) {
        List<Hourly> hourlyList = new ArrayList<>();

        if (result.days == null) {
            return hourlyList;
        }

        for (VisualCrossingResult.Day day : result.days) {
            if (day.hours == null) {
                continue;
            }

            for (VisualCrossingResult.Hour hour : day.hours) {
                hourlyList.add(new Hourly(
                        day.datetime + "T" + hour.datetime,
                        hour.conditions != null ? hour.conditions : null,
                        convertWeatherCode(hour.icon),
                        new Temperature(
                                hour.temp != null ? hour.temp.intValue() : 0,
                                hour.feelslike != null ? hour.feelslike.intValue() : null,
                                null, null, null, null, null
                        ),
                        new Precipitation(
                                hour.precip != null ? hour.precip.floatValue() : null,
                                null, null, null, null
                        ),
                        new PrecipitationProbability(
                                hour.precipprob != null ? hour.precipprob.floatValue() : null,
                                null, null, null, null
                        ),
                        new Wind(
                                "N",
                                new WindDegree(hour.winddir != null ? hour.winddir.intValue() : 0, false),
                                hour.windspeed != null ? hour.windspeed.floatValue() : null,
                                CommonConverter.getWindLevel(context, hour.windspeed != null ? hour.windspeed.floatValue() : 0)
                        ),
                        new UV(hour.uvindex != null ? hour.uvindex.intValue() : null, null, null),
                        null, // airQuality
                        isDayIcon(hour.icon)
                ));
            }
        }

        return hourlyList;
    }

    @Nullable
    private static WeatherCode convertWeatherCode(@Nullable String icon) {
        if (icon == null) {
            return null;
        }

        switch (icon) {
            case "clear-day":
            case "clear-night":
                return WeatherCode.CLEAR;
            case "partly-cloudy-day":
            case "partly-cloudy-night":
            case "cloudy":
                return WeatherCode.CLOUDY;
            case "fog":
                return WeatherCode.FOG;
            case "rain":
            case "showers-day":
            case "showers-night":
                return WeatherCode.RAIN;
            case "snow":
            case "snow-showers-day":
            case "snow-showers-night":
                return WeatherCode.SNOW;
            case "thunder-rain":
            case "thunder-showers-day":
            case "thunder-showers-night":
                return WeatherCode.THUNDERSTORM;
            case "sleet":
            case "sleet-showers-day":
            case "sleet-showers-night":
                return WeatherCode.SLEET;
            case "hail":
                return WeatherCode.HAIL;
            case "wind":
                return WeatherCode.WIND;
            default:
                return WeatherCode.CLEAR;
        }
    }

    @Nullable
    private static MoonPhase convertMoonPhase(@Nullable Double moonphase) {
        if (moonphase == null) {
            return null;
        }

        if (moonphase < 0.125) {
            return MoonPhase.NEW_MOON;
        } else if (moonphase < 0.25) {
            return MoonPhase.WAXING_CRESCENT;
        } else if (moonphase < 0.375) {
            return MoonPhase.FIRST_QUARTER;
        } else if (moonphase < 0.5) {
            return MoonPhase.WAXING_GIBBOUS;
        } else if (moonphase < 0.625) {
            return MoonPhase.FULL_MOON;
        } else if (moonphase < 0.75) {
            return MoonPhase.WANING_GIBBOUS;
        } else if (moonphase < 0.875) {
            return MoonPhase.LAST_QUARTER;
        } else {
            return MoonPhase.WANING_CRESCENT;
        }
    }

    private static boolean isDayIcon(@Nullable String icon) {
        if (icon == null) {
            return true;
        }
        return icon.contains("day") || icon.equals("clear-day") || icon.equals("partly-cloudy-day");
    }
}
