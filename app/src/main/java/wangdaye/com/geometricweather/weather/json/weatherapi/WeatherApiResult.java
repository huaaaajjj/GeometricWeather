package wangdaye.com.geometricweather.weather.json.weatherapi;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * WeatherAPI.com result model.
 * https://www.weatherapi.com/docs/
 */

public class WeatherApiResult {

    @SerializedName("location")
    public Location location;

    @SerializedName("current")
    public Current current;

    @SerializedName("forecast")
    public Forecast forecast;

    @SerializedName("alerts")
    public Alerts alerts;

    public static class Location {
        @SerializedName("name")
        public String name;

        @SerializedName("region")
        public String region;

        @SerializedName("country")
        public String country;

        @SerializedName("lat")
        public Double lat;

        @SerializedName("lon")
        public Double lon;

        @SerializedName("tz_id")
        public String tzId;

        @SerializedName("localtime_epoch")
        public Long localtimeEpoch;

        @SerializedName("localtime")
        public String localtime;
    }

    public static class Current {
        @SerializedName("temp_c")
        public Double tempC;

        @SerializedName("temp_f")
        public Double tempF;

        @SerializedName("is_day")
        public Integer isDay;

        @SerializedName("condition")
        public Condition condition;

        @SerializedName("wind_mph")
        public Double windMph;

        @SerializedName("wind_kph")
        public Double windKph;

        @SerializedName("wind_degree")
        public Integer windDegree;

        @SerializedName("wind_dir")
        public String windDir;

        @SerializedName("pressure_mb")
        public Double pressureMb;

        @SerializedName("pressure_in")
        public Double pressureIn;

        @SerializedName("precip_mm")
        public Double precipMm;

        @SerializedName("precip_in")
        public Double precipIn;

        @SerializedName("humidity")
        public Integer humidity;

        @SerializedName("cloud")
        public Integer cloud;

        @SerializedName("feelslike_c")
        public Double feelslikeC;

        @SerializedName("feelslike_f")
        public Double feelslikeF;

        @SerializedName("vis_km")
        public Double visKm;

        @SerializedName("vis_miles")
        public Double visMiles;

        @SerializedName("uv")
        public Double uv;

        @SerializedName("gust_mph")
        public Double gustMph;

        @SerializedName("gust_kph")
        public Double gustKph;

        @SerializedName("air_quality")
        public AirQuality airQuality;
    }

    public static class Condition {
        @SerializedName("text")
        public String text;

        @SerializedName("icon")
        public String icon;

        @SerializedName("code")
        public Integer code;
    }

    public static class AirQuality {
        @SerializedName("co")
        public Double co;

        @SerializedName("no2")
        public Double no2;

        @SerializedName("o3")
        public Double o3;

        @SerializedName("so2")
        public Double so2;

        @SerializedName("pm2_5")
        public Double pm25;

        @SerializedName("pm10")
        public Double pm10;

        @SerializedName("us-epa-index")
        public Integer usEpaIndex;

        @SerializedName("gb-defra-index")
        public Integer gbDefraIndex;
    }

    public static class Forecast {
        @SerializedName("forecastday")
        public List<ForecastDay> forecastday;
    }

    public static class ForecastDay {
        @SerializedName("date")
        public String date;

        @SerializedName("date_epoch")
        public Long dateEpoch;

        @SerializedName("day")
        public Day day;

        @SerializedName("astro")
        public Astro astro;

        @SerializedName("hour")
        public List<Hour> hour;
    }

    public static class Day {
        @SerializedName("maxtemp_c")
        public Double maxtempC;

        @SerializedName("maxtemp_f")
        public Double maxtempF;

        @SerializedName("mintemp_c")
        public Double mintempC;

        @SerializedName("mintemp_f")
        public Double mintempF;

        @SerializedName("avgtemp_c")
        public Double avgtempC;

        @SerializedName("avgtemp_f")
        public Double avgtempF;

        @SerializedName("maxwind_mph")
        public Double maxwindMph;

        @SerializedName("maxwind_kph")
        public Double maxwindKph;

        @SerializedName("totalprecip_mm")
        public Double totalprecipMm;

        @SerializedName("totalprecip_in")
        public Double totalprecipIn;

        @SerializedName("totalsnow_cm")
        public Double totalsnowCm;

        @SerializedName("avgvis_km")
        public Double avgvisKm;

        @SerializedName("avgvis_miles")
        public Double avgvisMiles;

        @SerializedName("avghumidity")
        public Integer avghumidity;

        @SerializedName("daily_will_it_rain")
        public Integer dailyWillItRain;

        @SerializedName("daily_chance_of_rain")
        public Integer dailyChanceOfRain;

        @SerializedName("daily_will_it_snow")
        public Integer dailyWillItSnow;

        @SerializedName("daily_chance_of_snow")
        public Integer dailyChanceOfSnow;

        @SerializedName("condition")
        public Condition condition;

        @SerializedName("uv")
        public Double uv;

        @SerializedName("air_quality")
        public AirQuality airQuality;
    }

    public static class Astro {
        @SerializedName("sunrise")
        public String sunrise;

        @SerializedName("sunset")
        public String sunset;

        @SerializedName("moonrise")
        public String moonrise;

        @SerializedName("moonset")
        public String moonset;

        @SerializedName("moon_phase")
        public String moonPhase;

        @SerializedName("moon_illumination")
        public String moonIllumination;
    }

    public static class Hour {
        @SerializedName("time_epoch")
        public Long timeEpoch;

        @SerializedName("time")
        public String time;

        @SerializedName("temp_c")
        public Double tempC;

        @SerializedName("temp_f")
        public Double tempF;

        @SerializedName("is_day")
        public Integer isDay;

        @SerializedName("condition")
        public Condition condition;

        @SerializedName("wind_mph")
        public Double windMph;

        @SerializedName("wind_kph")
        public Double windKph;

        @SerializedName("wind_degree")
        public Integer windDegree;

        @SerializedName("wind_dir")
        public String windDir;

        @SerializedName("pressure_mb")
        public Double pressureMb;

        @SerializedName("pressure_in")
        public Double pressureIn;

        @SerializedName("precip_mm")
        public Double precipMm;

        @SerializedName("precip_in")
        public Double precipIn;

        @SerializedName("humidity")
        public Integer humidity;

        @SerializedName("cloud")
        public Integer cloud;

        @SerializedName("feelslike_c")
        public Double feelslikeC;

        @SerializedName("feelslike_f")
        public Double feelslikeF;

        @SerializedName("windchill_c")
        public Double windchillC;

        @SerializedName("windchill_f")
        public Double windchillF;

        @SerializedName("heatindex_c")
        public Double heatindexC;

        @SerializedName("heatindex_f")
        public Double heatindexF;

        @SerializedName("dewpoint_c")
        public Double dewpointC;

        @SerializedName("dewpoint_f")
        public Double dewpointF;

        @SerializedName("will_it_rain")
        public Integer willItRain;

        @SerializedName("chance_of_rain")
        public Integer chanceOfRain;

        @SerializedName("will_it_snow")
        public Integer willItSnow;

        @SerializedName("chance_of_snow")
        public Integer chanceOfSnow;

        @SerializedName("vis_km")
        public Double visKm;

        @SerializedName("vis_miles")
        public Double visMiles;

        @SerializedName("gust_mph")
        public Double gustMph;

        @SerializedName("gust_kph")
        public Double gustKph;

        @SerializedName("uv")
        public Double uv;
    }

    public static class Alerts {
        @SerializedName("alert")
        public List<Alert> alert;
    }

    public static class Alert {
        @SerializedName("headline")
        public String headline;

        @SerializedName("msgtype")
        public String msgtype;

        @SerializedName("severity")
        public String severity;

        @SerializedName("urgency")
        public String urgency;

        @SerializedName("areas")
        public String areas;

        @SerializedName("category")
        public String category;

        @SerializedName("certainty")
        public String certainty;

        @SerializedName("event")
        public String event;

        @SerializedName("note")
        public String note;

        @SerializedName("effective")
        public String effective;

        @SerializedName("expires")
        public String expires;

        @SerializedName("desc")
        public String desc;

        @SerializedName("instruction")
        public String instruction;
    }
}
