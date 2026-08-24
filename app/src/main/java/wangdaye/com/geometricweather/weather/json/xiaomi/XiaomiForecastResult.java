package wangdaye.com.geometricweather.weather.json.xiaomi;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Xiaomi Weather {@code weather/all} result — current, daily, hourly, air quality and alerts in one
 * response.
 *
 * Three habits of this API shape the model:
 *
 * 1. **Almost every number is a String**, and an unavailable one is {@code ""} rather than absent
 *    (see {@code current.visibility}), so the converter parses leniently instead of trusting types.
 *    The two exceptions are the hourly arrays and the daily AQI array, which really are numbers.
 * 2. **The forecast arrays carry no per-entry timestamp.** Daily runs one entry per day from
 *    {@code forecastDaily.sunRiseSet.value[i].from}'s date (fall back to {@code current.pubTime}),
 *    hourly one per hour from {@code forecastHourly.temperature.pubTime}. The hourly wind entries do
 *    carry a {@code datetime}, which is what confirms that anchor.
 * 3. **Blocks that have no data still exist**, as {@code {"status": -2}} with no {@code value} — that
 *    is what air quality looks like outside China. Every list has to be treated as possibly null.
 *
 * Response sizes measured 2026-08-24: China 15 days / 23 hours / AQI / alerts; outside China (an
 * {@code accu:} locationKey with {@code isGlobal=true}) 5 days / 23 hours / no AQI.
 */
public class XiaomiForecastResult {

    @SerializedName("current")
    public Current current;

    @SerializedName("forecastDaily")
    public ForecastDaily forecastDaily;

    @SerializedName("forecastHourly")
    public ForecastHourly forecastHourly;

    /** Current air quality. China only; abroad this is present but empty. */
    @SerializedName("aqi")
    public Aqi aqi;

    @SerializedName("alerts")
    public List<Alert> alerts;

    public static class Current {

        /** ISO-8601 with an explicit offset, e.g. "2026-08-24T08:57:24+08:00". */
        @SerializedName("pubTime")
        public String pubTime;

        /** Icon number as a String, e.g. "2". See XiaomiResultConverter for the mapping. */
        @SerializedName("weather")
        public String weather;

        /** celsius */
        @SerializedName("temperature")
        public UnitValue temperature;

        /** celsius */
        @SerializedName("feelsLike")
        public UnitValue feelsLike;

        /** % */
        @SerializedName("humidity")
        public UnitValue humidity;

        /** hPa */
        @SerializedName("pressure")
        public UnitValue pressure;

        /** km — routinely {@code ""} even inside China. */
        @SerializedName("visibility")
        public UnitValue visibility;

        /** Bare String, not a UnitValue. */
        @SerializedName("uvIndex")
        public String uvIndex;

        @SerializedName("wind")
        public Wind wind;
    }

    public static class UnitValue {
        @SerializedName("unit")
        public String unit;

        @SerializedName("value")
        public String value;
    }

    public static class Wind {
        /** degrees */
        @SerializedName("direction")
        public UnitValue direction;

        /** km/h already — no conversion needed, unlike most sources. */
        @SerializedName("speed")
        public UnitValue speed;
    }

    /**
     * Every daily block is an array parallel to the others, so index {@code i} means day {@code i}
     * throughout — except {@code precipitationProbability}, which is routinely shorter (5 entries
     * against 15) and simply runs out.
     */
    public static class ForecastDaily {

        @SerializedName("pubTime")
        public String pubTime;

        /** {@code from} = day high, {@code to} = night low. */
        @SerializedName("temperature")
        public FromToList temperature;

        /** {@code from} = day icon, {@code to} = night icon. */
        @SerializedName("weather")
        public FromToList weather;

        @SerializedName("wind")
        public DailyWind wind;

        /** % as Strings, one per day, shorter than the other arrays. */
        @SerializedName("precipitationProbability")
        public StringList precipitationProbability;

        /** {@code from} = sunrise, {@code to} = sunset, both full ISO-8601 timestamps. */
        @SerializedName("sunRiseSet")
        public FromToList sunRiseSet;

        /** Chinese AQI (0-500), one per day, already an index rather than a band number. */
        @SerializedName("aqi")
        public IntList aqi;
    }

    public static class DailyWind {
        /** degrees, {@code from}/{@code to} per half-day. */
        @SerializedName("direction")
        public FromToList direction;

        /** km/h, {@code from}/{@code to} per half-day. */
        @SerializedName("speed")
        public FromToList speed;
    }

    public static class FromTo {
        @SerializedName("from")
        public String from;

        @SerializedName("to")
        public String to;
    }

    public static class FromToList {
        @SerializedName("status")
        public Integer status;

        @SerializedName("unit")
        public String unit;

        @SerializedName("value")
        public List<FromTo> value;
    }

    public static class StringList {
        @SerializedName("status")
        public Integer status;

        @SerializedName("value")
        public List<String> value;
    }

    public static class IntList {
        @SerializedName("pubTime")
        public String pubTime;

        @SerializedName("status")
        public Integer status;

        @SerializedName("value")
        public List<Integer> value;
    }

    /**
     * 23 hourly entries, and unlike the daily block these values are real JSON numbers. The first
     * entry is the top of the hour at or after publication, so "now" is not in here.
     */
    public static class ForecastHourly {

        /** celsius; its {@code pubTime} is the timestamp of value[0]. */
        @SerializedName("temperature")
        public IntList temperature;

        /** Icon numbers. */
        @SerializedName("weather")
        public IntList weather;

        @SerializedName("wind")
        public HourlyWindList wind;

        /** Present but empty outside China; the app has nowhere to put per-hour AQI anyway. */
        @SerializedName("aqi")
        public IntList aqi;
    }

    public static class HourlyWindList {
        @SerializedName("status")
        public Integer status;

        @SerializedName("value")
        public List<HourlyWind> value;
    }

    public static class HourlyWind {
        /**
         * The only per-entry timestamp anywhere in the forecast arrays. Note the format differs
         * between backends — "2026-08-24T09:00:00.000+08:00" in China against
         * "2026-08-24T03:00:00+02:00" abroad — so it is used to corroborate the hourly anchor
         * rather than to build it.
         */
        @SerializedName("datetime")
        public String datetime;

        /** degrees */
        @SerializedName("direction")
        public String direction;

        /** km/h */
        @SerializedName("speed")
        public String speed;
    }

    /**
     * Current air quality, sourced from 中国环境监测总站. {@code aqi} is the Chinese 0-500 index — a real
     * index, not a band number — and the concentrations are µg/m³ except {@code co}, which is mg/m³
     * (matching what {@code AirQuality.getCOColor} thresholds expect).
     *
     * Outside China the whole object arrives as {@code {"status": -2}}.
     */
    public static class Aqi {
        @SerializedName("status")
        public Integer status;

        @SerializedName("aqi")
        public String aqi;

        @SerializedName("pm25")
        public String pm25;

        @SerializedName("pm10")
        public String pm10;

        @SerializedName("so2")
        public String so2;

        @SerializedName("no2")
        public String no2;

        @SerializedName("o3")
        public String o3;

        /** mg/m³ */
        @SerializedName("co")
        public String co;
    }

    public static class Alert {

        /** e.g. "weathercn:101011600-1787455140000-暴雨蓝色" — a String, so the converter hashes it. */
        @SerializedName("alertId")
        public String alertId;

        @SerializedName("pubTime")
        public String pubTime;

        /** e.g. "东城发布暴雨蓝色预警". */
        @SerializedName("title")
        public String title;

        /** e.g. "暴雨". */
        @SerializedName("type")
        public String type;

        /** Chinese warning colour: 蓝色 / 黄色 / 橙色 / 红色, ascending. */
        @SerializedName("level")
        public String level;

        @SerializedName("detail")
        public String detail;
    }
}
