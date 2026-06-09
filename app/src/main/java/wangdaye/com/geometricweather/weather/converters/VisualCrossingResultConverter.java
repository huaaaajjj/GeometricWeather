package wangdaye.com.geometricweather.weather.converters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.weather.AirQuality;
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
                    convertCurrent(result),
                    convertDailyList(result),
                    convertHourlyList(result),
                    null, // minutely
                    null, // alerts
                    null, // airQuality
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
                result.timezone != null ? result.timezone : "UTC",
                System.currentTimeMillis()
        );
    }

    @Nullable
    private static Current convertCurrent(VisualCrossingResult result) {
        if (result.currentConditions == null) {
            return null;
        }

        VisualCrossingResult.CurrentConditions current = result.currentConditions;

        return new Current(
                current.temp != null ? current.temp : 0,
                current.feelslike,
                new Precipitation(current.precip, null, null, null),
                new Wind(
                        current.windspeed != null ? current.windspeed : 0,
                        current.winddir != null ? current.winddir.intValue() : 0,
                        current.windgust
                ),
                new UV(current.uvindex != null ? current.uvindex : 0, null),
                null, // airQuality
                current.humidity != null ? current.humidity.intValue() : 0,
                current.pressure != null ? current.pressure : 0,
                current.visibility,
                current.dew,
                current.cloudcover != null ? current.cloudcover.intValue() : 0,
                convertWeatherCode(current.icon),
                current.conditions
        );
    }

    @NonNull
    private static List<Daily> convertDailyList(VisualCrossingResult result) {
        List<Daily> dailyList = new ArrayList<>();

        if (result.days == null) {
            return dailyList;
        }

        for (VisualCrossingResult.Day day : result.days) {
            dailyList.add(new Daily(
                    day.datetime,
                    new HalfDay(
                            "Day",
                            convertWeatherCode(day.icon),
                            day.description,
                            new Temperature(day.tempmax, null, null, null),
                            new Precipitation(day.precip, null, null, null),
                            new PrecipitationProbability(day.precipprob, null, null, null, null),
                            new Wind(day.windspeed != null ? day.windspeed : 0, day.winddir != null ? day.winddir.intValue() : 0, day.windgust),
                            day.cloudcover != null ? day.cloudcover.intValue() : null,
                            null  // weatherDescription
                    ),
                    new HalfDay(
                            "Night",
                            convertWeatherCode(day.icon),
                            day.description,
                            new Temperature(null, null, day.tempmin, null),
                            new Precipitation(day.precip, null, null, null),
                            new PrecipitationProbability(day.precipprob, null, null, null, null),
                            new Wind(day.windspeed != null ? day.windspeed : 0, day.winddir != null ? day.winddir.intValue() : 0, day.windgust),
                            day.cloudcover != null ? day.cloudcover.intValue() : null,
                            null  // weatherDescription
                    ),
                    day.sunrise != null || day.sunset != null ? new Astro(day.sunrise, day.sunset) : null,
                    day.moonrise != null || day.moonset != null ? new Astro(day.moonrise, day.moonset) : null,
                    convertMoonPhase(day.moonphase),
                    new UV(day.uvindex != null ? day.uvindex : 0, null),
                    null, // airQuality
                    null, // pollen
                    null  // hoursOfSun
            ));
        }

        return dailyList;
    }

    @NonNull
    private static List<Hourly> convertHourlyList(VisualCrossingResult result) {
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
                        new Temperature(hour.temp, hour.feelslike, null, null),
                        new Precipitation(hour.precip, null, null, null),
                        new PrecipitationProbability(hour.precipprob, null, null, null, null),
                        new Wind(hour.windspeed != null ? hour.windspeed : 0, hour.winddir != null ? hour.winddir.intValue() : 0, hour.windgust),
                        new UV(hour.uvindex != null ? hour.uvindex : 0, null),
                        convertWeatherCode(hour.icon),
                        hour.cloudcover != null ? hour.cloudcover.intValue() : null,
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

        // Visual Crossing icon codes
        // https://www.visualcrossing.com/resources/documentation/weather-api/weather-condition-fields
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

        // Visual Crossing moon phase values
        // 0 = new moon, 0.5 = full moon, 1 = next new moon
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
