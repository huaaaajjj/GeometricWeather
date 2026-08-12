package wangdaye.com.geometricweather.weather.json.owm;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class OwmForecastResult {
    public int cnt;
    public List<ListBean> list;

    public static class ListBean {
        public long dt;
        public MainBean main;
        public List<WeatherBean> weather;
        public WindBean wind;
        public int visibility;
        public double pop;
        public CloudsBean clouds;
        public RainBean rain;
        public SysBean sys;
    }

    public static class SysBean {
        // "d" or "n" — the provider's own day/night flag for this step.
        public String pod;
    }

    public static class MainBean {
        public double temp;
        public double feels_like;
        public double temp_min;
        public double temp_max;
        public int humidity;
        public double pressure;
    }

    public static class WeatherBean {
        public int id;
        public String main;
        public String description;
        public String icon;
    }

    public static class WindBean {
        public double speed;
        public int deg;
    }

    public static class CloudsBean {
        public int all;
    }

    public static class RainBean {
        // The JSON key is "3h", which is not a legal Java identifier — without this annotation
        // Gson looked for a field literally named "_3h" and every entry reported 0 mm.
        @SerializedName("3h")
        public double _3h;
    }
}
