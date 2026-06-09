package wangdaye.com.geometricweather.weather.json.qweather;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * QWeather warning result.
 * https://dev.qweather.com/docs/api/warning/weather-warning/
 */

public class QWeatherWarningResult {

    @SerializedName("code")
    public String code;

    @SerializedName("updateTime")
    public String updateTime;

    @SerializedName("warning")
    public List<Warning> warning;

    public static class Warning {
        @SerializedName("id")
        public String id;

        @SerializedName("sender")
        public String sender;

        @SerializedName("title")
        public String title;

        @SerializedName("startTime")
        public String startTime;

        @SerializedName("endTime")
        public String endTime;

        @SerializedName("status")
        public String status;

        @SerializedName("level")
        public String level;

        @SerializedName("type")
        public String type;

        @SerializedName("typeName")
        public String typeName;

        @SerializedName("text")
        public String text;
    }
}
