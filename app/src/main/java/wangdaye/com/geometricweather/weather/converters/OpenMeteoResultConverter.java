package wangdaye.com.geometricweather.weather.converters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
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
                    convertCurrent(result),
                    convertDailyList(result),
                    convertHourlyList(result),
                    null, // minutely
                    null, // alerts
                    null, // airQuality
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
                result.timezone,
                System.currentTimeMillis()
        );
    }

    @Nullable
    private static Current convertCurrent(OpenMeteoResult result) {
        if (result.current == null) {
            return null;
        }

        return new Current(
                result.current.temperature != null ? result.current.temperature : 0,
                result.current.apparentTemperature,
                null, // precipitation
                new Wind(
                        result.current.windSpeed != null ? result.current.windSpeed : 0,
                        result.current.windDirection != null ? result.current.windDirection.intValue() : 0,
                        null // gust
                ),
                null, // UV
                null, // airQuality
                result.current.humidity != null ? result.current.humidity.intValue() : 0,
                result.current.pressure != null ? result.current.pressure : 0,
                null, // visibility
                null, // dewPoint
                result.current.cloudCover != null ? result.current.cloudCover.intValue() : 0,
                convertWeatherCode(result.current.weatherCode),
                "Open-Meteo"
        );
    }

    @NonNull
    private static List<Daily> convertDailyList(OpenMeteoResult result) {
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
            String sunrise = getValueOrNull(result.daily.sunrise, i);
            String sunset = getValueOrNull(result.daily.sunset, i);

            dailyList.add(new Daily(
                    date,
                    new HalfDay(
                            "Day",
                            convertWeatherCode(weatherCode),
                            "",
                            new Temperature(tempMax, null, tempMin, null),
                            new Precipitation(null, null, null, null),
                            new PrecipitationProbability(precipProb, null, null, null, null),
                            new Wind(windSpeed != null ? windSpeed : 0, windDir != null ? windDir.intValue() : 0, null),
                            null, // cloudCover
                            null  // weatherDescription
                    ),
                    new HalfDay(
                            "Night",
                            convertWeatherCode(weatherCode),
                            "",
                            new Temperature(tempMax, null, tempMin, null),
                            new Precipitation(null, null, null, null),
                            new PrecipitationProbability(precipProb, null, null, null, null),
                            new Wind(windSpeed != null ? windSpeed : 0, windDir != null ? windDir.intValue() : 0, null),
                            null, // cloudCover
                            null  // weatherDescription
                    ),
                    null, // sun
                    null, // moon
                    null, // moonPhase
                    new UV(uvIndex != null ? uvIndex : 0, null),
                    null, // airQuality
                    null, // pollen
                    null  // hoursOfSun
            ));
        }

        return dailyList;
    }

    @NonNull
    private static List<Hourly> convertHourlyList(OpenMeteoResult result) {
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
                    new Temperature(temp, feelsLike, null, null),
                    new Precipitation(precip, null, null, null),
                    new PrecipitationProbability(precipProb, null, null, null, null),
                    new Wind(windSpeed != null ? windSpeed : 0, windDir != null ? windDir.intValue() : 0, null),
                    new UV(uvIndex != null ? uvIndex : 0, null),
                    convertWeatherCode(weatherCode),
                    null, // cloudCover
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
        // https://open-meteo.com/en/docs
        switch (code) {
            case 0: // Clear sky
                return WeatherCode.CLEAR;
            case 1: // Mainly clear
            case 2: // Partly cloudy
            case 3: // Overcast
                return WeatherCode.CLOUDY;
            case 45: // Fog
            case 48: // Depositing rime fog
                return WeatherCode.FOG;
            case 51: // Drizzle: Light
            case 53: // Drizzle: Moderate
            case 55: // Drizzle: Dense
                return WeatherCode.RAIN;
            case 56: // Freezing Drizzle: Light
            case 57: // Freezing Drizzle: Dense
                return WeatherCode.SLEET;
            case 61: // Rain: Slight
            case 63: // Rain: Moderate
            case 65: // Rain: Heavy
                return WeatherCode.RAIN;
            case 66: // Freezing Rain: Light
            case 67: // Freezing Rain: Heavy
                return WeatherCode.SLEET;
            case 71: // Snow fall: Slight
            case 73: // Snow fall: Moderate
            case 75: // Snow fall: Heavy
            case 77: // Snow grains
                return WeatherCode.SNOW;
            case 80: // Rain showers: Slight
            case 81: // Rain showers: Moderate
            case 82: // Rain showers: Violent
                return WeatherCode.RAIN;
            case 85: // Snow showers: Slight
            case 86: // Snow showers: Heavy
                return WeatherCode.SNOW;
            case 95: // Thunderstorm: Slight or moderate
                return WeatherCode.THUNDER;
            case 96: // Thunderstorm with slight hail
            case 99: // Thunderstorm with heavy hail
                return WeatherCode.THUNDERSTORM;
            default:
                return WeatherCode.CLEAR;
        }
    }

    @Nullable
    private static <T> T getValueOrNull(List<T> list, int index) {
        if (list == null || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }
}
