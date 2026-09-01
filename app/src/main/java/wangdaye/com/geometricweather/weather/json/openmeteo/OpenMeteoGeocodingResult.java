package wangdaye.com.geometricweather.weather.json.openmeteo;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Open-Meteo geocoding API result model.
 * https://open-meteo.com/en/docs/geocoding-api
 *
 * This is the app's place search, not a weather source's: it answers "where is this name" with a
 * coordinate, and the weather then comes from whichever source the user has selected. Free and
 * key-less, like the forecast host.
 *
 * A query that matches nothing comes back as {@code {"generationtime_ms": ...}} — the results key
 * is absent rather than an empty array, so the field is nullable.
 */

public class OpenMeteoGeocodingResult {

    @SerializedName("results")
    public List<Result> results;

    public static class Result {

        @SerializedName("name")
        public String name;

        @SerializedName("latitude")
        public Double latitude;

        @SerializedName("longitude")
        public Double longitude;

        /** IANA id, e.g. "Asia/Shanghai". The other search paths have to guess at this. */
        @SerializedName("timezone")
        public String timezone;

        @SerializedName("country")
        public String country;

        @SerializedName("country_code")
        public String countryCode;

        /** Administrative divisions, widest first: province / prefecture / district. */
        @SerializedName("admin1")
        public String admin1;

        @SerializedName("admin2")
        public String admin2;

        @SerializedName("admin3")
        public String admin3;
    }
}
