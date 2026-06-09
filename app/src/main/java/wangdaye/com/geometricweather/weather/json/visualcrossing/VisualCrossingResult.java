package wangdaye.com.geometricweather.weather.json.visualcrossing;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Visual Crossing Weather API result model.
 * https://www.visualcrossing.com/resources/documentation/weather-api/timeline-weather-api/
 */

public class VisualCrossingResult {

    @SerializedName("latitude")
    public Double latitude;

    @SerializedName("longitude")
    public Double longitude;

    @SerializedName("resolvedAddress")
    public String resolvedAddress;

    @SerializedName("timezone")
    public String timezone;

    @SerializedName("tzoffset")
    public Double tzoffset;

    @SerializedName("currentConditions")
    public CurrentConditions currentConditions;

    @SerializedName("days")
    public List<Day> days;

    public static class CurrentConditions {
        @SerializedName("datetime")
        public String datetime;

        @SerializedName("temp")
        public Double temp;

        @SerializedName("feelslike")
        public Double feelslike;

        @SerializedName("humidity")
        public Double humidity;

        @SerializedName("dew")
        public Double dew;

        @SerializedName("precip")
        public Double precip;

        @SerializedName("snow")
        public Double snow;

        @SerializedName("snowdepth")
        public Double snowdepth;

        @SerializedName("windgust")
        public Double windgust;

        @SerializedName("windspeed")
        public Double windspeed;

        @SerializedName("winddir")
        public Double winddir;

        @SerializedName("pressure")
        public Double pressure;

        @SerializedName("visibility")
        public Double visibility;

        @SerializedName("cloudcover")
        public Double cloudcover;

        @SerializedName("solarradiation")
        public Double solarradiation;

        @SerializedName("solarenergy")
        public Double solarenergy;

        @SerializedName("uvindex")
        public Double uvindex;

        @SerializedName("conditions")
        public String conditions;

        @SerializedName("icon")
        public String icon;

        @SerializedName("stations")
        public List<String> stations;
    }

    public static class Day {
        @SerializedName("datetime")
        public String datetime;

        @SerializedName("datetimeEpoch")
        public Long datetimeEpoch;

        @SerializedName("tempmax")
        public Double tempmax;

        @SerializedName("tempmin")
        public Double tempmin;

        @SerializedName("temp")
        public Double temp;

        @SerializedName("feelslikemax")
        public Double feelslikemax;

        @SerializedName("feelslikemin")
        public Double feelslikemin;

        @SerializedName("feelslike")
        public Double feelslike;

        @SerializedName("humidity")
        public Double humidity;

        @SerializedName("precip")
        public Double precip;

        @SerializedName("precipprob")
        public Double precipprob;

        @SerializedName("precipcover")
        public Double precipcover;

        @SerializedName("preciptype")
        public List<String> preciptype;

        @SerializedName("snow")
        public Double snow;

        @SerializedName("snowdepth")
        public Double snowdepth;

        @SerializedName("windgust")
        public Double windgust;

        @SerializedName("windspeed")
        public Double windspeed;

        @SerializedName("winddir")
        public Double winddir;

        @SerializedName("pressure")
        public Double pressure;

        @SerializedName("cloudcover")
        public Double cloudcover;

        @SerializedName("visibility")
        public Double visibility;

        @SerializedName("solarradiation")
        public Double solarradiation;

        @SerializedName("solarenergy")
        public Double solarenergy;

        @SerializedName("uvindex")
        public Double uvindex;

        @SerializedName("sunrise")
        public String sunrise;

        @SerializedName("sunriseEpoch")
        public Long sunriseEpoch;

        @SerializedName("sunset")
        public String sunset;

        @SerializedName("sunsetEpoch")
        public Long sunsetEpoch;

        @SerializedName("moonrise")
        public String moonrise;

        @SerializedName("moonriseEpoch")
        public Long moonriseEpoch;

        @SerializedName("moonset")
        public String moonset;

        @SerializedName("moonsetEpoch")
        public Long moonsetEpoch;

        @SerializedName("moonphase")
        public Double moonphase;

        @SerializedName("conditions")
        public String conditions;

        @SerializedName("description")
        public String description;

        @SerializedName("icon")
        public String icon;

        @SerializedName("hours")
        public List<Hour> hours;
    }

    public static class Hour {
        @SerializedName("datetime")
        public String datetime;

        @SerializedName("datetimeEpoch")
        public Long datetimeEpoch;

        @SerializedName("temp")
        public Double temp;

        @SerializedName("feelslike")
        public Double feelslike;

        @SerializedName("humidity")
        public Double humidity;

        @SerializedName("dew")
        public Double dew;

        @SerializedName("precip")
        public Double precip;

        @SerializedName("precipprob")
        public Double precipprob;

        @SerializedName("snow")
        public Double snow;

        @SerializedName("snowdepth")
        public Double snowdepth;

        @SerializedName("windgust")
        public Double windgust;

        @SerializedName("windspeed")
        public Double windspeed;

        @SerializedName("winddir")
        public Double winddir;

        @SerializedName("pressure")
        public Double pressure;

        @SerializedName("visibility")
        public Double visibility;

        @SerializedName("cloudcover")
        public Double cloudcover;

        @SerializedName("solarradiation")
        public Double solarradiation;

        @SerializedName("solarenergy")
        public Double solarenergy;

        @SerializedName("uvindex")
        public Double uvindex;

        @SerializedName("conditions")
        public String conditions;

        @SerializedName("icon")
        public String icon;
    }
}
