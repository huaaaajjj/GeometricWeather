package wangdaye.com.geometricweather.weather.json.metno;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * MET Norway metalerts/2.0 result model — a GeoJSON FeatureCollection.
 * https://api.met.no/weatherapi/metalerts/2.0/documentation
 *
 * The endpoint already filters by the lat/lon it was called with, so there is no client-side
 * geometry test to do and {@code geometry} is skipped entirely.
 */
public class MetNoAlertResult {

    @SerializedName("features")
    public List<Feature> features;

    @SerializedName("lang")
    public String lang;

    public static class Feature {
        @SerializedName("properties")
        public Properties properties;

        @SerializedName("when")
        public When when;
    }

    public static class When {
        /** [start, end], ISO-8601 with offset ("2026-08-05T08:30:00+00:00"). */
        @SerializedName("interval")
        public List<String> interval;
    }

    public static class Properties {
        /** Pre-composed one-liner: event, level, area and the interval. */
        @SerializedName("title")
        public String title;

        @SerializedName("description")
        public String description;

        @SerializedName("instruction")
        public String instruction;

        @SerializedName("consequences")
        public String consequences;

        /** Machine name, e.g. "forestFire". */
        @SerializedName("event")
        public String event;

        /** Human name, e.g. "Forest fire danger". */
        @SerializedName("eventAwarenessName")
        public String eventAwarenessName;

        /** "<n>; <colour>; <word>", e.g. "2; yellow; Moderate" — the colour and rank live here. */
        @SerializedName("awareness_level")
        public String awarenessLevel;

        /** "<n>; <slug>", e.g. "8; forest-fire". */
        @SerializedName("awareness_type")
        public String awarenessType;

        @SerializedName("severity")
        public String severity;

        @SerializedName("certainty")
        public String certainty;

        @SerializedName("area")
        public String area;

        /** CAP identifier, a String — Alert.alertId is a long, so the converter hashes it. */
        @SerializedName("id")
        public String id;
    }
}
