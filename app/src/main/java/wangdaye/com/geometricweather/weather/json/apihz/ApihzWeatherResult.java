package wangdaye.com.geometricweather.weather.json.apihz;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * apihz.cn tqybip.php (中国天气网) weather response.
 *
 * Docs: https://www.apihz.cn/api/tqtqybip.html
 *
 * Day 1 is flat on the root ({@code weather1/weather2/wd1/wd2/...}); days 2-7 are nested objects
 * {@code weatherday2}..{@code weatherday7}. Hourly periods are {@code hour1}..{@code hour7}, each an
 * array of objects keyed by Chinese field names. Numbers are boxed so missing values stay null.
 *
 * NOTE: {@code wd1}/{@code wd2} arrive as strings on the root ("33") but as numbers in the nested
 * day objects (29); typed as String here because Gson coerces a JSON number to its string form.
 */
public class ApihzWeatherResult {

    @SerializedName("code")
    public Integer code;
    @SerializedName("msg")
    public String msg;

    @SerializedName("guo")
    public String guo;
    @SerializedName("sheng")
    public String sheng;
    @SerializedName("shi")
    public String shi;
    @SerializedName("name")
    public String name;
    @SerializedName("lon")
    public String lon;
    @SerializedName("lat")
    public String lat;
    @SerializedName("uptime")
    public String uptime;

    // Day 1 (today), flat on the root.
    @SerializedName("weather1")
    public String weather1;
    @SerializedName("weather2")
    public String weather2;
    @SerializedName("wd1")
    public String wd1;
    @SerializedName("wd2")
    public String wd2;
    @SerializedName("winddirection1")
    public String winddirection1;
    @SerializedName("winddirection2")
    public String winddirection2;
    @SerializedName("windleve1")
    public String windleve1;
    @SerializedName("windleve2")
    public String windleve2;

    @SerializedName("nowinfo")
    public NowInfo nowinfo;
    @SerializedName("alarm")
    public List<Alarm> alarm;

    @SerializedName("weatherday2")
    public DayForecast weatherday2;
    @SerializedName("weatherday3")
    public DayForecast weatherday3;
    @SerializedName("weatherday4")
    public DayForecast weatherday4;
    @SerializedName("weatherday5")
    public DayForecast weatherday5;
    @SerializedName("weatherday6")
    public DayForecast weatherday6;
    @SerializedName("weatherday7")
    public DayForecast weatherday7;

    @SerializedName("hour1")
    public List<Hour> hour1;
    @SerializedName("hour2")
    public List<Hour> hour2;
    @SerializedName("hour3")
    public List<Hour> hour3;
    @SerializedName("hour4")
    public List<Hour> hour4;
    @SerializedName("hour5")
    public List<Hour> hour5;
    @SerializedName("hour6")
    public List<Hour> hour6;
    @SerializedName("hour7")
    public List<Hour> hour7;

    @SerializedName("suntimes")
    public List<SunTime> suntimes;

    public static class NowInfo {
        @SerializedName("precipitation")
        public Double precipitation;
        @SerializedName("temperature")
        public Double temperature;
        @SerializedName("pressure")
        public Double pressure;
        @SerializedName("humidity")
        public Double humidity;
        @SerializedName("windDirection")
        public String windDirection;
        @SerializedName("windDirectionDegree")
        public Double windDirectionDegree;
        @SerializedName("windSpeed")
        public Double windSpeed;
        @SerializedName("windScale")
        public String windScale;
        @SerializedName("feelst")
        public Double feelst;
        @SerializedName("uptime")
        public String uptime;
    }

    public static class Alarm {
        @SerializedName("id")
        public String id;
        @SerializedName("title")
        public String title;
        @SerializedName("signaltype")
        public String signaltype;
        @SerializedName("signallevel")
        public String signallevel;
        @SerializedName("effective")
        public String effective;
        @SerializedName("eventType")
        public String eventType;
        @SerializedName("severity")
        public String severity;
        @SerializedName("type")
        public String type;
    }

    // Days 2-7. Same fields as the flat day-1 block, plus an explicit date.
    public static class DayForecast {
        @SerializedName("date")
        public String date;
        @SerializedName("weather1")
        public String weather1;
        @SerializedName("weather2")
        public String weather2;
        @SerializedName("wd1")
        public String wd1;
        @SerializedName("wd2")
        public String wd2;
        @SerializedName("winddirection1")
        public String winddirection1;
        @SerializedName("winddirection2")
        public String winddirection2;
        @SerializedName("windleve1")
        public String windleve1;
        @SerializedName("windleve2")
        public String windleve2;
    }

    // 3-hourly period. Field names are Chinese in the JSON.
    public static class Hour {
        @SerializedName("时间")
        public String time;
        @SerializedName("天气")
        public String weather;
        @SerializedName("气温")
        public String temperature;
        @SerializedName("降水")
        public String precipitation;
        @SerializedName("风速")
        public String windSpeed;
        @SerializedName("风向")
        public String windDirection;
        @SerializedName("气压")
        public String pressure;
        @SerializedName("湿度")
        public String humidity;
        @SerializedName("云量")
        public String cloudCover;
    }

    public static class SunTime {
        @SerializedName("date")
        public String date;
        @SerializedName("sunrise")
        public String sunrise;
        @SerializedName("sunset")
        public String sunset;
    }
}
