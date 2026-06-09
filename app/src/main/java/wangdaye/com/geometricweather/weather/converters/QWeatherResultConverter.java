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
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherAirResult;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherDailyResult;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherHourlyResult;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherMinutelyResult;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherNowResult;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherWarningResult;

public class QWeatherResultConverter {

    @Nullable
    public static Weather convert(Context context, Location location,
                                  QWeatherNowResult nowResult,
                                  QWeatherDailyResult dailyResult,
                                  QWeatherHourlyResult hourlyResult,
                                  QWeatherMinutelyResult minutelyResult,
                                  QWeatherAirResult airResult,
                                  QWeatherWarningResult warningResult) {
        if (nowResult == null || !"200".equals(nowResult.code)) return null;
        try {
            return new Weather(
                    new Base(location.getCityId(), System.currentTimeMillis(),
                            new Date(), System.currentTimeMillis(),
                            new Date(), System.currentTimeMillis()),
                    convertCurrent(context, nowResult),
                    null,
                    convertDailyList(context, dailyResult),
                    convertHourlyList(context, hourlyResult),
                    convertMinutelyList(minutelyResult),
                    convertAlertList(warningResult)
            );
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    private static Current convertCurrent(Context context, QWeatherNowResult result) {
        QWeatherNowResult.Now n = result.now;
        int temp = parseFloat(n.temp);
        Integer feelsLike = parseInt2(n.feelsLike);
        Float precip = parseFloat2(n.precip);
        float windSpeed = parseFloat(n.windSpeed);
        int windDir = parseInt(n.wind360);
        Float humidity = parseFloat2(n.humidity);
        Float pressure = parseFloat2(n.pressure);
        Float visibility = parseFloat2(n.vis);
        Integer dew = parseInt2(n.dew);
        Integer cloud = parseInt2(n.cloud);

        return new Current(
                n.text != null ? n.text : "Unknown",
                convertWeatherCode(n.icon),
                new Temperature(temp, feelsLike, null, null, null, null, null),
                new Precipitation(precip, null, null, null, null),
                new PrecipitationProbability(null, null, null, null, null),
                new Wind(n.windDir != null ? n.windDir : "N",
                        new WindDegree(windDir, false), windSpeed,
                        CommonConverter.getWindLevel(context, windSpeed)),
                new UV(null, null, null),
                new AirQuality(null, null, null, null, null, null, null, null),
                humidity, pressure, visibility, dew, cloud, null, null, null
        );
    }

    @NonNull
    private static List<Daily> convertDailyList(Context context, QWeatherDailyResult result) {
        List<Daily> list = new ArrayList<>();
        if (result == null || result.daily == null) return list;

        for (QWeatherDailyResult.Daily d : result.daily) {
            Date date = parseDate(d.fxDate);
            if (date == null) continue;

            float windSpeedDay = parseFloat(d.windSpeedDay);
            int windDirDay = parseInt(d.wind360Day);
            float windSpeedNight = parseFloat(d.windSpeedNight);
            int windDirNight = parseInt(d.wind360Night);
            Integer uvIndex = parseInt2(d.uvIndex);
            Float precip = parseFloat2(d.precip);

            HalfDay day = new HalfDay(
                    d.textDay != null ? d.textDay : "Unknown", "Day", convertWeatherCode(d.iconDay),
                    new Temperature(parseInt(d.tempMax), null, null, null, null, null, null),
                    new Precipitation(precip, null, null, null, null),
                    new PrecipitationProbability(null, null, null, null, null),
                    new PrecipitationDuration(null, null, null, null),
                    new Wind(d.windDirDay != null ? d.windDirDay : "N",
                            new WindDegree(windDirDay, false), windSpeedDay,
                            CommonConverter.getWindLevel(context, windSpeedDay)),
                    null
            );
            HalfDay night = new HalfDay(
                    d.textNight != null ? d.textNight : "Unknown", "Night", convertWeatherCode(d.iconNight),
                    new Temperature(parseInt(d.tempMin), null, null, null, null, null, null),
                    new Precipitation(precip, null, null, null, null),
                    new PrecipitationProbability(null, null, null, null, null),
                    new PrecipitationDuration(null, null, null, null),
                    new Wind(d.windDirNight != null ? d.windDirNight : "N",
                            new WindDegree(windDirNight, false), windSpeedNight,
                            CommonConverter.getWindLevel(context, windSpeedNight)),
                    null
            );
            list.add(new Daily(date, date.getTime(), day, night, null, null, null, null, null,
                    new UV(uvIndex, null, null), 0f));
        }
        return list;
    }

    @NonNull
    private static List<Hourly> convertHourlyList(Context context, QWeatherHourlyResult result) {
        List<Hourly> list = new ArrayList<>();
        if (result == null || result.hourly == null) return list;

        for (QWeatherHourlyResult.Hourly h : result.hourly) {
            Date date = parseDateTime(h.fxTime);
            if (date == null) continue;

            float windSpeed = parseFloat(h.windSpeed);
            int windDir = parseInt(h.wind360);
            Float precip = parseFloat2(h.precip);
            Float pop = parseFloat2(h.pop);

            list.add(new Hourly(
                    date, date.getTime(), true,
                    h.text != null ? h.text : "Unknown", convertWeatherCode(h.icon),
                    new Temperature(parseInt(h.temp), null, null, null, null, null, null),
                    new Precipitation(precip, null, null, null, null),
                    new PrecipitationProbability(pop, null, null, null, null),
                    new Wind(h.windDir != null ? h.windDir : "N",
                            new WindDegree(windDir, false), windSpeed,
                            CommonConverter.getWindLevel(context, windSpeed)),
                    new UV(null, null, null)
            ));
        }
        return list;
    }

    @NonNull
    private static List<Minutely> convertMinutelyList(QWeatherMinutelyResult result) {
        List<Minutely> list = new ArrayList<>();
        if (result == null || result.minutely == null) return list;

        for (QWeatherMinutelyResult.Minutely m : result.minutely) {
            Date date = parseDateTime(m.fxTime);
            if (date == null) continue;
            Float precip = parseFloat2(m.precip);
            list.add(new Minutely(date, date.getTime(), true, null, null, 5, null, null));
        }
        return list;
    }

    @NonNull
    private static List<Alert> convertAlertList(QWeatherWarningResult result) {
        List<Alert> list = new ArrayList<>();
        if (result == null || result.warning == null) return list;

        long id = 0;
        for (QWeatherWarningResult.Warning w : result.warning) {
            Date startDate = parseDateTime(w.startTime);
            long time = startDate != null ? startDate.getTime() : System.currentTimeMillis();
            int priority = convertAlertPriority(w.level);
            int color = convertAlertColor(w.level);
            list.add(new Alert(id++, startDate != null ? startDate : new Date(), time,
                    w.title, w.text, w.type, priority, color));
        }
        return list;
    }

    @Nullable
    private static WeatherCode convertWeatherCode(@Nullable String icon) {
        if (icon == null) return null;
        switch (icon) {
            case "100": return WeatherCode.CLEAR;
            case "101": case "102": case "103": case "104": return WeatherCode.CLOUDY;
            case "300": case "301": case "302": case "303": case "304":
            case "305": case "306": case "307": case "308": case "309":
            case "310": case "311": case "312": case "313": case "314":
            case "315": case "316": case "317": case "318": case "399": return WeatherCode.RAIN;
            case "400": case "401": case "402": case "403": case "404":
            case "405": case "406": case "407": case "408": case "409":
            case "410": case "499": return WeatherCode.SNOW;
            case "500": case "501": case "502": case "503": case "504":
            case "507": case "508": case "509": case "510": case "511":
            case "512": case "513": case "514": case "515": return WeatherCode.FOG;
            default: return WeatherCode.CLEAR;
        }
    }

    private static int convertAlertPriority(@Nullable String level) {
        if (level == null) return 1;
        switch (level) {
            case "蓝色": case "Blue": return 1;
            case "黄色": case "Yellow": return 2;
            case "橙色": case "Orange": return 3;
            case "红色": case "Red": return 4;
            default: return 1;
        }
    }

    private static int convertAlertColor(@Nullable String level) {
        if (level == null) return 0xFFFFB82B;
        switch (level) {
            case "蓝色": case "Blue": return 0xFF2196F3;
            case "黄色": case "Yellow": return 0xFFFFEB3B;
            case "橙色": case "Orange": return 0xFFFF9800;
            case "红色": case "Red": return 0xFFF44336;
            default: return 0xFFFFB82B;
        }
    }

    private static int parseFloat(String s) {
        if (s == null || s.isEmpty()) return 0;
        try { return (int) Float.parseFloat(s); }
        catch (NumberFormatException e) { return 0; }
    }

    @Nullable
    private static Float parseFloat2(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Float.parseFloat(s); }
        catch (NumberFormatException e) { return null; }
    }

    private static int parseInt(String s) {
        if (s == null || s.isEmpty()) return 0;
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) { return 0; }
    }

    @Nullable
    private static Integer parseInt2(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) { return null; }
    }

    @Nullable
    private static Date parseDate(String s) {
        try { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s); }
        catch (ParseException e) { return null; }
    }

    @Nullable
    private static Date parseDateTime(String s) {
        try { return new SimpleDateFormat("yyyy-MM-dd'T'HH:mmXXX", Locale.US).parse(s); }
        catch (ParseException e) {
            try { return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(s); }
            catch (ParseException e2) { return null; }
        }
    }
}
