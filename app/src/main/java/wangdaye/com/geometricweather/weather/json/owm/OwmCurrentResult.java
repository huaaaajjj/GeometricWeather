package wangdaye.com.geometricweather.weather.json.owm;

import java.util.List;

public class OwmCurrentResult {
    public long dt;
    public int timezone;
    public MainBean main;
    public List<WeatherBean> weather;
    public WindBean wind;
    public int visibility;
    public SysBean sys;
    public String name;

    public static class MainBean {
        public double temp;
        public double feels_like;
        public int humidity;
        public double pressure;
        public double temp_min;
        public double temp_max;
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

    public static class SysBean {
        public long sunrise;
        public long sunset;
    }
}
