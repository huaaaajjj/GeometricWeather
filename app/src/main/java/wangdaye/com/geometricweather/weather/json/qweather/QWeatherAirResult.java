package wangdaye.com.geometricweather.weather.json.qweather;

import com.google.gson.annotations.SerializedName;

/**
 * QWeather air quality result.
 * https://dev.qweather.com/docs/api/air/air-now/
 */

public class QWeatherAirResult {

    @SerializedName("code")
    public String code;

    @SerializedName("updateTime")
    public String updateTime;

    @SerializedName("now")
    public AirNow now;

    public static class AirNow {
        @SerializedName("pubTime")
        public String pubTime;

        @SerializedName("aqi")
        public String aqi;

        @SerializedName("level")
        public String level;

        @SerializedName("category")
        public String category;

        @SerializedName("primary")
        public String primary;

        @SerializedName("pm10")
        public String pm10;

        @SerializedName("pm2p5")
        public String pm2p5;

        @SerializedName("no2")
        public String no2;

        @SerializedName("so2")
        public String so2;

        @SerializedName("co")
        public String co;

        @SerializedName("o3")
        public String o3;
    }
}
