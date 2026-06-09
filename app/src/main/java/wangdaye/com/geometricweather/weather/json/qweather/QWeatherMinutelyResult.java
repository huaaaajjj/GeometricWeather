package wangdaye.com.geometricweather.weather.json.qweather;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * QWeather minutely result.
 * https://dev.qweather.com/docs/api/weather/weather-minutely/
 */

public class QWeatherMinutelyResult {

    @SerializedName("code")
    public String code;

    @SerializedName("updateTime")
    public String updateTime;

    @SerializedName("minutely")
    public List<Minutely> minutely;

    @SerializedName("summary")
    public String summary;

    public static class Minutely {
        @SerializedName("fxTime")
        public String fxTime;

        @SerializedName("precip")
        public String precip;
    }
}
