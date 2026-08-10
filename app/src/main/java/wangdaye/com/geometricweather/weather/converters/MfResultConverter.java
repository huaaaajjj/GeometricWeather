package wangdaye.com.geometricweather.weather.converters;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.common.basic.models.weather.AirQuality;
import wangdaye.com.geometricweather.common.basic.models.weather.Alert;
import wangdaye.com.geometricweather.common.basic.models.weather.Astro;
import wangdaye.com.geometricweather.common.basic.models.weather.Base;
import wangdaye.com.geometricweather.common.basic.models.weather.Current;
import wangdaye.com.geometricweather.common.basic.models.weather.Daily;
import wangdaye.com.geometricweather.common.basic.models.weather.HalfDay;
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
import wangdaye.com.geometricweather.weather.json.atmoaura.AtmoAuraQAResult;
import wangdaye.com.geometricweather.weather.json.mf.MfCurrentResult;
import wangdaye.com.geometricweather.weather.json.mf.MfEphemerisResult;
import wangdaye.com.geometricweather.weather.json.mf.MfForecastV2Result;
import wangdaye.com.geometricweather.weather.json.mf.MfLocationResult;
import wangdaye.com.geometricweather.weather.json.mf.MfRainResult;
import wangdaye.com.geometricweather.weather.json.mf.MfWarningsResult;
import wangdaye.com.geometricweather.weather.services.WeatherService;

/**
 * Météo France serves GeoJSON features (everything under "properties") with ISO-8601 timestamps.
 * Provider fields are frequently null — guard every one of them, since the Weather model's
 * @NonNull assertions throw and the outer catch would turn that into a silent "no data".
 */
public class MfResultConverter {

    private static final long ONE_HOUR = 3600 * 1000L;

    // Result of a coordinates search
    @NonNull
    public static Location convert(@Nullable Location location, MfForecastV2Result result) {
        MfForecastV2Result.ForecastProperties properties = result.properties;
        Float latitude = null;
        Float longitude = null;
        if (result.geometry != null && result.geometry.coordinates != null
                && result.geometry.coordinates.size() >= 2) {
            longitude = result.geometry.coordinates.get(0);
            latitude = result.geometry.coordinates.get(1);
        }

        boolean keepAddress = location != null
                && !TextUtils.isEmpty(location.getProvince())
                && !TextUtils.isEmpty(location.getCity())
                && !TextUtils.isEmpty(location.getDistrict());

        return new Location(
                properties.insee, // cityId
                latitude != null ? latitude : (location != null ? location.getLatitude() : 0f),
                longitude != null ? longitude : (location != null ? location.getLongitude() : 0f),
                TimeZone.getTimeZone(
                        properties.timezone != null ? properties.timezone : "Europe/Paris"),
                properties.country != null ? properties.country : "",
                keepAddress
                        ? location.getProvince()
                        : (properties.frenchDepartment != null ? properties.frenchDepartment : ""),
                keepAddress
                        ? location.getCity()
                        : (properties.name != null ? properties.name : ""),
                keepAddress ? location.getDistrict() : "",
                null,
                WeatherSource.MF,
                false,
                false,
                isChinese(properties.country)
        );
    }

    // Result of a query string search
    @NonNull
    public static Location convert(@Nullable Location location, MfLocationResult result) {
        boolean keepAddress = location != null
                && !TextUtils.isEmpty(location.getProvince())
                && !TextUtils.isEmpty(location.getCity())
                && !TextUtils.isEmpty(location.getDistrict());

        return new Location(
                result.postCode, // cityId
                (float) result.lat,
                (float) result.lon,
                TimeZone.getTimeZone("Europe/Paris"), // Météo France serves France only; MfLocationResult has no tz (real tz comes with the forecast)
                result.country != null ? result.country : "",
                keepAddress
                        ? location.getProvince()
                        : (result.admin2 != null ? result.admin2 : ""), // Domain (département)
                keepAddress
                        ? location.getCity()
                        : (result.name != null ? result.name + (result.postCode == null ? "" : (" (" + result.postCode + ")")) : ""),
                keepAddress ? location.getDistrict() : "",
                null,
                WeatherSource.MF,
                false,
                false,
                isChinese(result.country)
        );
    }

    private static boolean isChinese(@Nullable String country) {
        if (TextUtils.isEmpty(country)) {
            return false;
        }
        String code = country.toUpperCase();
        return code.startsWith("CN") || code.startsWith("HK") || code.startsWith("TW");
    }

    @NonNull
    public static WeatherService.WeatherResultWrapper convert(Context context,
                                                              Location location,
                                                              MfCurrentResult currentResult,
                                                              MfForecastV2Result forecastResult,
                                                              MfEphemerisResult ephemerisResult,
                                                              MfRainResult rainResult,
                                                              MfWarningsResult warningsResult,
                                                              @Nullable AtmoAuraQAResult aqiAtmoAuraResult) {
        try {
            MfForecastV2Result.ForecastProperties properties = forecastResult.properties;
            List<MfForecastV2Result.ForecastProperties.HourForecast> hourlyForecast =
                    properties.forecast != null ? properties.forecast : new ArrayList<>();
            List<MfForecastV2Result.ForecastProperties.ForecastV2> dailyForecast =
                    properties.dailyForecast != null ? properties.dailyForecast : new ArrayList<>();

            List<Hourly> hourly = getHourlyList(
                    context, hourlyForecast, properties.probabilityForecast);
            List<Daily> daily = getDailyList(
                    context, dailyForecast, hourlyForecast, hourly, ephemerisResult, aqiAtmoAuraResult);
            if (daily.isEmpty()) {
                // Every consumer assumes at least one day; an empty list crashes the UI downstream.
                return new WeatherService.WeatherResultWrapper(null);
            }

            long updateTime = forecastResult.updateTime != null
                    ? forecastResult.updateTime.getTime() : System.currentTimeMillis();

            Weather weather = new Weather(
                    new Base(
                            location.getCityId(),
                            System.currentTimeMillis(),
                            new Date(updateTime),
                            updateTime,
                            new Date(),
                            System.currentTimeMillis()
                    ),
                    getCurrent(context, currentResult, aqiAtmoAuraResult),
                    null, // TODO: Fill in with observation data instead
                    daily,
                    hourly,
                    getMinutelyList(daily.get(0).sun(), rainResult),
                    getWarningsList(warningsResult)
            );
            return new WeatherService.WeatherResultWrapper(weather);
        } catch (Exception ignored) {
            return new WeatherService.WeatherResultWrapper(null);
        }
    }

    private static Current getCurrent(Context context,
                                      @Nullable MfCurrentResult currentResult,
                                      @Nullable AtmoAuraQAResult aqiAtmoAuraResult) {
        MfCurrentResult.Properties.Observation observation =
                currentResult != null && currentResult.properties != null
                        ? currentResult.properties.gridded : null;

        Float windSpeed = observation != null && observation.windSpeed != null
                ? observation.windSpeed * 3.6f : null;

        return new Current(
                observation != null && observation.weatherDescription != null
                        ? observation.weatherDescription : "",
                getWeatherCode(observation != null ? observation.weatherIcon : null),
                new Temperature(
                        observation != null ? toInt(observation.temperature) : 0,
                        null, null, null, null, null, null
                ),
                new Precipitation(null, null, null, null, null),
                new PrecipitationProbability(null, null, null, null, null),
                new Wind(
                        observation != null && observation.windIcon != null ? observation.windIcon : "",
                        getWindDegree(observation != null ? observation.windDirection : null),
                        windSpeed,
                        windSpeed != null ? CommonConverter.getWindLevel(context, windSpeed) : ""
                ),
                new UV(null, null, null),
                getAirQuality(new Date(), aqiAtmoAuraResult),
                null, null, null, null, null, null, null, null
        );
    }

    /** MF reports -1 for a variable wind direction. */
    private static WindDegree getWindDegree(@Nullable Integer direction) {
        if (direction == null || direction == -1) {
            return new WindDegree(0, true);
        }
        return new WindDegree(direction, false);
    }

    // This can be improved by adding Aqi results from other regions
    private static AirQuality getAirQuality(Date requestedDate, @Nullable AtmoAuraQAResult aqiAtmoAuraResult) {
        if (aqiAtmoAuraResult == null || aqiAtmoAuraResult.indexs == null) {
            return new AirQuality(
                    null, null,
                    null, null,
                    null, null,
                    null, null
            );
        } else {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd");
            if (matchesDay(fmt, requestedDate, aqiAtmoAuraResult.indexs.yesterday)) {
                return getAirQuality(aqiAtmoAuraResult.indexs.yesterday);
            } else if (matchesDay(fmt, requestedDate, aqiAtmoAuraResult.indexs.today)) {
                return getAirQuality(aqiAtmoAuraResult.indexs.today);
            } else if (matchesDay(fmt, requestedDate, aqiAtmoAuraResult.indexs.tomorrow)) {
                return getAirQuality(aqiAtmoAuraResult.indexs.tomorrow);
            } else if (matchesDay(fmt, requestedDate, aqiAtmoAuraResult.indexs.inTwoDays)) {
                return getAirQuality(aqiAtmoAuraResult.indexs.inTwoDays);
            } else {
                return new AirQuality(
                        null, null,
                        null, null,
                        null, null,
                        null, null
                );
            }
        }
    }

    private static boolean matchesDay(SimpleDateFormat fmt, Date requestedDate,
                                      @Nullable AtmoAuraQAResult.MultiDaysIndexs.MultiIndex index) {
        return index != null && index.date != null
                && fmt.format(requestedDate).equals(fmt.format(index.date));
    }

    private static AirQuality getAirQuality(AtmoAuraQAResult.MultiDaysIndexs.MultiIndex index) {
        return new AirQuality(
                index.aggregatedIndex != null ? index.aggregatedIndex.quali : null,
                index.aggregatedIndex != null ? (int) Math.round(index.aggregatedIndex.val) : null,
                null, index.pm10 != null ? (float) index.pm10.val : null,
                null, index.no2 != null ? (float) index.no2.val : null,
                index.o3 != null ? (float) index.o3.val : null, null
        );
    }

    private static HalfDay getHalfDay(Context context, boolean isDaytime, List<Hourly> hourly,
                                      List<MfForecastV2Result.ForecastProperties.HourForecast> hourlyForecast,
                                      MfForecastV2Result.ForecastProperties.ForecastV2 dailyForecast) {
        Integer temp = isDaytime ? toIntOrNull(dailyForecast.tMax) : toIntOrNull(dailyForecast.tMin);
        Integer tempWindChill = null;

        Float precipitationTotal = 0.0f;
        Float precipitationRain = 0.0f;
        Float precipitationSnow = 0.0f;

        Float probPrecipitationTotal = 0.0f;
        Float probPrecipitationRain = 0.0f;
        Float probPrecipitationSnow = 0.0f;
        Float probPrecipitationIce = 0.0f;

        long dayStart = dailyForecast.time.getTime();

        for (Hourly hour : hourly) {
            if (isInHalfDay(hour.getTime(), dayStart, isDaytime)) {
                // Temperature
                if (isDaytime) {
                    if (temp == null || hour.getTemperature().getTemperature() > temp) {
                        temp = hour.getTemperature().getTemperature();
                    }
                    Integer windChill = hour.getTemperature().getWindChillTemperature();
                    if (windChill != null && (tempWindChill == null || windChill > tempWindChill)) {
                        tempWindChill = windChill;
                    }
                } else {
                    if (temp == null || hour.getTemperature().getTemperature() < temp) {
                        temp = hour.getTemperature().getTemperature();
                    }
                    Integer windChill = hour.getTemperature().getWindChillTemperature();
                    if (windChill != null && (tempWindChill == null || windChill < tempWindChill)) {
                        tempWindChill = windChill;
                    }
                }

                // Precipitation
                precipitationTotal += orZero(hour.getPrecipitation().getTotal());
                precipitationRain += orZero(hour.getPrecipitation().getRain());
                precipitationSnow += orZero(hour.getPrecipitation().getSnow());

                // Precipitation probability
                probPrecipitationTotal = max(probPrecipitationTotal, hour.getPrecipitationProbability().getTotal());
                probPrecipitationRain = max(probPrecipitationRain, hour.getPrecipitationProbability().getRain());
                probPrecipitationSnow = max(probPrecipitationSnow, hour.getPrecipitationProbability().getSnow());
                probPrecipitationIce = max(probPrecipitationIce, hour.getPrecipitationProbability().getIce());
            }
        }

        Integer cloudCover = null;
        String windDirection = "Pas d’info";
        WindDegree windDegree = new WindDegree(0, false);
        Float windSpeed = null;
        String windLevel = "Pas d’info";

        for (MfForecastV2Result.ForecastProperties.HourForecast hourForecast : hourlyForecast) {
            if (hourForecast.time == null
                    || !isInHalfDay(hourForecast.time.getTime(), dayStart, isDaytime)) {
                continue;
            }
            if (hourForecast.totalCloudCover != null
                    && (cloudCover == null || hourForecast.totalCloudCover > cloudCover)) {
                cloudCover = hourForecast.totalCloudCover;
            }
            if (hourForecast.windSpeed != null
                    && (windSpeed == null || hourForecast.windSpeed * 3.6f > windSpeed)) {
                windDirection = hourForecast.windIcon != null ? hourForecast.windIcon : windDirection;
                windDegree = getWindDegree(hourForecast.windDirection);
                windSpeed = hourForecast.windSpeed * 3.6f;
                windLevel = CommonConverter.getWindLevel(context, windSpeed);
            }
        }

        return new HalfDay(
                dailyForecast.dailyWeatherDescription == null ? "" : dailyForecast.dailyWeatherDescription,
                dailyForecast.dailyWeatherDescription == null ? "" : dailyForecast.dailyWeatherDescription,
                getWeatherCode(dailyForecast.dailyWeatherIcon),
                new Temperature(
                        temp != null ? temp : 0,
                        null,
                        null,
                        null,
                        tempWindChill,
                        null,
                        null
                ),
                new Precipitation(
                        precipitationTotal,
                        null,
                        precipitationRain,
                        precipitationSnow,
                        null
                ),
                new PrecipitationProbability(
                        probPrecipitationTotal,
                        null,
                        probPrecipitationRain,
                        probPrecipitationSnow,
                        probPrecipitationIce
                ),
                new PrecipitationDuration(
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                new Wind(
                        windDirection,
                        windDegree,
                        windSpeed,
                        windLevel
                ),
                cloudCover
        );
    }

    /** Day runs 06:00–18:00 after the day's midnight; night runs 18:00–06:00 the following day. */
    private static boolean isInHalfDay(long time, long dayStart, boolean isDaytime) {
        return isDaytime
                ? time >= dayStart + 6 * ONE_HOUR && time < dayStart + 18 * ONE_HOUR
                : time >= dayStart + 18 * ONE_HOUR && time < dayStart + 30 * ONE_HOUR;
    }

    private static float orZero(@Nullable Float value) {
        return value != null ? value : 0f;
    }

    private static Float max(Float current, @Nullable Float candidate) {
        return candidate != null && candidate > current ? candidate : current;
    }

    private static List<Daily> getDailyList(Context context,
                                            List<MfForecastV2Result.ForecastProperties.ForecastV2> dailyForecasts,
                                            List<MfForecastV2Result.ForecastProperties.HourForecast> hourlyForecast,
                                            List<Hourly> hourly,
                                            @Nullable MfEphemerisResult ephemerisResult,
                                            @Nullable AtmoAuraQAResult aqiAtmoAuraResult) {
        MfEphemerisResult.Properties.Ephemeris ephemeris =
                ephemerisResult != null && ephemerisResult.properties != null
                        ? ephemerisResult.properties.ephemeris : null;

        List<Daily> dailyList = new ArrayList<>(dailyForecasts.size());
        for (MfForecastV2Result.ForecastProperties.ForecastV2 dailyForecast : dailyForecasts) {
            // Skip days without a date or temperature: both are non-nullable downstream, and MF
            // pads the tail of the range with entries that carry neither.
            if (dailyForecast.time == null
                    || dailyForecast.tMin == null || dailyForecast.tMax == null) {
                continue;
            }
            dailyList.add(
                    new Daily(
                            dailyForecast.time,
                            dailyForecast.time.getTime(),
                            getHalfDay(context, true, hourly, hourlyForecast, dailyForecast),
                            getHalfDay(context, false, hourly, hourlyForecast, dailyForecast),
                            new Astro(dailyForecast.sunriseTime, dailyForecast.sunsetTime),
                            // Note: Below is the same moon data for all days, but since we are only showing the data for the current day in the app, this does not matter
                            new Astro(
                                    ephemeris != null ? ephemeris.moonriseTime : null,
                                    ephemeris != null ? ephemeris.moonsetTime : null
                            ),
                            new MoonPhase(
                                    ephemeris != null
                                            ? CommonConverter.getMoonPhaseAngle(ephemeris.moonPhaseDescription) : null,
                                    ephemeris != null ? ephemeris.moonPhaseDescription : null
                            ),
                            getAirQuality(dailyForecast.time, aqiAtmoAuraResult),
                            new Pollen(null, null, null, null, null, null, null, null, null, null, null, null),
                            new UV(dailyForecast.uvIndex, null, null),
                            getHoursOfDay(dailyForecast.sunriseTime, dailyForecast.sunsetTime)
                    )
            );
        }
        return dailyList;
    }

    /** MF publishes cumulative rain/snow over several windows; take the shortest one available. */
    @Nullable
    private static Float getShortestCumul(@Nullable Float cumul1H, @Nullable Float cumul3H,
                                          @Nullable Float cumul6H, @Nullable Float cumul12H,
                                          @Nullable Float cumul24H) {
        if (cumul1H != null) {
            return cumul1H;
        } else if (cumul3H != null) {
            return cumul3H;
        } else if (cumul6H != null) {
            return cumul6H;
        } else if (cumul12H != null) {
            return cumul12H;
        }
        return cumul24H;
    }

    private static Precipitation getHourlyPrecipitation(
            MfForecastV2Result.ForecastProperties.HourForecast hourlyForecast) {
        Float rainCumul = getShortestCumul(hourlyForecast.rain1h, hourlyForecast.rain3h,
                hourlyForecast.rain6h, hourlyForecast.rain12h, hourlyForecast.rain24h);
        Float snowCumul = getShortestCumul(hourlyForecast.snow1h, hourlyForecast.snow3h,
                hourlyForecast.snow6h, hourlyForecast.snow12h, hourlyForecast.snow24h);
        Float totalCumul;

        if (rainCumul == null) {
            totalCumul = snowCumul;
        } else if (snowCumul == null) {
            totalCumul = rainCumul;
        } else {
            totalCumul = snowCumul + rainCumul;
        }

        return new Precipitation(
                totalCumul,
                null,
                rainCumul,
                snowCumul,
                null
        );
    }

    private static PrecipitationProbability getHourlyPrecipitationProbability(
            @Nullable List<MfForecastV2Result.ForecastProperties.ProbabilityForecastV2> probabilityForecastResult,
            long time) {
        Float rainProbability = null;
        Float snowProbability = null;
        Float iceProbability = null;

        if (probabilityForecastResult == null) {
            return new PrecipitationProbability(null, null, null, null, null);
        }

        for (MfForecastV2Result.ForecastProperties.ProbabilityForecastV2 probabilityForecast
                : probabilityForecastResult) {
            if (probabilityForecast.time == null) {
                continue;
            }
            long start = probabilityForecast.time.getTime();

            /*
             * Probablity are given every 3 hours, sometimes every 6 hours.
             * Sometimes every 3 hour-schedule give 3 hours probability AND 6 hours probability,
             * sometimes only one of them
             * It's not very clear but we take all hours in order.
             */
            if (time >= start && time < start + 3 * ONE_HOUR) {
                if (probabilityForecast.rainHazard3h != null) {
                    rainProbability = probabilityForecast.rainHazard3h * 1f;
                } else if (probabilityForecast.rainHazard6h != null) {
                    rainProbability = probabilityForecast.rainHazard6h * 1f;
                }
                if (probabilityForecast.snowHazard3h != null) {
                    snowProbability = probabilityForecast.snowHazard3h * 1f;
                } else if (probabilityForecast.snowHazard6h != null) {
                    snowProbability = probabilityForecast.snowHazard6h * 1f;
                }
                if (probabilityForecast.freezingHazard != null) {
                    iceProbability = probabilityForecast.freezingHazard * 1f;
                }
            }

            /*
             * If it's found as part of the "6 hour schedule" and we find later a "3 hour schedule"
             * the "3 hour schedule" will overwrite the "6 hour schedule" below with the above
             */
            if (time >= start + 3 * ONE_HOUR && time < start + 6 * ONE_HOUR) {
                if (probabilityForecast.rainHazard6h != null) {
                    rainProbability = probabilityForecast.rainHazard6h * 1f;
                }
                if (probabilityForecast.snowHazard6h != null) {
                    snowProbability = probabilityForecast.snowHazard6h * 1f;
                }
                if (probabilityForecast.freezingHazard != null) {
                    iceProbability = probabilityForecast.freezingHazard * 1f;
                }
            }
        }

        List<Float> allProbabilities = new ArrayList<>();
        allProbabilities.add(rainProbability != null ? rainProbability : 0f);
        allProbabilities.add(snowProbability != null ? snowProbability : 0f);
        allProbabilities.add(iceProbability != null ? iceProbability : 0f);

        return new PrecipitationProbability(
                Collections.max(allProbabilities, null),
                null,
                rainProbability,
                snowProbability,
                iceProbability
        );
    }

    private static List<Hourly> getHourlyList(
            Context context,
            List<MfForecastV2Result.ForecastProperties.HourForecast> hourlyForecastResult,
            @Nullable List<MfForecastV2Result.ForecastProperties.ProbabilityForecastV2> probabilityForecastResult) {
        List<Hourly> hourlyList = new ArrayList<>(hourlyForecastResult.size());
        for (MfForecastV2Result.ForecastProperties.HourForecast hourlyForecast : hourlyForecastResult) {
            // Time and temperature are non-nullable downstream; MF leaves both out on padded entries.
            if (hourlyForecast.time == null || hourlyForecast.t == null) {
                continue;
            }
            Float windSpeed = hourlyForecast.windSpeed != null
                    ? hourlyForecast.windSpeed * 3.6f : null;
            hourlyList.add(
                    new Hourly(
                            hourlyForecast.time,
                            hourlyForecast.time.getTime(),
                            // TODO: Probably not the best way to check if it is daytime or nighttime
                            // Use CommonConverter.isDaylight(sunrise, sunset, hourlyForecast.time) instead
                            hourlyForecast.weatherIcon == null || !hourlyForecast.weatherIcon.endsWith("n"),
                            hourlyForecast.weatherDescription != null ? hourlyForecast.weatherDescription : "",
                            getWeatherCode(hourlyForecast.weatherIcon),
                            new Temperature(
                                    toInt(hourlyForecast.t),
                                    null,
                                    null,
                                    null,
                                    toIntOrNull(hourlyForecast.tWindchill),
                                    null,
                                    null
                            ),
                            getHourlyPrecipitation(hourlyForecast),
                            getHourlyPrecipitationProbability(
                                    probabilityForecastResult, hourlyForecast.time.getTime()),
                            new Wind(
                                    hourlyForecast.windIcon != null ? hourlyForecast.windIcon : "",
                                    getWindDegree(hourlyForecast.windDirection),
                                    windSpeed,
                                    windSpeed != null ? CommonConverter.getWindLevel(context, windSpeed) : ""
                            ),
                            new UV(null, null, null)
                    )
            );
        }
        return hourlyList;
    }

    private static List<Minutely> getMinutelyList(Astro sun, @Nullable MfRainResult rainResult) {
        if (rainResult == null || rainResult.properties == null
                || rainResult.properties.rainForecasts == null
                || rainResult.properties.rainForecasts.isEmpty()) {
            return new ArrayList<>();
        }
        List<MfRainResult.Properties.RainForecast> rainForecasts = rainResult.properties.rainForecasts;
        List<Minutely> minutelyList = new ArrayList<>(rainForecasts.size());
        Long minuteZero = null;
        for (MfRainResult.Properties.RainForecast rainForecast : rainForecasts) {
            if (rainForecast.time == null) {
                continue;
            }
            long minute = rainForecast.time.getTime() / 60000;
            if (minuteZero == null) {
                minuteZero = minute;
            }
            minutelyList.add(
                    new Minutely(
                            rainForecast.time,
                            rainForecast.time.getTime(),
                            sun.getRiseDate() != null && sun.getSetDate() != null
                                    && CommonConverter.isDaylight(
                                            sun.getRiseDate(), sun.getSetDate(), rainForecast.time),
                            rainForecast.rainIntensityDescription != null
                                    ? rainForecast.rainIntensityDescription : "",
                            // 1 means "no rain"; anything above that is an actual rain step.
                            rainForecast.rainIntensity != null && rainForecast.rainIntensity > 1
                                    ? WeatherCode.RAIN : getWeatherCode(null),
                            (int) (minute - minuteZero),
                            null,
                            null
                    )
            );
        }
        return minutelyList;
    }

    private static List<Alert> getWarningsList(@Nullable MfWarningsResult warningsResult) {
        if (warningsResult == null || warningsResult.phenomenonsItems == null) {
            return new ArrayList<>();
        }
        List<Alert> alertList = new ArrayList<>(warningsResult.phenomenonsItems.size());
        for (MfWarningsResult.PhenomenonMaxColor phemononItem : warningsResult.phenomenonsItems) {
            if (phemononItem.phenomenoMaxColorId > 1) { // Do not warn when there is nothing to warn (green alert)
                alertList.add(
                        new Alert(
                                phemononItem.phenomenonId,
                                new Date(warningsResult.updateTime * 1000), // FIXME: Do not take updateTime but phenomonon time instead
                                warningsResult.updateTime * 1000, // FIXME: Do not take updateTime but phenomonon time instead
                                getWarningType(phemononItem.phenomenonId) + " — " + getWarningText(phemononItem.phenomenoMaxColorId),
                                "", // TODO: Longer description (I think there is a report in the web service when alert is orange or red)
                                getWarningType(phemononItem.phenomenonId),
                                phemononItem.phenomenoMaxColorId, // TODO: Check Priority
                                getWarningColor(phemononItem.phenomenoMaxColorId)
                        )
                );
            }
        }
        Alert.deduplication(alertList);
        return alertList;
    }

    private static int toInt(@Nullable Float value) {
        return value != null ? (int) (value + 0.5) : 0;
    }

    @Nullable
    private static Integer toIntOrNull(@Nullable Float value) {
        return value != null ? (int) (value + 0.5) : null;
    }

    private static String getWarningType(int phemononId) {
        if (phemononId == 1) {
            return "Vent";
        } else if (phemononId == 2) {
            return "Pluie-Inondation";
        } else if (phemononId == 3) {
            return "Orages";
        } else if (phemononId == 4) {
            return "Crues";
        } else if (phemononId == 5) {
            return "Neige-Verglas";
        } else if (phemononId == 6) {
            return "Canicule";
        } else if (phemononId == 7) {
            return "Grand Froid";
        } else if (phemononId == 8) {
            return "Avalanches";
        } else if (phemononId == 9) {
            return "Vagues-Submersion";
        } else {
            return "Divers";
        }
    }

    private static String getWarningText(int colorId) {
        if (colorId == 4) {
            return "Vigilance absolue";
        } else if (colorId == 3) {
            return "Soyez très vigilant";
        } else if (colorId == 2) {
            return "Soyez attentif";
        } else {
            return "Pas de vigilance particulière";
        }
    }

    private static int getWarningColor(int colorId) {
        if (colorId == 4) {
            return Color.rgb(204, 0, 0);
        } else if (colorId == 3) {
            return Color.rgb(255, 184, 43);
        } else if (colorId == 2) {
            return Color.rgb(255, 246, 0);
        } else {
            return Color.rgb(49, 170, 53);
        }
    }

    private static WeatherCode getWeatherCode(@Nullable String icon) {
        if (icon == null) {
            return WeatherCode.CLEAR;
        }

        // Note: Météo France doesn't have icons for WIND
        if (icon.equals("p1") || icon.equals("p1j") || icon.equals("p1n")
                || icon.equals("p1bis") || icon.equals("p1bisj") || icon.equals("p1bisn")) {
            return WeatherCode.CLEAR;
        } else if (icon.equals("p2") || icon.equals("p2j") || icon.equals("p2n")
                || icon.equals("p2bis") || icon.equals("p2bisj") || icon.equals("p2bisn")) {
            return WeatherCode.PARTLY_CLOUDY;
        } else if (icon.equals("p3") || icon.equals("p3j") || icon.equals("p3n")
                || icon.equals("p3bis") || icon.equals("p3bisj") || icon.equals("p3bisn")) {
            return WeatherCode.CLOUDY;
        } else if (icon.equals("p4") || icon.equals("p4j") || icon.equals("p4n")
                || icon.equals("p5") || icon.equals("p5j") || icon.equals("p5n")
                || icon.equals("p5bis") || icon.equals("p5bisj") || icon.equals("p5bisn")) {
            return WeatherCode.HAZE;
        } else if (icon.equals("p6") || icon.equals("p6j") || icon.equals("p6n")
                || icon.equals("p6bis") || icon.equals("p6bisj") || icon.equals("p6bisn")
                || icon.equals("p6ter") || icon.equals("p6terj") || icon.equals("p6tern")
                || icon.equals("p7") || icon.equals("p7j") || icon.equals("p7n")
                || icon.equals("p7bis") || icon.equals("p7bisj") || icon.equals("p7bisn")
                || icon.equals("p8") || icon.equals("p8j") || icon.equals("p8n")
                || icon.equals("p8bis") || icon.equals("p8bisj") || icon.equals("p8bisn")) {
            return WeatherCode.FOG;
        } else if (icon.equals("p9") || icon.equals("p9j") || icon.equals("p9n")
                || icon.startsWith("p10") || icon.startsWith("p11") || icon.startsWith("p12")
                || icon.startsWith("p13") || icon.startsWith("p14")) { // We can start using "startsWith" when there are 2 digits
            return WeatherCode.RAIN;
        } else if (icon.startsWith("p16") || icon.startsWith("p24") || icon.startsWith("p25")) {
            return WeatherCode.THUNDERSTORM;
        } else if (icon.startsWith("p17") || icon.startsWith("p18")) {
            return WeatherCode.SLEET;
        } else if (icon.startsWith("p19") || icon.startsWith("p20")) {
            return WeatherCode.HAIL;
        } else if (icon.startsWith("p21") || icon.startsWith("p22") || icon.startsWith("p23")) {
            return WeatherCode.SNOW;
        } else if (icon.startsWith("p26") || icon.startsWith("p27") || icon.startsWith("p28")
                || icon.startsWith("p29")) {
            return WeatherCode.THUNDER;
        } else {
            return WeatherCode.CLEAR;
        }
    }

    private static float getHoursOfDay(@Nullable Date sunrise, @Nullable Date sunset) {
        if (sunrise == null || sunset == null) {
            return 0;
        }
        return (float) (
                (sunset.getTime() - sunrise.getTime()) // get delta millisecond.
                        / 1000 // second.
                        / 60 // minutes.
                        / 60.0 // hours.
        );
    }
}
