package wangdaye.com.geometricweather.weather.json.mf;

import com.google.gson.annotations.SerializedName;

import java.util.Date;
import java.util.List;

/**
 * Mf current result (v2/observation).
 *
 * GeoJSON feature: the observation lives under {@code properties.gridded}.
 **/

public class MfCurrentResult {
    public Geometry geometry;
    public Properties properties;
    public String type;
    @SerializedName("update_time")
    public Date updateTime;

    public static class Geometry {
        public List<Float> coordinates;
        public String type;
    }

    public static class Properties {
        public String timezone;
        public Observation gridded;

        public static class Observation {
            public Date time;
            @SerializedName("T")
            public Float temperature;
            @SerializedName("wind_speed")
            public Float windSpeed;
            @SerializedName("wind_speed_gust")
            public Float windSpeedGust;
            /** Degrees, or -1 when the wind direction is variable. */
            @SerializedName("wind_direction")
            public Integer windDirection;
            @SerializedName("wind_icon")
            public String windIcon;
            @SerializedName("weather_icon")
            public String weatherIcon;
            @SerializedName("weather_description")
            public String weatherDescription;
        }
    }
}
