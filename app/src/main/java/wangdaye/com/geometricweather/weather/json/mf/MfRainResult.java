package wangdaye.com.geometricweather.weather.json.mf;

import com.google.gson.annotations.SerializedName;

import java.util.Date;
import java.util.List;

/**
 * Mf rain nowcast result (v3/nowcast/rain).
 *
 * GeoJSON feature: the 5-minute steps live under {@code properties.forecast}.
 **/

public class MfRainResult {
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
        public Integer altitude;
        public Integer confidence;
        public String country;
        @SerializedName("french_department")
        public String frenchDepartment;
        public String name;
        @SerializedName("rain_product_available")
        public Integer rainProductAvailable;
        public String timezone;
        @SerializedName("forecast")
        public List<RainForecast> rainForecasts;

        public static class RainForecast {
            public Date time;
            /** 1 = no rain, higher values mean increasing intensity. */
            @SerializedName("rain_intensity")
            public Integer rainIntensity;
            @SerializedName("rain_intensity_description")
            public String rainIntensityDescription;
        }
    }
}
