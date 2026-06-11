package wangdaye.com.geometricweather.weather.converters;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.weather.AirQuality;
import wangdaye.com.geometricweather.common.basic.models.weather.Alert;
import wangdaye.com.geometricweather.common.basic.models.weather.Astro;
import wangdaye.com.geometricweather.common.basic.models.weather.Base;
import wangdaye.com.geometricweather.common.basic.models.weather.Current;
import wangdaye.com.geometricweather.common.basic.models.weather.Daily;
import wangdaye.com.geometricweather.common.basic.models.weather.HalfDay;
import wangdaye.com.geometricweather.common.basic.models.weather.History;
import wangdaye.com.geometricweather.common.basic.models.weather.Hourly;
import wangdaye.com.geometricweather.common.basic.models.weather.Minutely;
import wangdaye.com.geometricweather.common.basic.models.weather.MoonPhase;
import wangdaye.com.geometricweather.common.basic.models.weather.Pollen;
import wangdaye.com.geometricweather.common.basic.models.weather.Precipitation;
import wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationDuration;
import wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationProbability;
import wangdaye.com.geometricweather.common.basic.models.weather.Temperature;
import wangdaye.com.geometricweather.common.basic.models.weather.UV;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode;
import wangdaye.com.geometricweather.common.basic.models.weather.Wind;
import wangdaye.com.geometricweather.common.basic.models.weather.WindDegree;
import wangdaye.com.geometricweather.weather.json.caiyun.CaiYunWeatherResult;
import wangdaye.com.geometricweather.weather.services.WeatherService;

public class CaiyunResultConverter {

    @NonNull
    public static WeatherService.WeatherResultWrapper convert(Context context, Location location,
                                                               CaiYunWeatherResult result) {
        try {
            if (result == null || result.result == null || result.result.realtime == null) {
                return new WeatherService.WeatherResultWrapper(null);
            }

            CaiYunWeatherResult.RealtimeBean r = result.result.realtime;
            CaiYunWeatherResult.DailyBean daily = result.result.daily;
            CaiYunWeatherResult.HourlyBean hourly = result.result.hourly;

            Date now = new Date(result.server_time * 1000);
            String skycon = r.skycon != null ? r.skycon : "CLEAR_DAY";

            Weather weather = new Weather(
                    new Base(
                            location.getCityId(),
                            now.getTime(),
                            now,
                            now.getTime(),
                            now,
                            now.getTime()
                    ),
                    new Current(
                            getWeatherText(skycon),
                            getWeatherCode(skycon),
                            new Temperature(
                                    (int) Math.round(r.temperature),
                                    r.life_index != null
                                            ? (int) Math.round(r.apparent_temperature)
                                            : null,
                                    null, null, null, null, null
                            ),
                            new Precipitation(
                                    r.precipitation != null && r.precipitation.local != null
                                            ? (float) r.precipitation.local.intensity : null,
                                    null, null, null, null
                            ),
                            new PrecipitationProbability(null, null, null, null, null),
                            r.wind != null
                                    ? new Wind(
                                            getWindDirection(r.wind.direction),
                                            new WindDegree((float) r.wind.direction, false),
                                            (float) r.wind.speed,
                                            CommonConverter.getWindLevel(context, (float) r.wind.speed)
                                    )
                                    : new Wind("", new WindDegree(0, true), null, ""),
                            new UV(
                                    r.life_index != null && r.life_index.ultraviolet != null
                                            ? r.life_index.ultraviolet.index : null,
                                    null,
                                    null
                            ),
                            getAirQuality(context, r.air_quality),
                            (float) r.humidity,
                            (float) r.pressure,
                            (float) r.visibility,
                            null, null, null, null,
                            result.result.forecast_keypoint
                    ),
                    null,
                    getDailyList(context, daily, result.timezone),
                    getHourlyList(context, hourly, result.timezone),
                    new ArrayList<Minutely>(),
                    new ArrayList<Alert>()
            );
            return new WeatherService.WeatherResultWrapper(weather);
        } catch (Exception e) {
            e.printStackTrace();
            return new WeatherService.WeatherResultWrapper(null);
        }
    }

    private static AirQuality getAirQuality(Context context, CaiYunWeatherResult.AirQualityBean aq) {
        if (aq == null) {
            return new AirQuality(null, null, null, null, null, null, null, null);
        }
        String quality = null;
        Integer index = null;
        try {
            index = aq.aqi != null ? aq.aqi.chn : null;
            quality = CommonConverter.getAqiQuality(context, index);
        } catch (Exception ignored) {}
        return new AirQuality(
                quality, index,
                (float) aq.pm25, (float) aq.pm10,
                (float) aq.so2, (float) aq.no2,
                (float) aq.o3, (float) aq.co
        );
    }

    private static List<Daily> getDailyList(Context context,
                                            CaiYunWeatherResult.DailyBean daily,
                                            String timezone) {
        List<Daily> list = new ArrayList<>();
        if (daily == null || daily.skycon == null || daily.temperature == null) {
            return list;
        }
        int count = Math.min(daily.skycon.size(), daily.temperature.size());
        for (int i = 0; i < count; i++) {
            String daySkycon = daily.skycon.get(i).value;
            String nightSkycon = daily.skycon_20h_32h != null && i < daily.skycon_20h_32h.size()
                    ? daily.skycon_20h_32h.get(i).value : daySkycon;
            Double tempMax = daily.temperature.get(i).max;
            Double tempMin = daily.temperature.get(i).min;
            if (tempMax == null) tempMax = 0.0;
            if (tempMin == null) tempMin = 0.0;

            Calendar calendar = Calendar.getInstance();
            try {
                calendar.setTime(parseDate(daily.skycon.get(i).date, timezone));
            } catch (Exception e) {
                calendar.setTime(new Date());
                calendar.add(Calendar.DAY_OF_YEAR, i);
            }

            list.add(new Daily(
                    calendar.getTime(),
                    calendar.getTimeInMillis(),
                    new HalfDay(
                            getWeatherText(daySkycon), getWeatherText(daySkycon),
                            getWeatherCode(daySkycon),
                            new Temperature(
                                    (int) Math.round(tempMax), null, null, null, null, null, null
                            ),
                            new Precipitation(null, null, null, null, null),
                            new PrecipitationProbability(null, null, null, null, null),
                            new PrecipitationDuration(null, null, null, null, null),
                            getWind(context, daily, i, true),
                            null
                    ),
                    new HalfDay(
                            getWeatherText(nightSkycon), getWeatherText(nightSkycon),
                            getWeatherCode(nightSkycon),
                            new Temperature(
                                    (int) Math.round(tempMin), null, null, null, null, null, null
                            ),
                            new Precipitation(null, null, null, null, null),
                            new PrecipitationProbability(null, null, null, null, null),
                            new PrecipitationDuration(null, null, null, null, null),
                            getWind(context, daily, i, false),
                            null
                    ),
                    getAstro(daily, i),
                    new Astro(null, null),
                    new MoonPhase(null, null),
                    getDailyAirQuality(context, daily, i),
                    new Pollen(null, null, null, null, null, null, null, null, null, null, null, null),
                    new UV(null, null, null),
                    0f
            ));
        }
        return list;
    }

    @Nullable
    private static Astro getAstro(CaiYunWeatherResult.DailyBean daily, int i) {
        try {
            if (daily.astro != null && i < daily.astro.size()) {
                return new Astro(
                        parseTime(daily.astro.get(i).sunrise.time),
                        parseTime(daily.astro.get(i).sunset.time)
                );
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Nullable
    private static Wind getWind(Context context, CaiYunWeatherResult.DailyBean daily, int i, boolean day) {
        try {
            CaiYunWeatherResult.WindDailyBean windData;
            if (daily.wind != null && i < daily.wind.size()) {
                windData = daily.wind.get(i);
            } else if (daily.wind_08h_20h != null && i < daily.wind_08h_20h.size() && day) {
                windData = daily.wind_08h_20h.get(i);
            } else if (daily.wind_20h_32h != null && i < daily.wind_20h_32h.size() && !day) {
                windData = daily.wind_20h_32h.get(i);
            } else {
                return new Wind("", new WindDegree(0, true), null, "");
            }
            double speed = windData.avg != null ? windData.avg.speed : 0;
            double dir = windData.avg != null ? windData.avg.direction : 0;
            return new Wind(
                    getWindDirection((float) dir),
                    new WindDegree((float) dir, false),
                    (float) speed,
                    CommonConverter.getWindLevel(context, (float) speed)
            );
        } catch (Exception e) {
            return new Wind(null, null, null, null);
        }
    }

    private static AirQuality getDailyAirQuality(Context context, CaiYunWeatherResult.DailyBean daily, int i) {
        try {
            if (daily.air_quality != null && daily.air_quality.size() > 0) {
                int index = Math.min(i, daily.air_quality.size() - 1);
                CaiYunWeatherResult.AirQualityDailyBean aqDay = daily.air_quality.get(index);
                int aqiValue = 0;
                if (aqDay.aqi != null && aqDay.aqi.size() > 0) {
                    int aqiIndex = Math.min(i, aqDay.aqi.size() - 1);
                    CaiYunWeatherResult.AqiBean aqiObj = aqDay.aqi.get(aqiIndex).avg;
                    aqiValue = aqiObj != null ? aqiObj.chn : 0;
                }
                Integer pm25 = null;
                if (aqDay.pm25 != null && aqDay.pm25.size() > 0) {
                    int pmIndex = Math.min(i, aqDay.pm25.size() - 1);
                    pm25 = aqDay.pm25.get(pmIndex).avg;
                }
                return new AirQuality(
                        CommonConverter.getAqiQuality(context, aqiValue), aqiValue,
                        pm25 != null ? (float) pm25 : null, null,
                        null, null, null, null
                );
            }
        } catch (Exception ignored) {}
        return new AirQuality(null, null, null, null, null, null, null, null);
    }

    private static List<Hourly> getHourlyList(Context context,
                                               CaiYunWeatherResult.HourlyBean hourly,
                                               String timezone) {
        List<Hourly> list = new ArrayList<>();
        if (hourly == null || hourly.temperature == null || hourly.skycon == null) {
            return list;
        }
        int count = Math.min(hourly.temperature.size(), hourly.skycon.size());
        for (int i = 0; i < count; i++) {
            String skycon = hourly.skycon.get(i).value;
            double tempValue = hourly.temperature.get(i).value;
            Calendar calendar = Calendar.getInstance();
            try {
                calendar.setTime(parseDate(hourly.skycon.get(i).datetime, timezone));
            } catch (Exception e) {
                calendar.setTime(new Date());
                calendar.add(Calendar.HOUR_OF_DAY, i);
            }

            list.add(new Hourly(
                    calendar.getTime(),
                    calendar.getTimeInMillis(),
                    skycon != null && (skycon.contains("DAY") || !skycon.contains("NIGHT")),
                    getWeatherText(skycon),
                    getWeatherCode(skycon),
                    new Temperature(
                            (int) Math.round(tempValue), null, null, null, null, null, null
                    ),
                    new Precipitation(null, null, null, null, null),
                    new PrecipitationProbability(null, null, null, null, null),
                    new Wind("", new WindDegree(0, true), null, ""),
                    new UV(null, null, null)
            ));
        }
        return list;
    }

    private static String getWeatherText(String skycon) {
        if (TextUtils.isEmpty(skycon)) return "未知";
        switch (skycon) {
            case "CLEAR_DAY": return "晴";
            case "CLEAR_NIGHT": return "晴";
            case "PARTLY_CLOUDY_DAY": return "多云";
            case "PARTLY_CLOUDY_NIGHT": return "多云";
            case "CLOUDY": return "阴";
            case "LIGHT_RAIN": return "小雨";
            case "MODERATE_RAIN": return "中雨";
            case "HEAVY_RAIN": return "大雨";
            case "STORM_RAIN": return "暴雨";
            case "LIGHT_SNOW": return "小雪";
            case "MODERATE_SNOW": return "中雪";
            case "HEAVY_SNOW": return "大雪";
            case "STORM_SNOW": return "暴雪";
            case "LIGHT_HAIL": return "冰雹";
            case "HAIL": return "冰雹";
            case "LIGHT_SLEET": return "雨夹雪";
            case "SLEET": return "雨夹雪";
            case "THUNDERSTORM": return "雷阵雨";
            case "THUNDER": return "雷雨";
            case "FOG": return "雾";
            case "HAZE": return "霾";
            case "WIND": return "大风";
            case "DUST": return "扬沙";
            case "SAND": return "沙尘暴";
            default: return "未知";
        }
    }

    private static WeatherCode getWeatherCode(String skycon) {
        return WeatherCode.getInstance(skycon != null ? skycon : "");
    }

    private static String getWindDirection(double degree) {
        String[] directions = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        int index = (int) Math.round(degree / 22.5) % 16;
        if (index < 0) index = 0;
        return directions[index];
    }

    @Nullable
    private static Date parseDate(String dateStr, String timezone) {
        if (dateStr == null) return null;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm", java.util.Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone(timezone != null ? timezone : "Asia/Shanghai"));
            return sdf.parse(dateStr);
        } catch (Exception e) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                        "yyyy-MM-dd", java.util.Locale.getDefault());
                sdf.setTimeZone(TimeZone.getTimeZone(timezone != null ? timezone : "Asia/Shanghai"));
                return sdf.parse(dateStr);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    @Nullable
    private static Date parseTime(String timeStr) {
        if (timeStr == null) return null;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "HH:mm", java.util.Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.parse(timeStr);
        } catch (Exception e) {
            return null;
        }
    }
}
