package wangdaye.com.geometricweather.weather.json.caiyun;

import java.util.List;

public class CaiYunWeatherResult {

    public String status;
    public String api_version;
    public String lang;
    public String unit;
    public int tzshift;
    public String timezone;
    public long server_time;
    public List<Double> location;
    public ResultBean result;

    public static class ResultBean {
        public RealtimeBean realtime;
        public DailyBean daily;
        public HourlyBean hourly;
        public String forecast_keypoint;
    }

    public static class RealtimeBean {
        public String status;
        public double temperature;
        public double humidity;
        public double cloudrate;
        public String skycon;
        public double visibility;
        public double dswrf;
        public WindBean wind;
        public double pressure;
        public double apparent_temperature;
        public PrecipitationBean precipitation;
        public AirQualityBean air_quality;
        public LifeIndexBean life_index;
    }

    public static class WindBean {
        public double speed;
        public double direction;
    }

    public static class PrecipitationBean {
        public LocalBean local;
        public NearestBean nearest;
    }

    public static class LocalBean {
        public String status;
        public String datasource;
        public double intensity;
    }

    public static class NearestBean {
        public String status;
        public double distance;
        public double intensity;
    }

    public static class AirQualityBean {
        public int pm25;
        public int pm10;
        public int o3;
        public int so2;
        public int no2;
        public double co;
        public AqiBean aqi;
        public DescriptionBean description;
    }

    public static class AqiBean {
        public int chn;
        public int usa;
    }

    public static class DescriptionBean {
        public String chn;
        public String usa;
    }

    public static class LifeIndexBean {
        public UltravioletBean ultraviolet;
        public ComfortBean comfort;
    }

    public static class UltravioletBean {
        public int index;
        public String desc;
    }

    public static class ComfortBean {
        public int index;
        public String desc;
    }

    public static class DailyBean {
        public String status;
        public List<AstroBean> astro;
        public List<PrecipitationDailyBean> precipitation_08h_20h;
        public List<PrecipitationDailyBean> precipitation_20h_32h;
        public List<PrecipitationDailyBean> precipitation;
        public List<TemperatureDailyBean> temperature;
        public List<TemperatureDailyBean> temperature_08h_20h;
        public List<TemperatureDailyBean> temperature_20h_32h;
        public List<WindDailyBean> wind;
        public List<WindDailyBean> wind_08h_20h;
        public List<WindDailyBean> wind_20h_32h;
        public List<HumidityDailyBean> humidity;
        public List<CloudrateDailyBean> cloudrate;
        public List<PressureDailyBean> pressure;
        public List<VisibilityDailyBean> visibility;
        public List<DswrfDailyBean> dswrf;
        public List<SkyconBean> skycon;
        public List<SkyconBean> skycon_08h_20h;
        public List<SkyconBean> skycon_20h_32h;
        public List<AirQualityDailyBean> air_quality;
        public LifeIndexDailyBean life_index;
    }

    public static class AstroBean {
        public String date;
        public SunTimeBean sunrise;
        public SunTimeBean sunset;
    }

    public static class SunTimeBean {
        public String time;
    }

    public static class PrecipitationDailyBean {
        public String date;
        public Double max;
        public Double min;
        public Double avg;
        public Integer probability;
    }

    public static class TemperatureDailyBean {
        public String date;
        public Double max;
        public Double min;
    }

    public static class WindDailyBean {
        public String date;
        public WindValueBean max;
        public WindValueBean min;
        public WindValueBean avg;
    }

    public static class WindValueBean {
        public double speed;
        public double direction;
    }

    public static class HumidityDailyBean {
        public String date;
        public Double max;
        public Double min;
        public Double avg;
    }

    public static class CloudrateDailyBean {
        public String date;
        public Double max;
        public Double min;
        public Double avg;
    }

    public static class PressureDailyBean {
        public String date;
        public Double max;
        public Double min;
        public Double avg;
    }

    public static class VisibilityDailyBean {
        public String date;
        public Double max;
        public Double min;
        public Double avg;
    }

    public static class DswrfDailyBean {
        public String date;
        public Double max;
        public Double min;
        public Double avg;
    }

    public static class SkyconBean {
        public String date;
        public String value;
    }

    public static class AirQualityDailyBean {
        public List<AqiDailyBean> aqi;
        public List<Pm25DailyBean> pm25;
    }

    public static class AqiDailyBean {
        public String date;
        public AqiBean max;
        public AqiBean avg;
        public AqiBean min;
    }

    public static class Pm25DailyBean {
        public String date;
        public Integer max;
        public Integer avg;
        public Integer min;
    }

    public static class LifeIndexDailyBean {
        public Object ultraviolet;
        public Object comfort;
    }

    public static class HourlyBean {
        public String status;
        public String description;
        public List<PrecipitationHourlyBean> precipitation;
        public List<TemperatureHourlyBean> temperature;
        public List<ApparentTemperatureHourlyBean> apparent_temperature;
        public List<WindHourlyBean> wind;
        public List<HumidityHourlyBean> humidity;
        public List<CloudrateHourlyBean> cloudrate;
        public List<SkyconHourlyBean> skycon;
        public List<PressureHourlyBean> pressure;
        public List<VisibilityHourlyBean> visibility;
        public List<DswrfHourlyBean> dswrf;
        public List<AirQualityHourlyBean> air_quality;
    }

    public static class TemperatureHourlyBean {
        public String datetime;
        public double value;
    }

    public static class ApparentTemperatureHourlyBean {
        public String datetime;
        public double value;
    }

    public static class PrecipitationHourlyBean {
        public String datetime;
        public double value;
    }

    public static class WindHourlyBean {
        public String datetime;
        public double speed;
        public double direction;
    }

    public static class HumidityHourlyBean {
        public String datetime;
        public double value;
    }

    public static class CloudrateHourlyBean {
        public String datetime;
        public double value;
    }

    public static class SkyconHourlyBean {
        public String datetime;
        public String value;
    }

    public static class PressureHourlyBean {
        public String datetime;
        public double value;
    }

    public static class VisibilityHourlyBean {
        public String datetime;
        public double value;
    }

    public static class DswrfHourlyBean {
        public String datetime;
        public double value;
    }

    public static class AirQualityHourlyBean {
        public String datetime;
        public int pm25;
        public int pm10;
        public int o3;
        public int so2;
        public int no2;
        public double co;
        public AqiBean aqi;
    }
}
