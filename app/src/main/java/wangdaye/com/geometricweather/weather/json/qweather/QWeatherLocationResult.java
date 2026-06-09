package wangdaye.com.geometricweather.weather.json.qweather;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * QWeather location result.
 * https://dev.qweather.com/docs/api/geoapi/city-lookup/
 */

public class QWeatherLocationResult {

    @SerializedName("code")
    public String code;

    @SerializedName("location")
    public List<Location> location;

    public static class Location {
        @SerializedName("name")
        public String name;

        @SerializedName("id")
        public String id;

        @SerializedName("lat")
        public String lat;

        @SerializedName("lon")
        public String lon;

        @SerializedName("adm2")
        public String adm2;

        @SerializedName("adm1")
        public String adm1;

        @SerializedName("country")
        public String country;

        @SerializedName("tz")
        public String tz;

        @SerializedName("utcOffset")
        public String utcOffset;

        @SerializedName("isDst")
        public String isDst;

        @SerializedName("type")
        public String type;

        @SerializedName("rank")
        public String rank;

        @SerializedName("fxLink")
        public String fxLink;
    }
}
