package wangdaye.com.geometricweather.weather.json.qweather;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * QWeather hourly result.
 * https://dev.qweather.com/docs/api/weather/weather-hourly-forecast/
 */

public class QWeatherHourlyResult {

    @SerializedName("code")
    public String code;

    @SerializedName("updateTime")
    public String updateTime;

    @SerializedName("hourly")
    public List<Hourly> hourly;

    public static class Hourly {
        @SerializedName("fxTime")
        public String fxTime;

        @SerializedName("temp")
        public String temp;

        @SerializedName("icon")
        public String icon;

        @SerializedName("text")
        public String text;

        @SerializedName("wind360")
        public String wind360;

        @SerializedName("windDir")
        public String windDir;

        @SerializedName("windScale")
        public String windScale;

        @SerializedName("windSpeed")
        public String windSpeed;

        @SerializedName("humidity")
        public String humidity;

        @SerializedName("pop")
        public String pop;

        @SerializedName("precip")
        public String precip;

        @SerializedName("pressure")
        public String pressure;

        @SerializedName("cloud")
        public String cloud;

        @SerializedName("dew")
        public String dew;
    }
}
