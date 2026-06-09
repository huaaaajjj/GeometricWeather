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
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.weather.json.owm.OwmLocationResult;
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode;
import wangdaye.com.geometricweather.common.basic.models.weather.Wind;
import wangdaye.com.geometricweather.common.basic.models.weather.WindDegree;
import wangdaye.com.geometricweather.weather.json.owm.OwmAirPollutionResult;
import wangdaye.com.geometricweather.weather.json.owm.OwmCurrentResult;
import wangdaye.com.geometricweather.weather.json.owm.OwmForecastResult;
import wangdaye.com.geometricweather.weather.services.WeatherService;

public class OwmResultConverter {

    @NonNull
    public static WeatherService.WeatherResultWrapper convert(
            Context context, Location location,
            OwmCurrentResult currentResult,
            OwmForecastResult forecastResult,
            @Nullable OwmAirPollutionResult airPollutionResult) {
        try {
            if (currentResult == null || currentResult.weather == null
                    || currentResult.weather.isEmpty() || currentResult.main == null) {
                return new WeatherService.WeatherResultWrapper(null);
            }

            int timezoneOffset = currentResult.timezone;
            Date now = new Date((long) currentResult.dt * 1000);
            int weatherId = currentResult.weather.get(0).id;

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
                            getWeatherText(weatherId),
                            getWeatherCode(weatherId),
                            new Temperature(
                                    (int) Math.round(currentResult.main.temp),
                                    (int) Math.round(currentResult.main.feels_like),
                                    null, null, null, null, null
                            ),
                            new Precipitation(null, null, null, null, null),
                            new PrecipitationProbability(null, null, null, null, null),
                            new Wind(
                                    getWindDirection((float) currentResult.wind.deg),
                                    new WindDegree((float) currentResult.wind.deg, false),
                                    (float) currentResult.wind.speed,
                                    CommonConverter.getWindLevel(context, (float) currentResult.wind.speed)
                            ),
                            new UV(null, null, null),
                            null,
                            (float) currentResult.main.humidity,
                            (float) currentResult.main.pressure,
                            (float) currentResult.visibility,
                            null, null, null, null, null
                    ),
                    null,
                    getDailyList(context, forecastResult, timezoneOffset),
                    getHourlyList(context, forecastResult, timezoneOffset),
                    new ArrayList<Minutely>(),
                    new ArrayList<Alert>()
            );
            return new WeatherService.WeatherResultWrapper(weather);
        } catch (Exception e) {
            e.printStackTrace();
            return new WeatherService.WeatherResultWrapper(null);
        }
    }

    private static List<Daily> getDailyList(Context context,
                                             OwmForecastResult forecast,
                                             int timezoneOffset) {
        List<Daily> dailyList = new ArrayList<>();
        if (forecast == null || forecast.list == null || forecast.list.isEmpty()) {
            return dailyList;
        }

        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat(
                "yyyy-MM-dd", java.util.Locale.getDefault());

        String currentDate = "";
        List<OwmForecastResult.ListBean> dayEntries = new ArrayList<>();

        for (OwmForecastResult.ListBean entry : forecast.list) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis((long) entry.dt * 1000);
            String dateStr = dateFormat.format(cal.getTime());

            if (!dateStr.equals(currentDate)) {
                if (!dayEntries.isEmpty()) {
                    Daily daily = buildDaily(context, dayEntries, timezoneOffset);
                    if (daily != null) dailyList.add(daily);
                }
                currentDate = dateStr;
                dayEntries.clear();
            }
            dayEntries.add(entry);
        }
        if (!dayEntries.isEmpty()) {
            Daily daily = buildDaily(context, dayEntries, timezoneOffset);
            if (daily != null) dailyList.add(daily);
        }

        return dailyList;
    }

    @Nullable
    private static Daily buildDaily(Context context,
                                    List<OwmForecastResult.ListBean> entries,
                                    int timezoneOffset) {
        if (entries.isEmpty()) return null;

        double tempMin = Double.MAX_VALUE, tempMax = Double.MIN_VALUE;
        double dayTemp = 0, nightTemp = 0;
        int dayCount = 0, nightCount = 0;
        int avgHumidity = 0;
        double totalPop = 0;
        int weatherId = entries.get(0).weather.get(0).id;
        int nightWeatherId = entries.get(entries.size() - 1).weather.get(0).id;
        double windSpeed = 0, windDeg = 0;
        int windCount = 0;
        long firstDt = entries.get(0).dt;

        for (OwmForecastResult.ListBean e : entries) {
            if (e.weather != null && !e.weather.isEmpty()) {
                weatherId = e.weather.get(0).id;
            }
            tempMin = Math.min(tempMin, e.main.temp_min);
            tempMax = Math.max(tempMax, e.main.temp_max);
            avgHumidity += e.main.humidity;
            totalPop += e.pop;
            windSpeed += e.wind.speed;
            windDeg += e.wind.deg;
            windCount++;

            int hour = (int) ((e.dt + timezoneOffset) % 86400 / 3600);
            if (hour >= 6 && hour < 18) {
                dayTemp += e.main.temp;
                dayCount++;
                weatherId = e.weather != null && !e.weather.isEmpty()
                        ? e.weather.get(0).id : weatherId;
            } else {
                nightTemp += e.main.temp;
                nightCount++;
                nightWeatherId = e.weather != null && !e.weather.isEmpty()
                        ? e.weather.get(0).id : nightWeatherId;
            }
        }

        avgHumidity /= entries.size();
        float avgPop = (float) (totalPop / entries.size());
        float avgWindSpeed = (float) (windSpeed / windCount);
        float avgWindDeg = (float) (windDeg / windCount);

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis((long) firstDt * 1000);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        double avgDayTemp = dayCount > 0 ? dayTemp / dayCount : (tempMax + tempMin) / 2;
        double avgNightTemp = nightCount > 0 ? nightTemp / nightCount : (tempMax + tempMin) / 2;

        return new Daily(
                calendar.getTime(),
                calendar.getTimeInMillis(),
                new HalfDay(
                        getWeatherText(weatherId),
                        getWeatherText(weatherId),
                        getWeatherCode(weatherId),
                        new Temperature(
                                (int) Math.round(avgDayTemp), null, null, null, null, null, null
                        ),
                        new Precipitation(null, null, null, null, null),
                        new PrecipitationProbability(avgPop, null, null, null, null),
                        new PrecipitationDuration(null, null, null, null, null),
                        new Wind(
                                getWindDirection(avgWindDeg),
                                new WindDegree(avgWindDeg, false),
                                avgWindSpeed,
                                CommonConverter.getWindLevel(context, avgWindSpeed)
                        ),
                        null
                ),
                new HalfDay(
                        getWeatherText(nightWeatherId),
                        getWeatherText(nightWeatherId),
                        getWeatherCode(nightWeatherId),
                        new Temperature(
                                (int) Math.round(avgNightTemp), null, null, null, null, null, null
                        ),
                        new Precipitation(null, null, null, null, null),
                        new PrecipitationProbability(avgPop, null, null, null, null),
                        new PrecipitationDuration(null, null, null, null, null),
                        new Wind(
                                getWindDirection(avgWindDeg),
                                new WindDegree(avgWindDeg, false),
                                avgWindSpeed,
                                CommonConverter.getWindLevel(context, avgWindSpeed)
                        ),
                        null
                ),
                null,
                new Astro(null, null),
                new MoonPhase(null, null),
                null,
                new Pollen(null, null, null, null, null, null, null, null, null, null, null, null),
                new UV(null, null, null),
                0f
        );
    }

    private static List<Hourly> getHourlyList(Context context,
                                               OwmForecastResult forecast,
                                               int timezoneOffset) {
        List<Hourly> hourlyList = new ArrayList<>();
        if (forecast == null || forecast.list == null) return hourlyList;

        for (OwmForecastResult.ListBean entry : forecast.list) {
            int weatherId = entry.weather != null && !entry.weather.isEmpty()
                    ? entry.weather.get(0).id : 800;
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis((long) entry.dt * 1000);

            hourlyList.add(new Hourly(
                    calendar.getTime(),
                    calendar.getTimeInMillis(),
                    true,
                    getWeatherText(weatherId),
                    getWeatherCode(weatherId),
                    new Temperature(
                            (int) Math.round(entry.main.temp), null, null, null, null, null, null
                    ),
                    new Precipitation(
                            entry.rain != null ? (float) entry.rain._3h : null,
                            null, null, null, null
                    ),
                    new PrecipitationProbability((float) entry.pop, null, null, null, null),
                    new Wind(
                            getWindDirection((float) entry.wind.deg),
                            new WindDegree((float) entry.wind.deg, false),
                            (float) entry.wind.speed,
                            CommonConverter.getWindLevel(context, (float) entry.wind.speed)
                    ),
                    new UV(null, null, null)
            ));
        }
        return hourlyList;
    }

    public static Location convert(@Nullable Location location, OwmLocationResult result,
                                    @Nullable String zipCode) {
        if (result == null) return null;
        if (location != null
                && !TextUtils.isEmpty(location.getProvince())
                && !TextUtils.isEmpty(location.getCity())
                && !TextUtils.isEmpty(location.getDistrict())) {
            return new Location(
                    Double.toString(result.lat) + ',' + Double.toString(result.lon),
                    (float) result.lat,
                    (float) result.lon,
                    TimeZone.getTimeZone("UTC"),
                    result.country,
                    "",
                    result.name,
                    "",
                    null,
                    WeatherSource.OWM,
                    false,
                    false,
                    !TextUtils.isEmpty(result.country)
                            && (result.country.equals("CN")
                            || result.country.equals("cn")
                            || result.country.equals("HK")
                            || result.country.equals("hk")
                            || result.country.equals("TW")
                            || result.country.equals("tw"))
            );
        } else {
            return new Location(
                    Double.toString(result.lat) + ',' + Double.toString(result.lon),
                    (float) result.lat,
                    (float) result.lon,
                    TimeZone.getTimeZone("UTC"),
                    result.country,
                    "",
                    result.name,
                    "",
                    null,
                    WeatherSource.OWM,
                    false,
                    false,
                    !TextUtils.isEmpty(result.country)
                            && (result.country.equals("CN")
                            || result.country.equals("cn")
                            || result.country.equals("HK")
                            || result.country.equals("hk")
                            || result.country.equals("TW")
                            || result.country.equals("tw"))
            );
        }
    }

    private static String getWeatherText(int icon) {
        switch (icon) {
            case 201:
            case 202:
            case 210:
            case 211:
            case 212:
            case 221:
            case 230:
            case 231:
            case 232:
                return "雷阵雨";
            case 300:
            case 301:
            case 302:
            case 310:
            case 311:
            case 312:
            case 313:
            case 314:
            case 321:
                return "毛毛雨";
            case 500:
                return "小雨";
            case 501:
                return "中雨";
            case 502:
                return "大雨";
            case 503:
                return "暴雨";
            case 504:
                return "大暴雨";
            case 511:
                return "冻雨";
            case 520:
                return "阵雨";
            case 521:
                return "阵雨";
            case 522:
            case 531:
                return "大阵雨";
            case 600:
                return "小雪";
            case 601:
                return "中雪";
            case 602:
                return "大雪";
            case 611:
            case 612:
                return "雨夹雪";
            case 613:
            case 614:
            case 615:
            case 616:
                return "雨夹雪";
            case 620:
                return "小雪";
            case 621:
                return "中雪";
            case 622:
                return "大雪";
            case 701:
            case 711:
            case 721:
            case 741:
                return "霾";
            case 731:
            case 751:
                return "扬沙";
            case 761:
                return "沙尘";
            case 762:
                return "沙尘暴";
            case 771:
                return "大风";
            case 781:
                return "龙卷风";
            case 800:
                return "晴";
            case 801:
                return "晴";
            case 802:
                return "多云";
            case 803:
                return "多云";
            case 804:
                return "阴";
            default:
                return "未知";
        }
    }

    private static WeatherCode getWeatherCode(int icon) {
        switch (icon) {
            case 201:
            case 202:
            case 210:
            case 211:
            case 212:
            case 221:
            case 230:
            case 231:
            case 232:
                return WeatherCode.THUNDERSTORM;
            case 300:
            case 301:
            case 302:
            case 310:
            case 311:
            case 312:
            case 313:
            case 314:
            case 321:
            case 500:
            case 501:
            case 502:
            case 503:
            case 504:
            case 511:
            case 520:
            case 521:
            case 522:
            case 531:
                return WeatherCode.RAIN;
            case 600:
            case 601:
            case 602:
            case 611:
            case 612:
            case 613:
            case 614:
            case 615:
            case 616:
            case 620:
            case 621:
            case 622:
                return WeatherCode.SNOW;
            case 701:
            case 711:
            case 721:
            case 731:
            case 741:
            case 751:
            case 761:
            case 762:
                return WeatherCode.FOG;
            case 771:
            case 781:
                return WeatherCode.WIND;
            case 800:
                return WeatherCode.CLEAR;
            case 801:
            case 802:
                return WeatherCode.PARTLY_CLOUDY;
            case 803:
            case 804:
                return WeatherCode.CLOUDY;
            default:
                return WeatherCode.CLEAR;
        }
    }

    private static String getWindDirection(float degree) {
        String[] directions = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        int index = (int) Math.round(degree / 22.5) % 16;
        if (index < 0) index = 0;
        return directions[index];
    }
}
