package wangdaye.com.geometricweather.weather.json.openmeteo;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Open-Meteo API result model.
 * https://open-meteo.com/en/docs
 */

public class OpenMeteoResult {

    @SerializedName("latitude")
    public double latitude;

    @SerializedName("longitude")
    public double longitude;

    @SerializedName("timezone")
    public String timezone;

    @SerializedName("current")
    public Current current;

    @SerializedName("hourly")
    public Hourly hourly;

    @SerializedName("daily")
    public Daily daily;

    public static class Current {
        @SerializedName("time")
        public String time;

        @SerializedName("temperature_2m")
        public Double temperature;

        @SerializedName("relative_humidity_2m")
        public Double humidity;

        @SerializedName("apparent_temperature")
        public Double apparentTemperature;

        @SerializedName("precipitation")
        public Double precipitation;

        @SerializedName("weather_code")
        public Integer weatherCode;

        @SerializedName("cloud_cover")
        public Double cloudCover;

        @SerializedName("pressure_msl")
        public Double pressure;

        @SerializedName("surface_pressure")
        public Double surfacePressure;

        @SerializedName("wind_speed_10m")
        public Double windSpeed;

        @SerializedName("wind_direction_10m")
        public Double windDirection;

        @SerializedName("wind_gusts_10m")
        public Double windGusts;
    }

    public static class Hourly {
        @SerializedName("time")
        public List<String> time;

        @SerializedName("temperature_2m")
        public List<Double> temperature;

        @SerializedName("relative_humidity_2m")
        public List<Double> humidity;

        @SerializedName("apparent_temperature")
        public List<Double> apparentTemperature;

        @SerializedName("precipitation_probability")
        public List<Double> precipitationProbability;

        @SerializedName("precipitation")
        public List<Double> precipitation;

        @SerializedName("weather_code")
        public List<Integer> weatherCode;

        @SerializedName("cloud_cover")
        public List<Double> cloudCover;

        @SerializedName("pressure_msl")
        public List<Double> pressure;

        @SerializedName("wind_speed_10m")
        public List<Double> windSpeed;

        @SerializedName("wind_direction_10m")
        public List<Double> windDirection;

        @SerializedName("wind_gusts_10m")
        public List<Double> windGusts;

        @SerializedName("uv_index")
        public List<Double> uvIndex;

        @SerializedName("visibility")
        public List<Double> visibility;

        @SerializedName("is_day")
        public List<Integer> isDay;
    }

    public static class Daily {
        @SerializedName("time")
        public List<String> time;

        @SerializedName("weather_code")
        public List<Integer> weatherCode;

        @SerializedName("temperature_2m_max")
        public List<Double> temperatureMax;

        @SerializedName("temperature_2m_min")
        public List<Double> temperatureMin;

        @SerializedName("apparent_temperature_max")
        public List<Double> apparentTemperatureMax;

        @SerializedName("apparent_temperature_min")
        public List<Double> apparentTemperatureMin;

        @SerializedName("sunrise")
        public List<String> sunrise;

        @SerializedName("sunset")
        public List<String> sunset;

        @SerializedName("precipitation_sum")
        public List<Double> precipitationSum;

        @SerializedName("precipitation_probability_max")
        public List<Double> precipitationProbabilityMax;

        @SerializedName("wind_speed_10m_max")
        public List<Double> windSpeedMax;

        @SerializedName("wind_gusts_10m_max")
        public List<Double> windGustsMax;

        @SerializedName("wind_direction_10m_dominant")
        public List<Double> windDirectionDominant;

        @SerializedName("uv_index_max")
        public List<Double> uvIndexMax;

        @SerializedName("sunshine_duration")
        public List<Double> sunshineDuration;
    }
}
