package wangdaye.com.geometricweather.weather.json.owm;

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
        public double _3h;
    }
}
