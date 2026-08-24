package wangdaye.com.geometricweather.weather.json.metno;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * MET Norway locationforecast/2.0 and nowcast/2.0 result model.
 * https://api.met.no/weatherapi/locationforecast/2.0/documentation
 *
 * The two endpoints share this tree, so one model serves both rather than duplicating it: nowcast
 * only adds {@code meta.radar_coverage} and {@code instant.details.precipitation_rate} and carries
 * just {@code next_1_hours}, and the fields it does not send simply stay null.
 *
 * Times are UTC ISO-8601 with a literal Z ("2026-08-23T15:00:00Z"), which the shared Gson's
 * "yyyy-MM-dd'T'HH:mm:ss" adapter would read as device-local — so they are kept as String here and
 * parsed explicitly in the converter, the way MfWeatherApi solves the same problem with its own Gson.
 */
public class MetNoForecastResult {

    @SerializedName("properties")
    public Properties properties;

    public static class Properties {
        @SerializedName("meta")
        public Meta meta;

        @SerializedName("timeseries")
        public List<Timeseries> timeseries;
    }

    public static class Meta {
        @SerializedName("updated_at")
        public String updatedAt;

        /**
         * Nowcast only, "ok" when radar reaches this point. This — not a hardcoded lat/lon box — is
         * how the Nordic-only limit is detected; null on a locationforecast response.
         */
        @SerializedName("radar_coverage")
        public String radarCoverage;
    }

    public static class Timeseries {
        @SerializedName("time")
        public String time;

        @SerializedName("data")
        public Data data;
    }

    public static class Data {
        @SerializedName("instant")
        public Block instant;

        @SerializedName("next_1_hours")
        public Block next1Hours;

        @SerializedName("next_6_hours")
        public Block next6Hours;

        @SerializedName("next_12_hours")
        public Block next12Hours;
    }

    public static class Block {
        @SerializedName("summary")
        public Summary summary;

        @SerializedName("details")
        public Details details;
    }

    public static class Summary {
        /** "<base>[_day|_night|_polartwilight]", e.g. "heavyrainshowersandthunder_day". */
        @SerializedName("symbol_code")
        public String symbolCode;
    }

    public static class Details {

        // ---- instant ----

        /** celsius */
        @SerializedName("air_temperature")
        public Double airTemperature;

        /** hPa */
        @SerializedName("air_pressure_at_sea_level")
        public Double airPressureAtSeaLevel;

        /** % */
        @SerializedName("relative_humidity")
        public Double relativeHumidity;

        /** celsius */
        @SerializedName("dew_point_temperature")
        public Double dewPointTemperature;

        /** % */
        @SerializedName("cloud_area_fraction")
        public Double cloudAreaFraction;

        /** % */
        @SerializedName("fog_area_fraction")
        public Double fogAreaFraction;

        @SerializedName("ultraviolet_index_clear_sky")
        public Double ultravioletIndexClearSky;

        /** degrees */
        @SerializedName("wind_from_direction")
        public Double windFromDirection;

        /** m/s — the app stores km/h (see Wind.WIND_SPEED_*), so the converter scales by 3.6. */
        @SerializedName("wind_speed")
        public Double windSpeed;

        /** m/s */
        @SerializedName("wind_speed_of_gust")
        public Double windSpeedOfGust;

        /** mm/h, nowcast only. */
        @SerializedName("precipitation_rate")
        public Double precipitationRate;

        // ---- next_1_hours / next_6_hours / next_12_hours ----

        /** mm */
        @SerializedName("precipitation_amount")
        public Double precipitationAmount;

        /** % */
        @SerializedName("probability_of_precipitation")
        public Double probabilityOfPrecipitation;

        /** %, next_1_hours only. */
        @SerializedName("probability_of_thunder")
        public Double probabilityOfThunder;

        /** celsius, next_6_hours only. */
        @SerializedName("air_temperature_max")
        public Double airTemperatureMax;

        /** celsius, next_6_hours only. */
        @SerializedName("air_temperature_min")
        public Double airTemperatureMin;
    }
}
