package wangdaye.com.geometricweather.weather.converters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Date;
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
import wangdaye.com.geometricweather.common.basic.models.weather.WindDegree;
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
                    convertCurrent(context, nowResult),
                    convertDailyList(context, dailyResult),
                    convertHourlyList(context, hourlyResult),
                    convertMinutelyList(minutelyResult),
                    convertAlertList(warningResult),
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
                System.currentTimeMillis(),
                new Date(),
                System.currentTimeMillis(),
                new Date(),
                System.currentTimeMillis()
        );
    }

    @Nullable
    private static Current convertCurrent(Context context, QWeatherNowResult result) {
        if (result.now == null) {
            return null;
        }

        return new Current(
                result.now.text != null ? result.now.text : "Unknown",
                convertWeatherCode(result.now.icon),
                new Temperature(
                        parseFloat(result.now.temp),
                        parseFloat(result.now.feelsLike),
                        null, null, null, null, null
                ),
                new Precipitation(
                        parseFloat(result.now.precip),
                        null, null, null, null
                ),
                new PrecipitationProbability(null, null, null, null, null),
                new Wind(
                        result.now.windDir != null ? result.now.windDir : "N",
                        new WindDegree(parseInt(result.now.wind360), false),
                        parseFloat(result.now.windSpeed),
                        CommonConverter.getWindLevel(context, parseFloat(result.now.windSpeed))
                ),
                new UV(null, null, null),
                new AirQuality(null, null, null, null, null, null, null, null),
                parseFloat(result.now.humidity),
                parseFloat(result.now.pressure),
                parseFloat(result.now.vis),
                parseFloat(result.now.dew),
                parseInt(result.now.cloud),
                null, // ceiling
                null, // dailyForecast
                null  // hourlyForecast
        );
    }

    @NonNull
    private static List<Daily> convertDailyList(Context context, QWeatherDailyResult result) {
        List<Daily> dailyList = new ArrayList<>();

        if (result == null || result.daily == null) {
            return dailyList;
        }

        for (QWeatherDailyResult.Daily daily : result.daily) {
            dailyList.add(new Daily(
                    daily.fxDate,
                    new HalfDay(
                            "Day",
                            daily.textDay,
                            convertWeatherCode(daily.iconDay),
                            new Temperature(
                                    parseFloat(daily.tempMax),
                                    null, null, null, null, null, null
                            ),
                            new Precipitation(
                                    parseFloat(daily.precip),
                                    null, null, null, null
                            ),
                            new PrecipitationProbability(null, null, null, null, null),
                            new Wind(
                                    daily.windDirDay != null ? daily.windDirDay : "N",
                                    new WindDegree(parseInt(daily.wind360Day), false),
                                    parseFloat(daily.windSpeedDay),
                                    CommonConverter.getWindLevel(context, parseFloat(daily.windSpeedDay))
                            ),
                            null, // cloudCover
                            null  // weatherDescription
                    ),
                    new HalfDay(
                            "Night",
                            daily.textNight,
                            convertWeatherCode(daily.iconNight),
                            new Temperature(
                                    parseFloat(daily.tempMin),
                                    null, null, null, null, null, null
                            ),
                            new Precipitation(
                                    parseFloat(daily.precip),
                                    null, null, null, null
                            ),
                            new PrecipitationProbability(null, null, null, null, null),
                            new Wind(
                                    daily.windDirNight != null ? daily.windDirNight : "N",
                                    new WindDegree(parseInt(daily.wind360Night), false),
                                    parseFloat(daily.windSpeedNight),
                                    CommonConverter.getWindLevel(context, parseFloat(daily.windSpeedNight))
                            ),
                            null, // cloudCover
                            null  // weatherDescription
                    ),
                    null, // sun
                    null, // moon
                    null, // moonPhase
                    new UV(parseFloat(daily.uvIndex), null, null),
                    null, // airQuality
                    null, // pollen
                    null  // hoursOfSun
            ));
        }

        return dailyList;
    }

    @NonNull
    private static List<Hourly> convertHourlyList(Context context, QWeatherHourlyResult result) {
        List<Hourly> hourlyList = new ArrayList<>();

        if (result == null || result.hourly == null) {
            return hourlyList;
        }

        for (QWeatherHourlyResult.Hourly hourly : result.hourly) {
            hourlyList.add(new Hourly(
                    hourly.fxTime,
                    hourly.text,
                    convertWeatherCode(hourly.icon),
                    new Temperature(
                            parseFloat(hourly.temp),
                            null, null, null, null, null, null
                    ),
                    new Precipitation(
                            parseFloat(hourly.precip),
                            null, null, null, null
                    ),
                    new PrecipitationProbability(
                            parseFloat(hourly.pop),
                            null, null, null, null
                    ),
                    new Wind(
                            hourly.windDir != null ? hourly.windDir : "N",
                            new WindDegree(parseInt(hourly.wind360), false),
                            parseFloat(hourly.windSpeed),
                            CommonConverter.getWindLevel(context, parseFloat(hourly.windSpeed))
                    ),
                    new UV(null, null, null),
                    null, // airQuality
                    null  // isDaylight
            ));
        }

        return hourlyList;
    }

    @NonNull
    private static List<Minutely> convertMinutelyList(QWeatherMinutelyResult result) {
        List<Minutely> minutelyList = new ArrayList<>();

        if (result == null || result.minutely == null) {
            return minutelyList;
        }

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

    @NonNull
    private static List<Alert> convertAlertList(QWeatherWarningResult result) {
        List<Alert> alertList = new ArrayList<>();

        if (result == null || result.warning == null) {
            return alertList;
        }

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
    private static WeatherCode convertWeatherCode(@Nullable String icon) {
        if (icon == null) {
            return null;
        }

        switch (icon) {
            case "100":
                return WeatherCode.CLEAR;
            case "101":
            case "102":
            case "103":
            case "104":
                return WeatherCode.CLOUDY;
            case "300":
            case "301":
            case "302":
            case "303":
            case "304":
            case "305":
            case "306":
            case "307":
            case "308":
            case "309":
            case "310":
            case "311":
            case "312":
            case "313":
            case "314":
            case "315":
            case "316":
            case "317":
            case "318":
            case "399":
                return WeatherCode.RAIN;
            case "400":
            case "401":
            case "402":
            case "403":
            case "404":
            case "405":
            case "406":
            case "407":
            case "408":
            case "409":
            case "410":
            case "499":
                return WeatherCode.SNOW;
            case "500":
            case "501":
            case "502":
            case "503":
            case "504":
            case "507":
            case "508":
            case "509":
            case "510":
            case "511":
            case "512":
            case "513":
            case "514":
            case "515":
                return WeatherCode.FOG;
            case "900":
            case "901":
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
