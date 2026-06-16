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
import wangdaye.com.geometricweather.weather.json.cma.CmaWeatherResult;

/**
 * Converts the China Meteorological Administration (weather.cma.cn) responses into the
 * unified {@link Weather} model.
 *
 * Notes specific to CMA:
 * - The {@code now} block has no weather text/code, so the current condition is taken from
 *   today's daily day/night text.
 * - Daily wind has only a Chinese scale string ({@code 微风}/{@code 3级}), no numeric speed.
 * - Hourly forecast has no JSON endpoint; it is scraped from the {@code hour-table} HTML.
 *   The weather icon number in the HTML is mapped back to a {@link WeatherCode} via the
 *   {@code code -> text} pairs already present in the daily JSON (self-consistent).
 */
public class CmaResultConverter {

    private static final TimeZone CN_TZ = TimeZone.getTimeZone("Asia/Shanghai");

    @Nullable
    public static Weather convert(Context context, Location location,
                                  CmaWeatherResult result, @Nullable String hourlyHtml) {
        if (result == null || result.data == null) {
            return null;
        }
        try {
            CmaWeatherResult.Data data = result.data;

            String cityId = location.getCityId();
            if (TextUtils.isEmpty(cityId) && data.location != null) {
                cityId = data.location.id;
            }

            Map<Integer, WeatherCode> iconCodeMap = buildIconCodeMap(data.daily);

            return new Weather(
                    new Base(cityId, System.currentTimeMillis(), new Date(),
                            System.currentTimeMillis(), new Date(), System.currentTimeMillis()),
                    convertCurrent(context, data),
                    null,
                    convertDailyList(context, data),
                    convertHourlyList(context, hourlyHtml, iconCodeMap),
                    new ArrayList<>(),
                    convertAlertList(data)
            );
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    private static Current convertCurrent(Context context, CmaWeatherResult.Data data) {
        CmaWeatherResult.Now now = data.now;

        // The now block carries no weather text; derive it from today's daily by day/night.
        boolean isDay = isDaytime();
        String weatherText = "";
        if (data.daily != null && !data.daily.isEmpty()) {
            CmaWeatherResult.Daily today = data.daily.get(0);
            weatherText = isDay ? today.dayText : today.nightText;
            if (TextUtils.isEmpty(weatherText)) {
                weatherText = isDay ? today.nightText : today.dayText;
            }
        }
        WeatherCode weatherCode = getWeatherCode(weatherText);

        int temp = now != null && now.temperature != null ? now.temperature.intValue() : 0;
        Integer feelsLike = now != null && now.feelst != null ? now.feelst.intValue() : null;
        Float precip = now != null ? toFloat(now.precipitation) : null;
        Float humidity = now != null ? toFloat(now.humidity) : null;
        Float pressure = now != null ? toFloat(now.pressure) : null;
        float windKph = now != null && now.windSpeed != null ? msToKph(now.windSpeed) : 0f;
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
    private static List<Daily> convertDailyList(Context context, CmaWeatherResult.Data data) {
        List<Daily> list = new ArrayList<>();
        if (data.daily == null) {
            return list;
        }
        for (CmaWeatherResult.Daily d : data.daily) {
            Date date = parseDate(d.date);
            if (date == null) {
                continue;
            }
            int high = d.high != null ? d.high.intValue() : 0;
            int low = d.low != null ? d.low.intValue() : 0;

            HalfDay day = buildHalfDay(context, "Day", d.dayText, d.dayWindDirection,
                    d.dayWindScale, high);
            HalfDay night = buildHalfDay(context, "Night", d.nightText, d.nightWindDirection,
                    d.nightWindScale, low);

            list.add(new Daily(date, date.getTime(), day, night, null, null, null,
                    null, null, new UV(null, null, null), 0f));
        }
        return list;
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
                getWeatherCode(text),
                new Temperature(temp, null, null, null, null, null, null),
                new Precipitation(null, null, null, null, null),
                new PrecipitationProbability(null, null, null, null, null),
                new PrecipitationDuration(null, null, null, null, null),
                new Wind(windDir, new WindDegree(0, noWindDir), 0f, level),
                null
        );
    }

    @NonNull
    private static List<Hourly> convertHourlyList(Context context, @Nullable String html,
                                                  Map<Integer, WeatherCode> iconCodeMap) {
        List<Hourly> list = new ArrayList<>();
        if (TextUtils.isEmpty(html)) {
            return list;
        }
        try {
            Pattern tablePattern = Pattern.compile(
                    "(?s)<table[^>]*class=\"hour-table\"[^>]*>(.*?)</table>");
            Matcher tableMatcher = tablePattern.matcher(html);

            Calendar cal = Calendar.getInstance(CN_TZ);
            int prevHour = -1;
            boolean started = false;

            while (tableMatcher.find()) {
                String table = tableMatcher.group(1);
                Map<String, List<String>> rows = parseRows(table);

                List<String> times = rows.get("时间");
                if (times == null || times.isEmpty()) {
                    continue;
                }
                List<String> weathers = rows.get("天气");
                List<String> temps = rows.get("气温");
                List<String> winds = rows.get("风速");
                List<String> windDirs = rows.get("风向");

                for (int i = 0; i < times.size(); i++) {
                    Integer hour = parseHour(times.get(i));
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
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    Date date = cal.getTime();

                    WeatherCode code = WeatherCode.CLEAR;
                    Integer icon = at(weathers, i) != null ? extractIcon(weathers.get(i)) : null;
                    if (icon != null && iconCodeMap.containsKey(icon)) {
                        code = iconCodeMap.get(icon);
                    }

                    Integer temp = parseNumber(at(temps, i));
                    float windKph = 0f;
                    Float ws = parseDecimal(at(winds, i));
                    if (ws != null) {
                        windKph = ws * 3.6f;
                    }
                    String wdir = at(windDirs, i) != null ? stripTags(windDirs.get(i)) : "";
                    boolean noWindDir = TextUtils.isEmpty(wdir) || wdir.contains("无");

                    list.add(new Hourly(
                            date, date.getTime(), hour >= 6 && hour < 18,
                            "", code,
                            new Temperature(temp != null ? temp : 0, null, null, null, null, null, null),
                            new Precipitation(null, null, null, null, null),
                            new PrecipitationProbability(null, null, null, null, null),
                            new Wind(wdir, new WindDegree(0, noWindDir), windKph,
                                    CommonConverter.getWindLevel(context, windKph)),
                            new UV(null, null, null)
                    ));
                }
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }
        return list;
    }

    @NonNull
    private static List<Alert> convertAlertList(CmaWeatherResult.Data data) {
        List<Alert> list = new ArrayList<>();
        if (data.alarm == null) {
            return list;
        }
        long id = 0;
        for (CmaWeatherResult.Alarm a : data.alarm) {
            if (a == null) {
                continue;
            }
            String title = !TextUtils.isEmpty(a.headline) ? a.headline : a.title;
            if (TextUtils.isEmpty(title) && TextUtils.isEmpty(a.description)) {
                continue;
            }
            Date date = parseDateTime(a.effective);
            long time = date != null ? date.getTime() : System.currentTimeMillis();
            list.add(new Alert(
                    id++,
                    date != null ? date : new Date(),
                    time,
                    title != null ? title : "",
                    a.description != null ? a.description : "",
                    a.type != null ? a.type : "",
                    1,
                    0xFFFFB82B
            ));
        }
        return list;
    }

    // builds icon-number -> WeatherCode from the daily code/text pairs (self-consistent).
    @NonNull
    private static Map<Integer, WeatherCode> buildIconCodeMap(@Nullable List<CmaWeatherResult.Daily> daily) {
        Map<Integer, WeatherCode> map = new HashMap<>();
        if (daily == null) {
            return map;
        }
        for (CmaWeatherResult.Daily d : daily) {
            if (d.dayCode != null && !TextUtils.isEmpty(d.dayText)) {
                map.put(d.dayCode, getWeatherCode(d.dayText));
            }
            if (d.nightCode != null && !TextUtils.isEmpty(d.nightText)) {
                map.put(d.nightCode, getWeatherCode(d.nightText));
            }
        }
        return map;
    }

    @NonNull
    static WeatherCode getWeatherCode(@Nullable String text) {
        if (TextUtils.isEmpty(text)) {
            return WeatherCode.CLEAR;
        }
        if (text.contains("雷") && text.contains("雨")) {
            return WeatherCode.THUNDERSTORM;
        }
        if (text.contains("雷")) {
            return WeatherCode.THUNDER;
        }
        if (text.contains("雹")) {
            return WeatherCode.HAIL;
        }
        if (text.contains("雨夹雪") || text.contains("雨雪") || (text.contains("雨") && text.contains("雪"))) {
            return WeatherCode.SLEET;
        }
        if (text.contains("雪")) {
            return WeatherCode.SNOW;
        }
        if (text.contains("雨")) {
            return WeatherCode.RAIN;
        }
        if (text.contains("雾")) {
            return WeatherCode.FOG;
        }
        if (text.contains("霾") || text.contains("沙") || text.contains("尘")) {
            return WeatherCode.HAZE;
        }
        if (text.contains("风") || text.contains("飑") || text.contains("龙卷")) {
            return WeatherCode.WIND;
        }
        if (text.contains("阴")) {
            return WeatherCode.CLOUDY;
        }
        if (text.contains("多云")) {
            return WeatherCode.PARTLY_CLOUDY;
        }
        if (text.contains("晴")) {
            return WeatherCode.CLEAR;
        }
        return WeatherCode.CLEAR;
    }

    // ---- HTML helpers ----

    private static Map<String, List<String>> parseRows(String tableHtml) {
        Map<String, List<String>> rows = new HashMap<>();
        Matcher rowMatcher = Pattern.compile("(?s)<tr[^>]*>(.*?)</tr>").matcher(tableHtml);
        while (rowMatcher.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cellMatcher = Pattern.compile("(?s)<td[^>]*>(.*?)</td>").matcher(rowMatcher.group(1));
            while (cellMatcher.find()) {
                cells.add(cellMatcher.group(1));
            }
            if (cells.isEmpty()) {
                continue;
            }
            String label = stripTags(cells.get(0));
            if (TextUtils.isEmpty(label)) {
                continue;
            }
            rows.put(label, cells.subList(1, cells.size()));
        }
        return rows;
    }

    @Nullable
    private static String at(@Nullable List<String> list, int i) {
        return list != null && i < list.size() ? list.get(i) : null;
    }

    @Nullable
    private static Integer extractIcon(String cellHtml) {
        Matcher m = Pattern.compile("w(\\d+)\\.png").matcher(cellHtml);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static String stripTags(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("(?s)<[^>]*>", "")
                .replace("&nbsp;", " ")
                .trim();
    }

    @Nullable
    private static Integer parseHour(String cell) {
        String t = stripTags(cell);
        Matcher m = Pattern.compile("(\\d{1,2}):\\d{2}").matcher(t);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    @Nullable
    private static Integer parseNumber(@Nullable String cell) {
        Float f = parseDecimal(cell);
        return f != null ? Math.round(f) : null;
    }

    @Nullable
    private static Float parseDecimal(@Nullable String cell) {
        if (cell == null) {
            return null;
        }
        Matcher m = Pattern.compile("-?\\d+(\\.\\d+)?").matcher(stripTags(cell));
        if (m.find()) {
            try {
                return Float.parseFloat(m.group());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    // ---- misc helpers ----

    private static boolean isDaytime() {
        int hour = Calendar.getInstance(CN_TZ).get(Calendar.HOUR_OF_DAY);
        return hour >= 6 && hour < 18;
    }

    @Nullable
    private static Float toFloat(@Nullable Double d) {
        return d != null ? d.floatValue() : null;
    }

    private static float msToKph(double ms) {
        return (float) (ms * 3.6);
    }

    @Nullable
    private static Date parseDate(@Nullable String s) {
        if (s == null) {
            return null;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
            sdf.setTimeZone(CN_TZ);
            return sdf.parse(s);
        } catch (ParseException e) {
            return null;
        }
    }

    @Nullable
    private static Date parseDateTime(@Nullable String s) {
        if (s == null) {
            return null;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US);
            sdf.setTimeZone(CN_TZ);
            return sdf.parse(s);
        } catch (ParseException e) {
            return null;
        }
    }
}
