package wangdaye.com.geometricweather.weather.json.cma;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * China Meteorological Administration (weather.cma.cn) weather response.
 *
 * Endpoint: GET api/weather/view?stationid={id}
 * (stationid empty -> nearest station by IP).
 *
 * Fields mirror the public JSON documented at https://www.mrxiaom.top/post/weather-api/ .
 * All numeric fields are boxed so missing values stay null instead of crashing the converter.
 */
public class CmaWeatherResult {

    @SerializedName("msg")
    public String msg;
    @SerializedName("code")
    public Integer code;
    @SerializedName("data")
    public Data data;

    public static class Data {
        @SerializedName("location")
        public Location location;
        @SerializedName("daily")
        public List<Daily> daily;
        @SerializedName("now")
        public Now now;
        @SerializedName("jieQi")
        public String jieQi;
        @SerializedName("alarm")
        public List<Alarm> alarm;
        @SerializedName("lastUpdate")
        public String lastUpdate;
    }

    public static class Location {
        @SerializedName("id")
        public String id;
        @SerializedName("name")
        public String name;
        @SerializedName("path")
        public String path;
        @SerializedName("longitude")
        public Double longitude;
        @SerializedName("latitude")
        public Double latitude;
        @SerializedName("timezone")
        public Integer timezone;
    }

    public static class Daily {
        @SerializedName("date")
        public String date;
        @SerializedName("high")
        public Double high;
        @SerializedName("low")
        public Double low;
        @SerializedName("dayText")
        public String dayText;
        @SerializedName("dayCode")
        public Integer dayCode;
        @SerializedName("dayWindDirection")
        public String dayWindDirection;
        @SerializedName("dayWindScale")
        public String dayWindScale;
        @SerializedName("nightText")
        public String nightText;
        @SerializedName("nightCode")
        public Integer nightCode;
        @SerializedName("nightWindDirection")
        public String nightWindDirection;
        @SerializedName("nightWindScale")
        public String nightWindScale;
    }

    public static class Now {
        @SerializedName("precipitation")
        public Double precipitation;
        @SerializedName("temperature")
        public Double temperature;
        @SerializedName("pressure")
        public Double pressure;
        @SerializedName("humidity")
        public Double humidity;
        @SerializedName("windDirection")
        public String windDirection;
        @SerializedName("windDirectionDegree")
        public Double windDirectionDegree;
        @SerializedName("windSpeed")
        public Double windSpeed;
        @SerializedName("windScale")
        public String windScale;
        @SerializedName("feelst")
        public Double feelst;
    }

    public static class Alarm {
        @SerializedName("id")
        public String id;
        @SerializedName("headline")
        public String headline;
        @SerializedName("title")
        public String title;
        @SerializedName("description")
        public String description;
        @SerializedName("effective")
        public String effective;
        @SerializedName("type")
        public String type;
    }
}
