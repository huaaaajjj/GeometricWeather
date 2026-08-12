package wangdaye.com.geometricweather.weather.converters;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

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

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    @Nullable
    public static Weather convert(Context context, Location location, WeatherApiResult result) {
        if (result == null || result.current == null) return null;
        try {
            return new Weather(
                    new Base(location.getCityId(),
                            System.currentTimeMillis(), new Date(), System.currentTimeMillis(),
                            new Date(), System.currentTimeMillis()),
                    convertCurrent(context, result),
                    null,
                    convertDailyList(context, location, result),
                    convertHourlyList(context, result),
                    new ArrayList<>(),
                    convertAlertList(result, location)
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
                convertAirQuality(context, c.airQuality),
                humidity, pressure, visibility, null, cloud, null, null, null
        );
    }

    @NonNull
    private static List<Daily> convertDailyList(Context context, Location location,
                                                WeatherApiResult result) {
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
                TimeZone timeZone = location.getTimeZone();
                sun = new Astro(
                        parseDayTime(fd.date, fd.astro.sunrise, timeZone),
                        parseDayTime(fd.date, fd.astro.sunset, timeZone));
                moon = new Astro(
                        parseDayTime(fd.date, fd.astro.moonrise, timeZone),
                        parseDayTime(fd.date, fd.astro.moonset, timeZone));
            }
            MoonPhase moonPhase = fd.astro != null ? convertMoonPhase(fd.astro.moonPhase) : null;

            list.add(new Daily(date, date.getTime(), day, night, sun, moon, moonPhase,
                    convertAirQuality(context, d.airQuality), null, new UV(uvIndex, null, null), 0f));
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
    private static List<Alert> convertAlertList(WeatherApiResult result, Location location) {
        List<Alert> list = new ArrayList<>();
        if (result.alerts == null || result.alerts.alert == null) return list;

        long id = 0;
        for (WeatherApiResult.Alert a : result.alerts.alert) {
            // WeatherAPI sometimes attaches an alert for a different region (observed: a Beijing
            // 延庆区 warning returned for a Tianjin point). Drop alerts whose areas field names a
            // province/city other than this location's.
            if (isForeignArea(a, location)) continue;

            Date date = parseDateTime(a.effective);
            long time = date != null ? date.getTime() : System.currentTimeMillis();
            list.add(new Alert(id++, date != null ? date : new Date(), time,
                    a.headline, a.desc, a.event, 1, 0xFFFFB82B));
        }
        return list;
    }

    // True when the alert clearly belongs to another Chinese admin area than the location.
    // Only judges when WeatherAPI provided an areas field and we have a location name to compare.
    private static boolean isForeignArea(WeatherApiResult.Alert a, Location location) {
        if (location == null || !location.isChina() || TextUtils.isEmpty(a.areas)) {
            return false;
        }
        if (TextUtils.isEmpty(location.getProvince()) && TextUtils.isEmpty(location.getCity())) {
            return false;
        }
        String text = a.areas + " " + (a.headline != null ? a.headline : "");
        boolean ours = nameMentioned(text, location.getProvince())
                || nameMentioned(text, location.getCity());
        return !ours;
    }

    private static boolean nameMentioned(String text, String adminName) {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(adminName)) return false;
        String stem = adminName.replaceAll("(省|市|自治区|特别行政区|地区|盟)$", "");
        return !TextUtils.isEmpty(stem) && text.contains(stem);
    }

    @Nullable
    private static AirQuality convertAirQuality(Context context, WeatherApiResult.AirQuality aq) {
        if (aq == null) return null;

        Float pm25 = aq.pm25 != null ? aq.pm25.floatValue() : null;
        Float pm10 = aq.pm10 != null ? aq.pm10.floatValue() : null;

        // us-epa-index 是 1~6 的档位号，原样写进 aqiIndex 会被当成 0~500 的 AQI，
        // 六档全部落在 ≤50 的第一档 → 空气再脏也是绿色。有浓度就按中国标准算真实
        // AQI（与彩云取 aqi.chn 一致），档位号仅在无浓度时兜底。
        Integer index = CommonConverter.getAqiIndexFromConcentration(pm25, pm10);
        if (index == null) {
            index = CommonConverter.getAqiIndexFromUsEpaCategory(aq.usEpaIndex);
        }

        return new AirQuality(
                CommonConverter.getAqiQuality(context, index),
                index,
                pm25,
                pm10,
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

    /**
     * WeatherAPI reports astro as a bare 12-hour clock ("05:21 AM") with the date living one level
     * up in forecastday.date. Parsing the clock alone yields 1970-01-01, which made
     * {@link wangdaye.com.geometricweather.common.basic.models.weather.Weather#isDaylight} compare
     * "now" against an epoch-day sunset and answer "night" forever. Anchor the clock to its own
     * day, in the location's zone, so the result is a real instant.
     */
    @Nullable
    private static Date parseDayTime(@Nullable String dayDate, @Nullable String clock,
                                     @NonNull TimeZone timeZone) {
        if (dayDate == null || clock == null || clock.isEmpty()) {
            return null;
        }

        Date clockOnly = null;
        for (String pattern : new String[] {"hh:mm a", "HH:mm"}) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                sdf.setTimeZone(UTC);
                clockOnly = sdf.parse(clock);
                break;
            } catch (ParseException ignored) {
                // try the next pattern
            }
        }
        if (clockOnly == null) {
            return null;
        }

        Calendar clockCalendar = Calendar.getInstance(UTC);
        clockCalendar.setTime(clockOnly);

        Date day;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            sdf.setTimeZone(timeZone);
            day = sdf.parse(dayDate);
        } catch (ParseException e) {
            return null;
        }
        if (day == null) {
            return null;
        }

        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.setTime(day);
        calendar.set(Calendar.HOUR_OF_DAY, clockCalendar.get(Calendar.HOUR_OF_DAY));
        calendar.set(Calendar.MINUTE, clockCalendar.get(Calendar.MINUTE));
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }
}
