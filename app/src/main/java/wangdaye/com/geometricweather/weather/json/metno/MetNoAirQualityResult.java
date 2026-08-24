package wangdaye.com.geometricweather.weather.json.metno;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * MET Norway airqualityforecast/0.1 result model.
 * https://api.met.no/weatherapi/airqualityforecast/0.1/documentation
 *
 * Note {@code AQI} is Norway's own 1..4+ index, not the 0..500 scale the app's AirQuality model and
 * its colour thresholds are built for — feeding it straight in would paint every location as
 * pristine. The converter therefore keeps only the µg/m³ concentrations and lets
 * CommonConverter.getAirQuality* derive the index the same way the other sources do.
 *
 * MET Norway reports no SO2, so {@code AirQuality.so2} stays null for this source.
 */
public class MetNoAirQualityResult {

    @SerializedName("data")
    public Data data;

    public static class Data {
        @SerializedName("time")
        public List<Time> time;
    }

    public static class Time {
        /** UTC ISO-8601 with an explicit offset ("2026-08-23T12:00:00Z"). */
        @SerializedName("from")
        public String from;

        @SerializedName("to")
        public String to;

        @SerializedName("variables")
        public Variables variables;
    }

    /**
     * The response carries ~37 variables per hour (per-pollutant AQI plus local/non-local source
     * fractions). Only the five below are read; Gson drops the rest.
     */
    public static class Variables {
        @SerializedName("pm25_concentration")
        public Value pm25;

        @SerializedName("pm10_concentration")
        public Value pm10;

        @SerializedName("o3_concentration")
        public Value o3;

        @SerializedName("no2_concentration")
        public Value no2;

        /** Norwegian 1..4+ index, deliberately unused — see the class comment. */
        @SerializedName("AQI")
        public Value aqi;
    }

    public static class Value {
        @SerializedName("value")
        public Double value;

        @SerializedName("units")
        public String units;
    }
}
