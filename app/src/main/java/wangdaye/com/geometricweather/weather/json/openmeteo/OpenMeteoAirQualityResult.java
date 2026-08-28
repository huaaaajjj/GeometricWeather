package wangdaye.com.geometricweather.weather.json.openmeteo;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Open-Meteo air quality API result model.
 * https://open-meteo.com/en/docs/air-quality-api
 *
 * Only fields the converter consumes are declared. The pm/ozone columns exist hourly too but are
 * not read anywhere, so they are not requested; mugwort has no slot in the Pollen model.
 */

public class OpenMeteoAirQualityResult {

    @SerializedName("current")
    public Current current;

    @SerializedName("hourly")
    public Hourly hourly;

    public static class Current {
        @SerializedName("time")
        public String time;

        @SerializedName("pm2_5")
        public Double pm25;

        @SerializedName("pm10")
        public Double pm10;

        @SerializedName("carbon_monoxide")
        public Double carbonMonoxide;

        @SerializedName("nitrogen_dioxide")
        public Double nitrogenDioxide;

        @SerializedName("sulphur_dioxide")
        public Double sulphurDioxide;

        @SerializedName("ozone")
        public Double ozone;
    }

    public static class Hourly {
        @SerializedName("time")
        public List<String> time;

        @SerializedName("alder_pollen")
        public List<Double> alderPollen;

        @SerializedName("birch_pollen")
        public List<Double> birchPollen;

        @SerializedName("grass_pollen")
        public List<Double> grassPollen;

        @SerializedName("olive_pollen")
        public List<Double> olivePollen;

        @SerializedName("ragweed_pollen")
        public List<Double> ragweedPollen;
    }
}
