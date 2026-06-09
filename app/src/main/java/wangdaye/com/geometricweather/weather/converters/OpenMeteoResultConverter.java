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
import java.util.TimeZone;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.weather.AirQuality;
import wangdaye.com.geometricweather.common.basic.models.weather.Base;
import wangdaye.com.geometricweather.common.basic.models.weather.Current;
import wangdaye.com.geometricweather.common.basic.models.weather.Daily;
import wangdaye.com.geometricweather.common.basic.models.weather.HalfDay;
import wangdaye.com.geometricweather.common.basic.models.weather.Hourly;
import wangdaye.com.geometricweather.common.basic.models.weather.Minutely;
import wangdaye.com.geometricweather.common.basic.models.weather.Precipitation;
import wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationDuration;
import wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationProbability;
import wangdaye.com.geometricweather.common.basic.models.weather.Temperature;
import wangdaye.com.geometricweather.common.basic.models.weather.UV;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode;
import wangdaye.com.geometricweather.common.basic.models.weather.Wind;
import wangdaye.com.geometricweather.common.basic.models.weather.WindDegree;
import wangdaye.com.geometricweather.weather.json.openmeteo.OpenMeteoResult;

public class OpenMeteoResultConverter {

    @Nullable
    public static Weather convert(Context context, Location location, OpenMeteoResult result) {
        if (result == null) {
            return null;
        }
        try {
            return new Weather(
                    new Base(location.getCityId(), System.currentTimeMillis(),
                            new Date(), System.currentTimeMillis(),
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
    private static Current convertCurrent(Context context, OpenMeteoResult result) {
        OpenMeteoResult.Current c = result.current;
        int temp = c.temperature != null ? c.temperature.intValue() : 0;
        Integer feelsLike = c.apparentTemperature != null ? c.apparentTemperature.intValue() : null;
        Float precip = c.precipitation != null ? c.precipitation.floatValue() : null;
        float windSpeed = c.windSpeed != null ? c.windSpeed.floatValue() : 0f;
        int windDir = c.windDirection != null ? c.windDirection.intValue() : 0;
        Float humidity = c.humidity != null ? c.humidity.floatValue() : null;
        Float pressure = c.pressure != null ? c.pressure.floatValue() : null;
        Integer cloudCover = c.cloudCover != null ? c.cloudCover.intValue() : null;

        return new Current(
                getWeatherText(c.weatherCode),
                convertWeatherCode(c.weatherCode),
                new Temperature(temp, feelsLike, null, null, null, null, null),
                new Precipitation(precip, null, null, null, null),
                new PrecipitationProbability(null, null, null, null, null),
                new Wind(getWindDirection(windDir), new WindDegree(windDir, false),
                        windSpeed, CommonConverter.getWindLevel(context, windSpeed)),
                new UV(null, null, null),
                new AirQuality(null, null, null, null, null, null, null, null),
                humidity, pressure, null, null, cloudCover, null, null, null
        );
    }

    @NonNull
    private static List<Daily> convertDailyList(Context context, OpenMeteoResult result) {
        List<Daily> list = new ArrayList<>();
        if (result.daily == null || result.daily.time == null) return list;

        for (int i = 0; i < result.daily.time.size(); i++) {
            Date date = parseDate(result.daily.time.get(i));
            if (date == null) continue;

            Integer weatherCode = getVal(result.daily.weatherCode, i);
            int tempMax = getIntVal(result.daily.temperatureMax, i);
            int tempMin = getIntVal(result.daily.temperatureMin, i);
            Float precip = getFloatVal(result.daily.precipitationSum, i);
            Float precipProb = getFloatVal(result.daily.precipitationProbabilityMax, i);
            float windSpeed = getFloatVal2(result.daily.windSpeedMax, i);
            int windDir = getIntVal(result.daily.windDirectionDominant, i);
            Integer uvIndex = getIntVal2(result.daily.uvIndexMax, i);

            HalfDay day = new HalfDay(
                    getWeatherText(weatherCode), "Day", convertWeatherCode(weatherCode),
                    new Temperature(tempMax, null, null, null, null, null, null),
                    new Precipitation(precip, null, null, null, null),
                    new PrecipitationProbability(precipProb, null, null, null, null),
                    new PrecipitationDuration(null, null, null, null),
                    new Wind(getWindDirection(windDir), new WindDegree(windDir, false),
                            windSpeed, CommonConverter.getWindLevel(context, windSpeed)),
                    null
            );
            HalfDay night = new HalfDay(
                    getWeatherText(weatherCode), "Night", convertWeatherCode(weatherCode),
                    new Temperature(tempMin, null, null, null, null, null, null),
                    new Precipitation(precip, null, null, null, null),
                    new PrecipitationProbability(precipProb, null, null, null, null),
                    new PrecipitationDuration(null, null, null, null),
                    new Wind(getWindDirection(windDir), new WindDegree(windDir, false),
                            windSpeed, CommonConverter.getWindLevel(context, windSpeed)),
                    null
            );
            list.add(new Daily(date, date.getTime(), day, night, null, null, null, null, null,
                    new UV(uvIndex, null, null), 0f));
        }
        return list;
    }

    @NonNull
    private static List<Hourly> convertHourlyList(Context context, OpenMeteoResult result) {
        List<Hourly> list = new ArrayList<>();
        if (result.hourly == null || result.hourly.time == null) return list;

        for (int i = 0; i < result.hourly.time.size(); i++) {
            Date date = parseDateTime(result.hourly.time.get(i));
            if (date == null) continue;

            Integer weatherCode = getVal(result.hourly.weatherCode, i);
            int temp = getIntVal(result.hourly.temperature, i);
            Integer feelsLike = getIntVal2(result.hourly.apparentTemperature, i);
            Float precip = getFloatVal(result.hourly.precipitation, i);
            Float precipProb = getFloatVal(result.hourly.precipitationProbability, i);
            float windSpeed = getFloatVal2(result.hourly.windSpeed, i);
            int windDir = getIntVal(result.hourly.windDirection, i);
            Integer uvIndex = getIntVal2(result.hourly.uvIndex, i);
            boolean isDay = getVal(result.hourly.isDay, i) != null && getVal(result.hourly.isDay, i) == 1;

            list.add(new Hourly(
                    date, date.getTime(), isDay,
                    getWeatherText(weatherCode), convertWeatherCode(weatherCode),
                    new Temperature(temp, feelsLike, null, null, null, null, null),
                    new Precipitation(precip, null, null, null, null),
                    new PrecipitationProbability(precipProb, null, null, null, null),
                    new Wind(getWindDirection(windDir), new WindDegree(windDir, false),
                            windSpeed, CommonConverter.getWindLevel(context, windSpeed)),
                    new UV(uvIndex, null, null)
            ));
        }
        return list;
    }

    @Nullable
    private static WeatherCode convertWeatherCode(@Nullable Integer code) {
        if (code == null) return null;
        switch (code) {
            case 0: return WeatherCode.CLEAR;
            case 1: case 2: case 3: return WeatherCode.CLOUDY;
            case 45: case 48: return WeatherCode.FOG;
            case 51: case 53: case 55: case 61: case 63: case 65: case 80: case 81: case 82: return WeatherCode.RAIN;
            case 56: case 57: case 66: case 67: return WeatherCode.SLEET;
            case 71: case 73: case 75: case 77: case 85: case 86: return WeatherCode.SNOW;
            case 95: return WeatherCode.THUNDER;
            case 96: case 99: return WeatherCode.THUNDERSTORM;
            default: return WeatherCode.CLEAR;
        }
    }

    @NonNull
    private static String getWeatherText(@Nullable Integer code) {
        if (code == null) return "Unknown";
        switch (code) {
            case 0: return "Clear sky";
            case 1: return "Mainly clear";
            case 2: return "Partly cloudy";
            case 3: return "Overcast";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: return "Drizzle";
            case 56: case 57: return "Freezing drizzle";
            case 61: case 63: case 65: return "Rain";
            case 66: case 67: return "Freezing rain";
            case 71: case 73: case 75: case 77: return "Snow";
            case 80: case 81: case 82: return "Rain showers";
            case 85: case 86: return "Snow showers";
            case 95: return "Thunderstorm";
            case 96: case 99: return "Thunderstorm with hail";
            default: return "Unknown";
        }
    }

    @NonNull
    private static String getWindDirection(int degree) {
        if (degree < 23 || degree >= 338) return "N";
        if (degree < 68) return "NE";
        if (degree < 113) return "E";
        if (degree < 158) return "SE";
        if (degree < 203) return "S";
        if (degree < 248) return "SW";
        if (degree < 293) return "W";
        return "NW";
    }

    @Nullable
    private static Date parseDate(String s) {
        try { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s); }
        catch (ParseException e) { return null; }
    }

    @Nullable
    private static Date parseDateTime(String s) {
        try { return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(s); }
        catch (ParseException e) { return null; }
    }

    @Nullable
    private static <T> T getVal(List<T> list, int i) {
        return (list == null || i >= list.size()) ? null : list.get(i);
    }

    private static int getIntVal(List<Double> list, int i) {
        Double v = getVal(list, i);
        return v != null ? v.intValue() : 0;
    }

    @Nullable
    private static Integer getIntVal2(List<Double> list, int i) {
        Double v = getVal(list, i);
        return v != null ? v.intValue() : null;
    }

    @Nullable
    private static Float getFloatVal(List<Double> list, int i) {
        Double v = getVal(list, i);
        return v != null ? v.floatValue() : null;
    }

    private static float getFloatVal2(List<Double> list, int i) {
        Double v = getVal(list, i);
        return v != null ? v.floatValue() : 0f;
    }
}
