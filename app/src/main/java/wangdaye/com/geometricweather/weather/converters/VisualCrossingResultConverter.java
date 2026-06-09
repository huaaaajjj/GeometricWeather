package wangdaye.com.geometricweather.weather.converters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.weather.Astro;
import wangdaye.com.geometricweather.common.basic.models.weather.Base;
import wangdaye.com.geometricweather.common.basic.models.weather.Current;
import wangdaye.com.geometricweather.common.basic.models.weather.Daily;
import wangdaye.com.geometricweather.common.basic.models.weather.HalfDay;
import wangdaye.com.geometricweather.common.basic.models.weather.Hourly;
import wangdaye.com.geometricweather.common.basic.models.weather.MoonPhase;
import wangdaye.com.geometricweather.common.basic.models.weather.Precipitation;
import wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationDuration;
import wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationProbability;
import wangdaye.com.geometricweather.common.basic.models.weather.Temperature;
import wangdaye.com.geometricweather.common.basic.models.weather.UV;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode;
import wangdaye.com.geometricweather.common.basic.models.weather.Wind;
import wangdaye.com.geometricweather.common.basic.models.weather.WindDegree;
import wangdaye.com.geometricweather.weather.json.visualcrossing.VisualCrossingResult;

public class VisualCrossingResultConverter {

    @Nullable
    public static Weather convert(Context context, Location location, VisualCrossingResult result) {
        if (result == null) return null;
        try {
            return new Weather(
                    new Base(result.resolvedAddress != null ? result.resolvedAddress : "",
                            System.currentTimeMillis(), new Date(), System.currentTimeMillis(),
                            new Date(), System.currentTimeMillis()),
                    convertCurrent(context, result),
                    null,
                    convertDailyList(context, result),
                    convertHourlyList(context, result),
                    new ArrayList<>(),
                    new ArrayList<>()
            );
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    private static Current convertCurrent(Context context, VisualCrossingResult result) {
        VisualCrossingResult.CurrentConditions c = result.currentConditions;
        int temp = c.temp != null ? c.temp.intValue() : 0;
        Integer feelsLike = c.feelslike != null ? c.feelslike.intValue() : null;
        Float precip = c.precip != null ? c.precip.floatValue() : null;
        float windSpeed = c.windspeed != null ? c.windspeed.floatValue() : 0f;
        int windDir = c.winddir != null ? c.winddir.intValue() : 0;
        Float humidity = c.humidity != null ? c.humidity.floatValue() : null;
        Float pressure = c.pressure != null ? c.pressure.floatValue() : null;
        Float visibility = c.visibility != null ? c.visibility.floatValue() : null;
        Integer dew = c.dew != null ? c.dew.intValue() : null;
        Integer cloud = c.cloudcover != null ? c.cloudcover.intValue() : null;
        Integer uvIndex = c.uvindex != null ? c.uvindex.intValue() : null;

        return new Current(
                c.conditions != null ? c.conditions : "Unknown",
                convertWeatherCode(c.icon),
                new Temperature(temp, feelsLike, null, null, null, null, null),
                new Precipitation(precip, null, null, null, null),
                new PrecipitationProbability(null, null, null, null, null),
                new Wind("N", new WindDegree(windDir, false), windSpeed,
                        CommonConverter.getWindLevel(context, windSpeed)),
                new UV(uvIndex, null, null),
                null, humidity, pressure, visibility, dew, cloud, null, null, null
        );
    }

    @NonNull
    private static List<Daily> convertDailyList(Context context, VisualCrossingResult result) {
        List<Daily> list = new ArrayList<>();
        if (result.days == null) return list;

        for (VisualCrossingResult.Day d : result.days) {
            Date date = parseDate(d.datetime);
            if (date == null) continue;

            int tempMax = d.tempmax != null ? d.tempmax.intValue() : 0;
            int tempMin = d.tempmin != null ? d.tempmin.intValue() : 0;
            Float precip = d.precip != null ? d.precip.floatValue() : null;
            Float precipProb = d.precipprob != null ? d.precipprob.floatValue() : null;
            float windSpeed = d.windspeed != null ? d.windspeed.floatValue() : 0f;
            int windDir = d.winddir != null ? d.winddir.intValue() : 0;
            Integer cloud = d.cloudcover != null ? d.cloudcover.intValue() : null;
            Integer uvIndex = d.uvindex != null ? d.uvindex.intValue() : null;

            HalfDay day = new HalfDay(
                    d.description != null ? d.description : "Unknown", "Day", convertWeatherCode(d.icon),
                    new Temperature(tempMax, null, null, null, null, null, null),
                    new Precipitation(precip, null, null, null, null),
                    new PrecipitationProbability(precipProb, null, null, null, null),
                    new PrecipitationDuration(null, null, null, null),
                    new Wind("N", new WindDegree(windDir, false), windSpeed,
                            CommonConverter.getWindLevel(context, windSpeed)),
                    cloud
            );
            HalfDay night = new HalfDay(
                    d.description != null ? d.description : "Unknown", "Night", convertWeatherCode(d.icon),
                    new Temperature(tempMin, null, null, null, null, null, null),
                    new Precipitation(precip, null, null, null, null),
                    new PrecipitationProbability(precipProb, null, null, null, null),
                    new PrecipitationDuration(null, null, null, null),
                    new Wind("N", new WindDegree(windDir, false), windSpeed,
                            CommonConverter.getWindLevel(context, windSpeed)),
                    cloud
            );

            Astro sun = new Astro(parseTime(d.sunrise), parseTime(d.sunset));
            Astro moon = new Astro(parseTime(d.moonrise), parseTime(d.moonset));
            MoonPhase moonPhase = convertMoonPhase(d.moonphase);

            list.add(new Daily(date, date.getTime(), day, night, sun, moon, moonPhase, null, null,
                    new UV(uvIndex, null, null), 0f));
        }
        return list;
    }

    @NonNull
    private static List<Hourly> convertHourlyList(Context context, VisualCrossingResult result) {
        List<Hourly> list = new ArrayList<>();
        if (result.days == null) return list;

        for (VisualCrossingResult.Day d : result.days) {
            if (d.hours == null) continue;
            for (VisualCrossingResult.Hour h : d.hours) {
                Date date = parseHourDateTime(d.datetime, h.datetime);
                if (date == null) continue;

                int temp = h.temp != null ? h.temp.intValue() : 0;
                Integer feelsLike = h.feelslike != null ? h.feelslike.intValue() : null;
                Float precip = h.precip != null ? h.precip.floatValue() : null;
                Float precipProb = h.precipprob != null ? h.precipprob.floatValue() : null;
                float windSpeed = h.windspeed != null ? h.windspeed.floatValue() : 0f;
                int windDir = h.winddir != null ? h.winddir.intValue() : 0;
                Integer uvIndex = h.uvindex != null ? h.uvindex.intValue() : null;
                boolean isDay = isDayIcon(h.icon);

                list.add(new Hourly(
                        date, date.getTime(), isDay,
                        h.conditions != null ? h.conditions : "Unknown",
                        convertWeatherCode(h.icon),
                        new Temperature(temp, feelsLike, null, null, null, null, null),
                        new Precipitation(precip, null, null, null, null),
                        new PrecipitationProbability(precipProb, null, null, null, null),
                        new Wind("N", new WindDegree(windDir, false), windSpeed,
                                CommonConverter.getWindLevel(context, windSpeed)),
                        new UV(uvIndex, null, null)
                ));
            }
        }
        return list;
    }

    @Nullable
    private static WeatherCode convertWeatherCode(@Nullable String icon) {
        if (icon == null) return null;
        switch (icon) {
            case "clear-day": case "clear-night": return WeatherCode.CLEAR;
            case "partly-cloudy-day": case "partly-cloudy-night": case "cloudy": return WeatherCode.CLOUDY;
            case "fog": return WeatherCode.FOG;
            case "rain": case "showers-day": case "showers-night": return WeatherCode.RAIN;
            case "snow": case "snow-showers-day": case "snow-showers-night": return WeatherCode.SNOW;
            case "thunder-rain": case "thunder-showers-day": case "thunder-showers-night": return WeatherCode.THUNDERSTORM;
            case "sleet": case "sleet-showers-day": case "sleet-showers-night": return WeatherCode.SLEET;
            case "hail": return WeatherCode.HAIL;
            case "wind": return WeatherCode.WIND;
            default: return WeatherCode.CLEAR;
        }
    }

    @Nullable
    private static MoonPhase convertMoonPhase(@Nullable Double mp) {
        if (mp == null) return null;
        if (mp < 0.125) return MoonPhase.NEW_MOON;
        if (mp < 0.25) return MoonPhase.WAXING_CRESCENT;
        if (mp < 0.375) return MoonPhase.FIRST_QUARTER;
        if (mp < 0.5) return MoonPhase.WAXING_GIBBOUS;
        if (mp < 0.625) return MoonPhase.FULL_MOON;
        if (mp < 0.75) return MoonPhase.WANING_GIBBOUS;
        if (mp < 0.875) return MoonPhase.LAST_QUARTER;
        return MoonPhase.WANING_CRESCENT;
    }

    private static boolean isDayIcon(@Nullable String icon) {
        if (icon == null) return true;
        return icon.contains("day");
    }

    @Nullable
    private static Date parseDate(String s) {
        try { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s); }
        catch (ParseException e) { return null; }
    }

    @Nullable
    private static Date parseTime(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return new SimpleDateFormat("HH:mm:ss", Locale.US).parse(s); }
        catch (ParseException e) {
            try { return new SimpleDateFormat("hh:mm a", Locale.US).parse(s); }
            catch (ParseException e2) { return null; }
        }
    }

    @Nullable
    private static Date parseHourDateTime(String dateStr, String timeStr) {
        if (dateStr == null || timeStr == null) return null;
        try { return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(dateStr + " " + timeStr); }
        catch (ParseException e) { return null; }
    }
}
