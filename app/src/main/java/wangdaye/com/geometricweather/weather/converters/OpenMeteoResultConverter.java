package wangdaye.com.geometricweather.weather.converters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.weather.AirQuality;
import wangdaye.com.geometricweather.common.basic.models.weather.Base;
import wangdaye.com.geometricweather.common.basic.models.weather.Current;
import wangdaye.com.geometricweather.common.basic.models.weather.Daily;
import wangdaye.com.geometricweather.common.basic.models.weather.HalfDay;
import wangdaye.com.geometricweather.common.basic.models.weather.Hourly;
import wangdaye.com.geometricweather.common.basic.models.weather.Precipitation;
import wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationProbability;
import wangdaye.com.geometricweather.common.basic.models.weather.Temperature;
import wangdaye.com.geometricweather.common.basic.models.weather.UV;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode;
import wangdaye.com.geometricweather.common.basic.models.weather.Wind;
import wangdaye.com.geometricweather.common.basic.models.weather.WindDegree;
import wangdaye.com.geometricweather.weather.json.openmeteo.OpenMeteoResult;

/**
 * Open-Meteo result converter.
 */

public class OpenMeteoResultConverter {

    @Nullable
    public static Weather convert(Context context, Location location, OpenMeteoResult result) {
        if (result == null) {
            return null;
        }

        try {
            return new Weather(
                    convertBase(result),
                    convertCurrent(context, result),
                    convertDailyList(context, result),
                    convertHourlyList(context, result),
                    new ArrayList<>(), // minutely
                    new ArrayList<>(), // alerts
                    null  // yesterday
            );
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    private static Base convertBase(OpenMeteoResult result) {
        return new Base(
                result.latitude + "," + result.longitude,
                System.currentTimeMillis(),
                new Date(),
                System.currentTimeMillis(),
                new Date(),
                System.currentTimeMillis()
        );
    }

    @Nullable
    private static Current convertCurrent(Context context, OpenMeteoResult result) {
        if (result.current == null) {
            return null;
        }

        return new Current(
                getWeatherText(result.current.weatherCode),
                convertWeatherCode(result.current.weatherCode),
                new Temperature(
                        result.current.temperature != null ? result.current.temperature.intValue() : 0,
                        result.current.apparentTemperature != null ? result.current.apparentTemperature.intValue() : null,
                        null, null, null, null, null
                ),
                new Precipitation(
                        result.current.precipitation != null ? result.current.precipitation.floatValue() : null,
                        null, null, null, null
                ),
                new PrecipitationProbability(null, null, null, null, null),
                new Wind(
                        getWindDirection(result.current.windDirection != null ? result.current.windDirection.intValue() : 0),
                        new WindDegree(result.current.windDirection != null ? result.current.windDirection.intValue() : 0, false),
                        result.current.windSpeed != null ? result.current.windSpeed.floatValue() : null,
                        CommonConverter.getWindLevel(context, result.current.windSpeed != null ? result.current.windSpeed.floatValue() : 0)
                ),
                new UV(null, null, null),
                new AirQuality(null, null, null, null, null, null, null, null),
                result.current.humidity != null ? result.current.humidity.floatValue() : null,
                result.current.pressure != null ? result.current.pressure.floatValue() : null,
                null, // visibility
                null, // dewPoint
                result.current.cloudCover != null ? result.current.cloudCover.intValue() : null,
                null, // ceiling
                null, // dailyForecast
                null  // hourlyForecast
        );
    }

    @NonNull
    private static List<Daily> convertDailyList(Context context, OpenMeteoResult result) {
        List<Daily> dailyList = new ArrayList<>();

        if (result.daily == null || result.daily.time == null) {
            return dailyList;
        }

        for (int i = 0; i < result.daily.time.size(); i++) {
            String date = result.daily.time.get(i);
            Integer weatherCode = getValueOrNull(result.daily.weatherCode, i);
            Double tempMax = getValueOrNull(result.daily.temperatureMax, i);
            Double tempMin = getValueOrNull(result.daily.temperatureMin, i);
            Double precipSum = getValueOrNull(result.daily.precipitationSum, i);
            Double precipProb = getValueOrNull(result.daily.precipitationProbabilityMax, i);
            Double windSpeed = getValueOrNull(result.daily.windSpeedMax, i);
            Double windDir = getValueOrNull(result.daily.windDirectionDominant, i);
            Double uvIndex = getValueOrNull(result.daily.uvIndexMax, i);

            dailyList.add(new Daily(
                    date,
                    new HalfDay(
                            "Day",
                            getWeatherText(weatherCode),
                            convertWeatherCode(weatherCode),
                            new Temperature(
                                    tempMax != null ? tempMax.intValue() : 0,
                                    null, null, null, null, null, null
                            ),
                            new Precipitation(
                                    precipSum != null ? precipSum.floatValue() : null,
                                    null, null, null, null
                            ),
                            new PrecipitationProbability(
                                    precipProb != null ? precipProb.floatValue() : null,
                                    null, null, null, null
                            ),
                            new Wind(
                                    getWindDirection(windDir != null ? windDir.intValue() : 0),
                                    new WindDegree(windDir != null ? windDir.intValue() : 0, false),
                                    windSpeed != null ? windSpeed.floatValue() : null,
                                    CommonConverter.getWindLevel(context, windSpeed != null ? windSpeed.floatValue() : 0)
                            ),
                            null, // cloudCover
                            null  // weatherDescription
                    ),
                    new HalfDay(
                            "Night",
                            getWeatherText(weatherCode),
                            convertWeatherCode(weatherCode),
                            new Temperature(
                                    tempMin != null ? tempMin.intValue() : 0,
                                    null, null, null, null, null, null
                            ),
                            new Precipitation(
                                    precipSum != null ? precipSum.floatValue() : null,
                                    null, null, null, null
                            ),
                            new PrecipitationProbability(
                                    precipProb != null ? precipProb.floatValue() : null,
                                    null, null, null, null
                            ),
                            new Wind(
                                    getWindDirection(windDir != null ? windDir.intValue() : 0),
                                    new WindDegree(windDir != null ? windDir.intValue() : 0, false),
                                    windSpeed != null ? windSpeed.floatValue() : null,
                                    CommonConverter.getWindLevel(context, windSpeed != null ? windSpeed.floatValue() : 0)
                            ),
                            null, // cloudCover
                            null  // weatherDescription
                    ),
                    null, // sun
                    null, // moon
                    null, // moonPhase
                    new UV(uvIndex != null ? uvIndex.intValue() : null, null, null),
                    null, // airQuality
                    null, // pollen
                    null  // hoursOfSun
            ));
        }

        return dailyList;
    }

    @NonNull
    private static List<Hourly> convertHourlyList(Context context, OpenMeteoResult result) {
        List<Hourly> hourlyList = new ArrayList<>();

        if (result.hourly == null || result.hourly.time == null) {
            return hourlyList;
        }

        for (int i = 0; i < result.hourly.time.size(); i++) {
            String time = result.hourly.time.get(i);
            Double temp = getValueOrNull(result.hourly.temperature, i);
            Double feelsLike = getValueOrNull(result.hourly.apparentTemperature, i);
            Double precip = getValueOrNull(result.hourly.precipitation, i);
            Double precipProb = getValueOrNull(result.hourly.precipitationProbability, i);
            Integer weatherCode = getValueOrNull(result.hourly.weatherCode, i);
            Double windSpeed = getValueOrNull(result.hourly.windSpeed, i);
            Double windDir = getValueOrNull(result.hourly.windDirection, i);
            Double uvIndex = getValueOrNull(result.hourly.uvIndex, i);
            Integer isDay = getValueOrNull(result.hourly.isDay, i);

            hourlyList.add(new Hourly(
                    time,
                    getWeatherText(weatherCode),
                    convertWeatherCode(weatherCode),
                    new Temperature(
                            temp != null ? temp.intValue() : 0,
                            feelsLike != null ? feelsLike.intValue() : null,
                            null, null, null, null, null
                    ),
                    new Precipitation(
                            precip != null ? precip.floatValue() : null,
                            null, null, null, null
                    ),
                    new PrecipitationProbability(
                            precipProb != null ? precipProb.floatValue() : null,
                            null, null, null, null
                    ),
                    new Wind(
                            getWindDirection(windDir != null ? windDir.intValue() : 0),
                            new WindDegree(windDir != null ? windDir.intValue() : 0, false),
                            windSpeed != null ? windSpeed.floatValue() : null,
                            CommonConverter.getWindLevel(context, windSpeed != null ? windSpeed.floatValue() : 0)
                    ),
                    new UV(uvIndex != null ? uvIndex.intValue() : null, null, null),
                    null, // airQuality
                    isDay != null && isDay == 1
            ));
        }

        return hourlyList;
    }

    @Nullable
    private static WeatherCode convertWeatherCode(@Nullable Integer code) {
        if (code == null) {
            return null;
        }

        // WMO Weather interpretation codes
        switch (code) {
            case 0:
                return WeatherCode.CLEAR;
            case 1:
            case 2:
            case 3:
                return WeatherCode.CLOUDY;
            case 45:
            case 48:
                return WeatherCode.FOG;
            case 51:
            case 53:
            case 55:
                return WeatherCode.RAIN;
            case 56:
            case 57:
                return WeatherCode.SLEET;
            case 61:
            case 63:
            case 65:
                return WeatherCode.RAIN;
            case 66:
            case 67:
                return WeatherCode.SLEET;
            case 71:
            case 73:
            case 75:
            case 77:
                return WeatherCode.SNOW;
            case 80:
            case 81:
            case 82:
                return WeatherCode.RAIN;
            case 85:
            case 86:
                return WeatherCode.SNOW;
            case 95:
                return WeatherCode.THUNDER;
            case 96:
            case 99:
                return WeatherCode.THUNDERSTORM;
            default:
                return WeatherCode.CLEAR;
        }
    }

    @NonNull
    private static String getWeatherText(@Nullable Integer code) {
        if (code == null) {
            return "Unknown";
        }

        switch (code) {
            case 0:
                return "Clear sky";
            case 1:
                return "Mainly clear";
            case 2:
                return "Partly cloudy";
            case 3:
                return "Overcast";
            case 45:
            case 48:
                return "Fog";
            case 51:
            case 53:
            case 55:
                return "Drizzle";
            case 56:
            case 57:
                return "Freezing drizzle";
            case 61:
            case 63:
            case 65:
                return "Rain";
            case 66:
            case 67:
                return "Freezing rain";
            case 71:
            case 73:
            case 75:
            case 77:
                return "Snow";
            case 80:
            case 81:
            case 82:
                return "Rain showers";
            case 85:
            case 86:
                return "Snow showers";
            case 95:
                return "Thunderstorm";
            case 96:
            case 99:
                return "Thunderstorm with hail";
            default:
                return "Unknown";
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
    private static <T> T getValueOrNull(List<T> list, int index) {
        if (list == null || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }
}
