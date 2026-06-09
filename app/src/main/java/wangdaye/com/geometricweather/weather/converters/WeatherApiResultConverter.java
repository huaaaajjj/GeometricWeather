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
import wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationDuration;
import wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationProbability;
import wangdaye.com.geometricweather.common.basic.models.weather.Temperature;
import wangdaye.com.geometricweather.common.basic.models.weather.UV;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode;
import wangdaye.com.geometricweather.common.basic.models.weather.Wind;
import wangdaye.com.geometricweather.common.basic.models.weather.WindDegree;
import wangdaye.com.geometricweather.weather.json.weatherapi.WeatherApiResult;

public class WeatherApiResultConverter {

    @Nullable
    public static Weather convert(Context context, Location location, WeatherApiResult result) {
        if (result == null || result.current == null) return null;
        try {
            return new Weather(
                    new Base(result.location != null ? result.location.name : "",
                            System.currentTimeMillis(), new Date(), System.currentTimeMillis(),
                            new Date(), System.currentTimeMillis()),
                    convertCurrent(context, result),
                    null,
                    convertDailyList(context, result),
                    convertHourlyList(context, result),
                    new ArrayList<>(),
                    convertAlertList(result)
            );
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    private static Current convertCurrent(Context context, WeatherApiResult result) {
        WeatherApiResult.Current c = result.current;
        int temp = c.tempC != null ? c.tempC.intValue() : 0;
        Integer feelsLike = c.feelslikeC != null ? c.feelslikeC.intValue() : null;
        Float precip = c.precipMm != null ? c.precipMm.floatValue() : null;
        float windSpeed = c.windKph != null ? c.windKph.floatValue() : 0f;
        int windDir = c.windDegree != null ? c.windDegree : 0;
        Float humidity = c.humidity != null ? (float) c.humidity : null;
        Float pressure = c.pressureMb != null ? c.pressureMb.floatValue() : null;
        Float visibility = c.visKm != null ? c.visKm.floatValue() : null;
        Integer cloud = c.cloud;
        Integer uvIndex = c.uv != null ? c.uv.intValue() : null;

        return new Current(
                c.condition != null ? c.condition.text : "Unknown",
                convertWeatherCode(c.condition != null ? c.condition.code : null),
                new Temperature(temp, feelsLike, null, null, null, null, null),
                new Precipitation(precip, null, null, null, null),
                new PrecipitationProbability(null, null, null, null, null),
                new Wind(c.windDir != null ? c.windDir : "N",
                        new WindDegree(windDir, false), windSpeed,
                        CommonConverter.getWindLevel(context, windSpeed)),
                new UV(uvIndex, null, null),
                convertAirQuality(c.airQuality),
                humidity, pressure, visibility, null, cloud, null, null, null
        );
    }

    @NonNull
    private static List<Daily> convertDailyList(Context context, WeatherApiResult result) {
        List<Daily> list = new ArrayList<>();
        if (result.forecast == null || result.forecast.forecastday == null) return list;

        for (WeatherApiResult.ForecastDay fd : result.forecast.forecastday) {
            if (fd.day == null) continue;
            Date date = parseDate(fd.date);
            if (date == null) continue;

            WeatherApiResult.Day d = fd.day;
            int tempMax = d.maxtempC != null ? d.maxtempC.intValue() : 0;
            int tempMin = d.mintempC != null ? d.mintempC.intValue() : 0;
            Float precip = d.totalprecipMm != null ? d.totalprecipMm.floatValue() : null;
            Float precipProb = d.dailyChanceOfRain != null ? (float) d.dailyChanceOfRain : null;
            float windSpeed = d.maxwindKph != null ? d.maxwindKph.floatValue() : 0f;
            Integer uvIndex = d.uv != null ? d.uv.intValue() : null;
            String conditionText = d.condition != null ? d.condition.text : null;
            WeatherCode weatherCode = convertWeatherCode(d.condition != null ? d.condition.code : null);

            HalfDay day = new HalfDay(
                    conditionText != null ? conditionText : "Unknown", "Day", weatherCode,
                    new Temperature(tempMax, null, null, null, null, null, null),
                    new Precipitation(precip, null, null, null, null),
                    new PrecipitationProbability(precipProb, null, null, null, null),
                    new PrecipitationDuration(null, null, null, null, null),
                    new Wind("N", new WindDegree(0, false), windSpeed,
                            CommonConverter.getWindLevel(context, windSpeed)),
                    null
            );
            HalfDay night = new HalfDay(
                    conditionText != null ? conditionText : "Unknown", "Night", weatherCode,
                    new Temperature(tempMin, null, null, null, null, null, null),
                    new Precipitation(precip, null, null, null, null),
                    new PrecipitationProbability(precipProb, null, null, null, null),
                    new PrecipitationDuration(null, null, null, null, null),
                    new Wind("N", new WindDegree(0, false), windSpeed,
                            CommonConverter.getWindLevel(context, windSpeed)),
                    null
            );

            Astro sun = null, moon = null;
            if (fd.astro != null) {
                sun = new Astro(parseTime(fd.astro.sunrise), parseTime(fd.astro.sunset));
                moon = new Astro(parseTime(fd.astro.moonrise), parseTime(fd.astro.moonset));
            }
            MoonPhase moonPhase = fd.astro != null ? convertMoonPhase(fd.astro.moonPhase) : null;

            list.add(new Daily(date, date.getTime(), day, night, sun, moon, moonPhase,
                    convertAirQuality(d.airQuality), null, new UV(uvIndex, null, null), 0f));
        }
        return list;
    }

    @NonNull
    private static List<Hourly> convertHourlyList(Context context, WeatherApiResult result) {
        List<Hourly> list = new ArrayList<>();
        if (result.forecast == null || result.forecast.forecastday == null) return list;

        for (WeatherApiResult.ForecastDay fd : result.forecast.forecastday) {
            if (fd.hour == null) continue;
            for (WeatherApiResult.Hour h : fd.hour) {
                Date date = parseDateTime(h.time);
                if (date == null) continue;

                int temp = h.tempC != null ? h.tempC.intValue() : 0;
                Integer feelsLike = h.feelslikeC != null ? h.feelslikeC.intValue() : null;
                Float precip = h.precipMm != null ? h.precipMm.floatValue() : null;
                Float precipProb = h.chanceOfRain != null ? (float) h.chanceOfRain : null;
                float windSpeed = h.windKph != null ? h.windKph.floatValue() : 0f;
                int windDir = h.windDegree != null ? h.windDegree : 0;
                Integer uvIndex = h.uv != null ? h.uv.intValue() : null;
                boolean isDay = h.isDay != null && h.isDay == 1;

                list.add(new Hourly(
                        date, date.getTime(), isDay,
                        h.condition != null ? h.condition.text : "Unknown",
                        convertWeatherCode(h.condition != null ? h.condition.code : null),
                        new Temperature(temp, feelsLike, null, null, null, null, null),
                        new Precipitation(precip, null, null, null, null),
                        new PrecipitationProbability(precipProb, null, null, null, null),
                        new Wind(h.windDir != null ? h.windDir : "N",
                                new WindDegree(windDir, false), windSpeed,
                                CommonConverter.getWindLevel(context, windSpeed)),
                        new UV(uvIndex, null, null)
                ));
            }
        }
        return list;
    }

    @NonNull
    private static List<Alert> convertAlertList(WeatherApiResult result) {
        List<Alert> list = new ArrayList<>();
        if (result.alerts == null || result.alerts.alert == null) return list;

        long id = 0;
        for (WeatherApiResult.Alert a : result.alerts.alert) {
            Date date = parseDateTime(a.effective);
            long time = date != null ? date.getTime() : System.currentTimeMillis();
            list.add(new Alert(id++, date != null ? date : new Date(), time,
                    a.headline, a.desc, a.event, 1, 0xFFFFB82B));
        }
        return list;
    }

    @Nullable
    private static AirQuality convertAirQuality(WeatherApiResult.AirQuality aq) {
        if (aq == null) return null;
        return new AirQuality(
                aq.usEpaIndex != null ? String.valueOf(aq.usEpaIndex) : null,
                aq.usEpaIndex,
                aq.pm25 != null ? aq.pm25.floatValue() : null,
                aq.pm10 != null ? aq.pm10.floatValue() : null,
                aq.so2 != null ? aq.so2.floatValue() : null,
                aq.no2 != null ? aq.no2.floatValue() : null,
                aq.o3 != null ? aq.o3.floatValue() : null,
                aq.co != null ? aq.co.floatValue() : null
        );
    }

    @Nullable
    private static WeatherCode convertWeatherCode(@Nullable Integer code) {
        if (code == null) return null;
        if (code == 1000) return WeatherCode.CLEAR;
        if (code >= 1003 && code <= 1009) return WeatherCode.CLOUDY;
        if ((code >= 1030 && code <= 1035) || (code >= 1135 && code <= 1147)) return WeatherCode.FOG;
        if ((code >= 1063 && code <= 1069) || (code >= 1150 && code <= 1153) || (code >= 1180 && code <= 1198) || (code >= 1240 && code <= 1246)) return WeatherCode.RAIN;
        if (code == 1087 || (code >= 1273 && code <= 1282)) return WeatherCode.THUNDERSTORM;
        if ((code >= 1114 && code <= 1117) || (code >= 1210 && code <= 1225) || (code >= 1255 && code <= 1264)) return WeatherCode.SNOW;
        if ((code >= 1168 && code <= 1171) || (code >= 1201 && code <= 1207) || (code >= 1249 && code <= 1252)) return WeatherCode.SLEET;
        if (code == 1237) return WeatherCode.HAIL;
        return WeatherCode.CLEAR;
    }

    @Nullable
    private static MoonPhase convertMoonPhase(@Nullable String mp) {
        if (mp == null) return null;
        switch (mp.toLowerCase()) {
            case "new moon": return new MoonPhase(0, "new moon");
            case "waxing crescent": return new MoonPhase(45, "waxing crescent");
            case "first quarter": return new MoonPhase(90, "first quarter");
            case "waxing gibbous": return new MoonPhase(135, "waxing gibbous");
            case "full moon": return new MoonPhase(180, "full moon");
            case "waning gibbous": return new MoonPhase(225, "waning gibbous");
            case "last quarter": case "third quarter": return new MoonPhase(270, "last quarter");
            case "waning crescent": return new MoonPhase(315, "waning crescent");
            default: return new MoonPhase(0, "new moon");
        }
    }

    @Nullable
    private static Date parseDate(String s) {
        try { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s); }
        catch (ParseException e) { return null; }
    }

    @Nullable
    private static Date parseDateTime(String s) {
        if (s == null) return null;
        try { return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(s); }
        catch (ParseException e) {
            try { return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(s); }
            catch (ParseException e2) { return null; }
        }
    }

    @Nullable
    private static Date parseTime(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.US);
            Date d = sdf.parse(s);
            if (d != null) return d;
        } catch (ParseException ignored) {}
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.US);
            return sdf.parse(s);
        } catch (ParseException e) {
            return null;
        }
    }
}
