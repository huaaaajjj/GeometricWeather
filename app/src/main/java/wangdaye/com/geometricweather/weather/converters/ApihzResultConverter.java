package wangdaye.com.geometricweather.weather.converters;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.weather.Alert;
import wangdaye.com.geometricweather.common.basic.models.weather.Astro;
import wangdaye.com.geometricweather.common.basic.models.weather.Base;
import wangdaye.com.geometricweather.common.basic.models.weather.Current;
import wangdaye.com.geometricweather.common.basic.models.weather.Daily;
import wangdaye.com.geometricweather.common.basic.models.weather.HalfDay;
import wangdaye.com.geometricweather.common.basic.models.weather.Hourly;
import wangdaye.com.geometricweather.common.basic.models.weather.Precipitation;
import wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationDuration;
import wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationProbability;
import wangdaye.com.geometricweather.common.basic.models.weather.Temperature;
import wangdaye.com.geometricweather.common.basic.models.weather.UV;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode;
import wangdaye.com.geometricweather.common.basic.models.weather.Wind;
import wangdaye.com.geometricweather.common.basic.models.weather.WindDegree;
import wangdaye.com.geometricweather.weather.json.apihz.ApihzWeatherResult;

/**
 * Converts the apihz.cn tqybip.php (中国天气网) response into the unified {@link Weather} model.
 *
 * Notes:
 * - {@code nowinfo} carries no weather text; the current condition is taken from today's day/night
 *   text (weather1/weather2) by time of day, same approach as the CMA converter.
 * - Weather text -> {@link WeatherCode} and wind speed -> level reuse {@link CmaResultConverter}
 *   and {@link CommonConverter} (same package) rather than duplicating the keyword/Beaufort tables.
 * - Daily wind has only a Chinese scale string ({@code 微风}/{@code 3级}); used as the display level.
 * - Hourly periods are 3-hourly and cross midnight; dates are walked forward from today, bumping a
 *   day whenever the hour wraps, identical to the CMA hourly handling.
 */
public class ApihzResultConverter {

    private static final TimeZone CN_TZ = TimeZone.getTimeZone("Asia/Shanghai");

    @Nullable
    public static Weather convert(Context context, Location location, ApihzWeatherResult result) {
        if (result == null || result.code == null || result.code != 200) {
            return null;
        }
        try {
            long now = System.currentTimeMillis();
            return new Weather(
                    new Base(location.getCityId(), now, new Date(), now, new Date(), now),
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
    private static Current convertCurrent(Context context, ApihzWeatherResult r) {
        ApihzWeatherResult.NowInfo now = r.nowinfo;

        // nowinfo has no weather text; derive it from today's daily by day/night.
        boolean isDay = isDaytime();
        String weatherText = isDay ? r.weather1 : r.weather2;
        if (TextUtils.isEmpty(weatherText)) {
            weatherText = isDay ? r.weather2 : r.weather1;
        }
        WeatherCode weatherCode = CmaResultConverter.getWeatherCode(weatherText);

        int temp = now != null && now.temperature != null ? (int) Math.round(now.temperature) : 0;
        Integer feelsLike = now != null && now.feelst != null ? (int) Math.round(now.feelst) : null;
        Float precip = now != null ? toFloat(now.precipitation) : null;
        Float humidity = now != null ? toFloat(now.humidity) : null;
        Float pressure = now != null ? toFloat(now.pressure) : null;
        float windKph = now != null && now.windSpeed != null ? (float) (now.windSpeed * 3.6) : 0f;
        String windDir = now != null && now.windDirection != null ? now.windDirection : "";
        float windDegree = now != null && now.windDirectionDegree != null
                ? now.windDirectionDegree.floatValue() : 0f;
        boolean noWindDir = TextUtils.isEmpty(windDir) || windDir.contains("无");

        return new Current(
                weatherText != null ? weatherText : "",
                weatherCode,
                new Temperature(temp, feelsLike, null, null, null, null, null),
                new Precipitation(precip, null, null, null, null),
                new PrecipitationProbability(null, null, null, null, null),
                new Wind(windDir, new WindDegree(windDegree, noWindDir), windKph,
                        CommonConverter.getWindLevel(context, windKph)),
                new UV(null, null, null),
                null,
                humidity, pressure, null, null, null, null, null, null
        );
    }

    @NonNull
    private static List<Daily> convertDailyList(Context context, ApihzWeatherResult r) {
        Map<String, ApihzWeatherResult.SunTime> sunMap = buildSunMap(r.suntimes);
        List<Daily> list = new ArrayList<>();

        // Day 1 is flat on the root; its date has no field of its own, so anchor it to today (CN).
        Date day1 = todayCn();
        addDaily(context, list, sunMap, day1, r.weather1, r.weather2, r.wd1, r.wd2,
                r.winddirection1, r.winddirection2, r.windleve1, r.windleve2);

        // Days 2-7 are nested objects, each with its own date.
        ApihzWeatherResult.DayForecast[] days = {
                r.weatherday2, r.weatherday3, r.weatherday4,
                r.weatherday5, r.weatherday6, r.weatherday7
        };
        for (ApihzWeatherResult.DayForecast d : days) {
            if (d == null) {
                continue;
            }
            addDaily(context, list, sunMap, parseDate(d.date), d.weather1, d.weather2, d.wd1, d.wd2,
                    d.winddirection1, d.winddirection2, d.windleve1, d.windleve2);
        }
        return list;
    }

    private static void addDaily(Context context, List<Daily> list,
                                 Map<String, ApihzWeatherResult.SunTime> sunMap, @Nullable Date date,
                                 String dayText, String nightText, String high, String low,
                                 String dayDir, String nightDir, String dayScale, String nightScale) {
        if (date == null) {
            return;
        }
        HalfDay day = buildHalfDay(context, "Day", dayText, dayDir, dayScale, toInt(high));
        HalfDay night = buildHalfDay(context, "Night", nightText, nightDir, nightScale, toInt(low));
        Astro sun = buildAstro(sunMap, date);
        list.add(new Daily(date, date.getTime(), day, night, sun, null, null, null, null,
                new UV(null, null, null), 0f));
    }

    @NonNull
    private static HalfDay buildHalfDay(Context context, String phase, String text,
                                        String windDirection, String windScale, int temp) {
        String windDir = windDirection != null ? windDirection : "";
        boolean noWindDir = TextUtils.isEmpty(windDir) || windDir.contains("无");
        // Daily wind has no numeric speed, only a Chinese scale string used as the display level.
        String level = !TextUtils.isEmpty(windScale) ? windScale
                : CommonConverter.getWindLevel(context, 0);
        return new HalfDay(
                text != null ? text : "",
                phase,
                CmaResultConverter.getWeatherCode(text),
                new Temperature(temp, null, null, null, null, null, null),
                new Precipitation(null, null, null, null, null),
                new PrecipitationProbability(null, null, null, null, null),
                new PrecipitationDuration(null, null, null, null, null),
                new Wind(windDir, new WindDegree(0, noWindDir), 0f, level),
                null
        );
    }

    @NonNull
    private static List<Hourly> convertHourlyList(Context context, ApihzWeatherResult r) {
        List<Hourly> list = new ArrayList<>();
        List<ApihzWeatherResult.Hour> all = new ArrayList<>();
        for (List<ApihzWeatherResult.Hour> bucket : Arrays.asList(
                r.hour1, r.hour2, r.hour3, r.hour4, r.hour5, r.hour6, r.hour7)) {
            if (bucket != null) {
                all.addAll(bucket);
            }
        }
        if (all.isEmpty()) {
            return list;
        }

        // ponytail: anchor to today and bump a day on each hour-wrap. Good enough for 3-hourly
        // periods; a first period before "now" can be misdated by up to a day. Same as CMA.
        Calendar cal = Calendar.getInstance(CN_TZ);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        int prevHour = -1;
        boolean started = false;

        for (ApihzWeatherResult.Hour h : all) {
            Integer hour = parseHourField(h.time);
            if (hour == null) {
                continue;
            }
            if (!started) {
                started = true;
            } else if (hour <= prevHour) {
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
            prevHour = hour;
            cal.set(Calendar.HOUR_OF_DAY, hour);
            Date date = cal.getTime();

            Integer temp = parseInt(h.temperature);
            float windKph = 0f;
            Float ws = parseDecimal(h.windSpeed);
            if (ws != null) {
                windKph = ws * 3.6f;
            }
            String wdir = h.windDirection != null ? h.windDirection : "";
            boolean noWindDir = TextUtils.isEmpty(wdir) || wdir.contains("无");
            Float precip = parsePrecip(h.precipitation);

            list.add(new Hourly(
                    date, date.getTime(), hour >= 6 && hour < 18,
                    h.weather != null ? h.weather : "",
                    CmaResultConverter.getWeatherCode(h.weather),
                    new Temperature(temp != null ? temp : 0, null, null, null, null, null, null),
                    new Precipitation(precip, null, null, null, null),
                    new PrecipitationProbability(null, null, null, null, null),
                    new Wind(wdir, new WindDegree(0, noWindDir), windKph,
                            CommonConverter.getWindLevel(context, windKph)),
                    new UV(null, null, null)
            ));
        }
        return list;
    }

    @NonNull
    private static List<Alert> convertAlertList(ApihzWeatherResult r) {
        List<Alert> list = new ArrayList<>();
        if (r.alarm == null) {
            return list;
        }
        long id = 0;
        for (ApihzWeatherResult.Alarm a : r.alarm) {
            if (a == null || TextUtils.isEmpty(a.title)) {
                continue;
            }
            Date date = parseDateTime(a.effective);
            long time = date != null ? date.getTime() : System.currentTimeMillis();
            String content = joinNonEmpty(a.signaltype, a.signallevel);
            list.add(new Alert(
                    id++,
                    date != null ? date : new Date(),
                    time,
                    a.title,
                    content,
                    a.type != null ? a.type : "",
                    1,
                    0xFFFFB82B
            ));
        }
        return list;
    }

    // ---- sun times ----

    @NonNull
    private static Map<String, ApihzWeatherResult.SunTime> buildSunMap(
            @Nullable List<ApihzWeatherResult.SunTime> suntimes) {
        Map<String, ApihzWeatherResult.SunTime> map = new HashMap<>();
        if (suntimes == null) {
            return map;
        }
        for (ApihzWeatherResult.SunTime st : suntimes) {
            if (st != null && !TextUtils.isEmpty(st.date)) {
                map.put(st.date, st);
            }
        }
        return map;
    }

    @Nullable
    private static Astro buildAstro(Map<String, ApihzWeatherResult.SunTime> sunMap, Date date) {
        ApihzWeatherResult.SunTime st = sunMap.get(keyOf(date));
        if (st == null) {
            return null;
        }
        return new Astro(combineTime(date, st.sunrise), combineTime(date, st.sunset));
    }

    @Nullable
    private static Date combineTime(Date date, @Nullable String hms) {
        if (date == null || TextUtils.isEmpty(hms)) {
            return null;
        }
        Matcher m = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?").matcher(hms);
        if (!m.find()) {
            return null;
        }
        Calendar cal = Calendar.getInstance(CN_TZ);
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(m.group(1)));
        cal.set(Calendar.MINUTE, Integer.parseInt(m.group(2)));
        cal.set(Calendar.SECOND, m.group(3) != null ? Integer.parseInt(m.group(3)) : 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    // ---- misc helpers ----

    private static boolean isDaytime() {
        int hour = Calendar.getInstance(CN_TZ).get(Calendar.HOUR_OF_DAY);
        return hour >= 6 && hour < 18;
    }

    @NonNull
    private static Date todayCn() {
        Calendar cal = Calendar.getInstance(CN_TZ);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private static String keyOf(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        sdf.setTimeZone(CN_TZ);
        return sdf.format(date);
    }

    private static String joinNonEmpty(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!TextUtils.isEmpty(p)) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(p);
            }
        }
        return sb.toString();
    }

    @Nullable
    private static Float toFloat(@Nullable Double d) {
        return d != null ? d.floatValue() : null;
    }

    // Day/night temps arrive as "33" (root) or 29 (nested, coerced to string by Gson).
    private static int toInt(@Nullable String s) {
        Float f = parseDecimal(s);
        return f != null ? Math.round(f) : 0;
    }

    @Nullable
    private static Integer parseInt(@Nullable String s) {
        Float f = parseDecimal(s);
        return f != null ? Math.round(f) : null;
    }

    // "无降水" -> 0; "0.5mm" -> 0.5; null/other -> null.
    @Nullable
    private static Float parsePrecip(@Nullable String s) {
        if (s == null) {
            return null;
        }
        if (s.contains("无")) {
            return 0f;
        }
        return parseDecimal(s);
    }

    @Nullable
    private static Float parseDecimal(@Nullable String s) {
        if (s == null) {
            return null;
        }
        Matcher m = Pattern.compile("-?\\d+(\\.\\d+)?").matcher(s);
        if (m.find()) {
            try {
                return Float.parseFloat(m.group());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    @Nullable
    private static Integer parseHourField(@Nullable String s) {
        if (s == null) {
            return null;
        }
        Matcher m = Pattern.compile("(\\d{1,2}):\\d{2}").matcher(s);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    // Daily dates: "yyyy/MM/dd" or "yyyy-MM-dd".
    @Nullable
    private static Date parseDate(@Nullable String s) {
        if (TextUtils.isEmpty(s)) {
            return null;
        }
        for (String fmt : new String[] {"yyyy/MM/dd", "yyyy-MM-dd"}) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.US);
                sdf.setTimeZone(CN_TZ);
                return sdf.parse(s);
            } catch (ParseException ignored) {
            }
        }
        return null;
    }

    // Alert effective times come in mixed formats ("yyyy/MM/dd HH:mm" and "yyyy-MM-dd HH:mm:ss").
    @Nullable
    private static Date parseDateTime(@Nullable String s) {
        if (TextUtils.isEmpty(s)) {
            return null;
        }
        for (String fmt : new String[] {
                "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm",
                "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm"}) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.US);
                sdf.setTimeZone(CN_TZ);
                return sdf.parse(s);
            } catch (ParseException ignored) {
            }
        }
        return null;
    }
}
