package wangdaye.com.geometricweather.weather.json.xiaomi;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Xiaomi Weather {@code weather/xm/forecast/minutely} result — 120 one-minute precipitation steps,
 * i.e. the next two hours.
 *
 * Coverage is per-city, not nationwide: a request for a county seat answers {@code {"status": -1}}
 * with no {@code precipitation} block at all (verified 2026-08-24: Beijing yes, 舒城 no). Failure is
 * a valid HTTP 200, so the converter checks for the block rather than the transport.
 */
public class XiaomiMinutelyResult {

    /** 0 when the block is present, -1 when this location has no minutely coverage. */
    @SerializedName("status")
    public Integer status;

    @SerializedName("precipitation")
    public Precipitation precipitation;

    public static class Precipitation {

        /** ISO-8601 with offset; the timestamp of {@code value[0]}. */
        @SerializedName("pubTime")
        public String pubTime;

        /**
         * Ready-made sentence, e.g. "最近的降雨带在东南53公里外呢" — fed to {@code Current.hourlyForecast},
         * which the app renders as the subtitle of the hourly card.
         */
        @SerializedName("description")
        public String description;

        /** Icon number for the period as a whole, not per minute. */
        @SerializedName("weather")
        public String weather;

        /** 1 when precipitation is expected somewhere in the window. */
        @SerializedName("isRainOrSnow")
        public Integer isRainOrSnow;

        /** mm/min, 120 entries. Zero means dry — the app has no intensity field, only a code. */
        @SerializedName("value")
        public List<Double> value;
    }
}
