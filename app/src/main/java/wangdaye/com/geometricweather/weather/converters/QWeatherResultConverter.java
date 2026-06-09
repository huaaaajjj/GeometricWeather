package wangdaye.com.geometricweather.weather.converters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

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
import wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationProbability;
import wangdaye.com.geometricweather.common.basic.models.weather.Temperature;
import wangdaye.com.geometricweather.common.basic.models.weather.UV;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode;
import wangdaye.com.geometricweather.common.basic.models.weather.Wind;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherAirResult;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherDailyResult;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherHourlyResult;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherMinutelyResult;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherNowResult;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherWarningResult;

/**
 * QWeather result converter.
 */

public class QWeatherResultConverter {

    @Nullable
    public static Weather convert(Context context, Location location,
                                  QWeatherNowResult nowResult,
                                  QWeatherDailyResult dailyResult,
                                  QWeatherHourlyResult hourlyResult,
                                  QWeatherMinutelyResult minutelyResult,
                                  QWeatherAirResult airResult,
                                  QWeatherWarningResult warningResult) {
        if (nowResult == null || !"200".equals(nowResult.code)) {
            return null;
        }

        try {
            return new Weather(
                    convertBase(location, nowResult),
                    convertCurrent(nowResult),
                    convertDailyList(dailyResult),
                    convertHourlyList(hourlyResult),
                    convertMinutelyList(minutelyResult),
                    convertAlertList(warningResult),
                    convertAirQuality(airResult),
                    null // yesterday
            );
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    private static Base convertBase(Location location, QWeatherNowResult result) {
        return new Base(
                location.getCityId(),
                "Asia/Shanghai", // QWeather is China-focused
                System.currentTimeMillis()
        );
    }

    @Nullable
    private static Current convertCurrent(QWeatherNowResult result) {
        if (result.now == null) {
            return null;
        }

        return new Current(
                parseFloat(result.now.temp),
                parseFloat(result.now.feelsLike),
                new Precipitation(parseFloat(result.now.precip), null, null, null),
                new Wind(
                        parseFloat(result.now.windSpeed),
                        parseInt(result.now.wind360),
                        null
                ),
                null, // UV
                null, // airQuality
                parseInt(result.now.humidity),
                parseFloat(result.now.pressure),
                parseFloat(result.now.vis),
                parseFloat(result.now.dew),
                parseInt(result.now.cloud),
                convertWeatherCode(result.now.icon),
                result.now.text
        );
    }

    @NonNull
    private static List<Daily> convertDailyList(QWeatherDailyResult result) {
        List<Daily> dailyList = new ArrayList<>();

        if (result == null || result.daily == null) {
            return dailyList;
        }

        for (QWeatherDailyResult.Daily daily : result.daily) {
            dailyList.add(new Daily(
                    daily.fxDate,
                    new HalfDay(
                            "Day",
                            convertWeatherCode(daily.iconDay),
                            daily.textDay,
                            new Temperature(parseFloat(daily.tempMax), null, null, null),
                            new Precipitation(parseFloat(daily.precip), null, null, null),
                            new PrecipitationProbability(null, null, null, null, null),
                            new Wind(parseFloat(daily.windSpeedDay), parseInt(daily.wind360Day), null),
                            null, // cloudCover
                            null  // weatherDescription
                    ),
                    new HalfDay(
                            "Night",
                            convertWeatherCode(daily.iconNight),
                            daily.textNight,
                            new Temperature(null, null, parseFloat(daily.tempMin), null),
                            new Precipitation(parseFloat(daily.precip), null, null, null),
                            new PrecipitationProbability(null, null, null, null, null),
                            new Wind(parseFloat(daily.windSpeedNight), parseInt(daily.wind360Night), null),
                            null, // cloudCover
                            null  // weatherDescription
                    ),
                    null, // sun
                    null, // moon
                    null, // moonPhase
                    new UV(parseFloat(daily.uvIndex), null),
                    null, // airQuality
                    null, // pollen
                    null  // hoursOfSun
            ));
        }

        return dailyList;
    }

    @NonNull
    private static List<Hourly> convertHourlyList(QWeatherHourlyResult result) {
        List<Hourly> hourlyList = new ArrayList<>();

        if (result == null || result.hourly == null) {
            return hourlyList;
        }

        for (QWeatherHourlyResult.Hourly hourly : result.hourly) {
            hourlyList.add(new Hourly(
                    hourly.fxTime,
                    new Temperature(parseFloat(hourly.temp), null, null, null),
                    new Precipitation(parseFloat(hourly.precip), null, null, null),
                    new PrecipitationProbability(parseFloat(hourly.pop), null, null, null, null),
                    new Wind(parseFloat(hourly.windSpeed), parseInt(hourly.wind360), null),
                    null, // UV
                    convertWeatherCode(hourly.icon),
                    parseInt(hourly.cloud),
                    null  // isDaylight
            ));
        }

        return hourlyList;
    }

    @Nullable
    private static List<Minutely> convertMinutelyList(QWeatherMinutelyResult result) {
        if (result == null || result.minutely == null) {
            return null;
        }

        List<Minutely> minutelyList = new ArrayList<>();
        for (QWeatherMinutelyResult.Minutely minutely : result.minutely) {
            minutelyList.add(new Minutely(
                    minutely.fxTime,
                    null, // weatherCode
                    null, // weatherText
                    parseFloat(minutely.precip),
                    null, // dBz
                    null  // cloudCover
            ));
        }

        return minutelyList;
    }

    @Nullable
    private static List<Alert> convertAlertList(QWeatherWarningResult result) {
        if (result == null || result.warning == null) {
            return null;
        }

        List<Alert> alertList = new ArrayList<>();
        for (QWeatherWarningResult.Warning warning : result.warning) {
            alertList.add(new Alert(
                    warning.id,
                    warning.title,
                    warning.startTime,
                    warning.endTime,
                    warning.text,
                    warning.level,
                    convertAlertColor(warning.level)
            ));
        }

        return alertList;
    }

    @Nullable
    private static AirQuality convertAirQuality(QWeatherAirResult result) {
        if (result == null || result.now == null) {
            return null;
        }

        return new AirQuality(
                parseInt(result.now.aqi),
                parseFloat(result.now.pm2p5),
                parseFloat(result.now.pm10),
                parseFloat(result.now.so2),
                parseFloat(result.now.no2),
                parseFloat(result.now.o3),
                parseFloat(result.now.co)
        );
    }

    @Nullable
    private static WeatherCode convertWeatherCode(@Nullable String icon) {
        if (icon == null) {
            return null;
        }

        // QWeather icon codes
        // https://dev.qweather.com/docs/start/icons/
        switch (icon) {
            case "100": // 晴
                return WeatherCode.CLEAR;
            case "101": // 多云
            case "102": // 少云
            case "103": // 晴间多云
            case "104": // 阴
                return WeatherCode.CLOUDY;
            case "300": // 阵雨
            case "301": // 强阵雨
            case "302": // 雷阵雨
            case "303": // 强雷阵雨
            case "304": // 雷阵雨伴有冰雹
            case "305": // 小雨
            case "306": // 中雨
            case "307": // 大雨
            case "308": // 极端降雨
            case "309": // 毛毛雨/细雨
            case "310": // 暴雨
            case "311": // 大暴雨
            case "312": // 特大暴雨
            case "313": // 冻雨
            case "314": // 小到中雨
            case "315": // 中到大雨
            case "316": // 大到暴雨
            case "317": // 暴雨到大暴雨
            case "318": // 大暴雨到特大暴雨
            case "399": // 雨
                return WeatherCode.RAIN;
            case "400": // 小雪
            case "401": // 中雪
            case "402": // 大雪
            case "403": // 暴雪
            case "404": // 雨夹雪
            case "405": // 雨雪天气
            case "406": // 阵雨夹雪
            case "407": // 阵雪
            case "408": // 小到中雪
            case "409": // 中到大雪
            case "410": // 大到暴雪
            case "499": // 雪
                return WeatherCode.SNOW;
            case "500": // 薄雾
            case "501": // 雾
            case "502": // 霾
            case "503": // 扬沙
            case "504": // 浮尘
            case "507": // 沙尘暴
            case "508": // 强沙尘暴
            case "509": // 浓雾
            case "510": // 强浓雾
            case "511": // 中度霾
            case "512": // 重度霾
            case "513": // 严重霾
            case "514": // 大雾
            case "515": // 特强浓雾
                return WeatherCode.FOG;
            case "900": // 热
            case "901": // 冷
                return WeatherCode.CLEAR;
            default:
                return WeatherCode.CLEAR;
        }
    }

    @Nullable
    private static Integer convertAlertColor(@Nullable String level) {
        if (level == null) {
            return null;
        }

        // QWeather alert levels
        // https://dev.qweather.com/docs/api/warning/weather-warning/
        switch (level) {
            case "蓝色":
            case "Blue":
                return 1;
            case "黄色":
            case "Yellow":
                return 2;
            case "橙色":
            case "Orange":
                return 3;
            case "红色":
            case "Red":
                return 4;
            default:
                return 1;
        }
    }

    @Nullable
    private static Float parseFloat(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private static Integer parseInt(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
