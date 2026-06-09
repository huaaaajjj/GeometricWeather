package wangdaye.com.geometricweather.weather.json.qweather;

import com.google.gson.annotations.SerializedName;

/**
 * QWeather now result.
 * https://dev.qweather.com/docs/api/weather/weather-now/
 */

public class QWeatherNowResult {

    @SerializedName("code")
    public String code;

    @SerializedName("updateTime")
    public String updateTime;

    @SerializedName("now")
    public Now now;

    public static class Now {
        @SerializedName("obsTime")
        public String obsTime;

        @SerializedName("temp")
        public String temp;

        @SerializedName("feelsLike")
        public String feelsLike;

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

        @SerializedName("precip")
        public String precip;

        @SerializedName("pressure")
        public String pressure;

        @SerializedName("vis")
        public String vis;

        @SerializedName("cloud")
        public String cloud;

        @SerializedName("dew")
        public String dew;
    }
}
