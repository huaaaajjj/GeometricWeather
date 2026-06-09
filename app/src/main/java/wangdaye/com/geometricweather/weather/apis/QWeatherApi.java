package wangdaye.com.geometricweather.weather.apis;

import io.reactivex.Observable;
import retrofit2.http.GET;
import retrofit2.http.Query;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherAirResult;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherDailyResult;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherHourlyResult;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherMinutelyResult;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherNowResult;
import wangdaye.com.geometricweather.weather.json.qweather.QWeatherWarningResult;

/**
 * QWeather (和风天气) API.
 * https://dev.qweather.com/docs/api/
 */

public interface QWeatherApi {

    @GET("v7/weather/now")
    Observable<QWeatherNowResult> getWeatherNow(
            @Query("location") String location,
            @Query("key") String key
    );

    @GET("v7/weather/3d")
    Observable<QWeatherDailyResult> getWeather3d(
            @Query("location") String location,
            @Query("key") String key
    );

    @GET("v7/weather/7d")
    Observable<QWeatherDailyResult> getWeather7d(
            @Query("location") String location,
            @Query("key") String key
    );

    @GET("v7/weather/24h")
    Observable<QWeatherHourlyResult> getWeather24h(
            @Query("location") String location,
            @Query("key") String key
    );

    @GET("v7/minutely/5m")
    Observable<QWeatherMinutelyResult> getMinutely(
            @Query("location") String location,
            @Query("key") String key
    );

    @GET("v7/air/now")
    Observable<QWeatherAirResult> getAirNow(
            @Query("location") String location,
            @Query("key") String key
    );

    @GET("v7/warning/now")
    Observable<QWeatherWarningResult> getWarningNow(
            @Query("location") String location,
            @Query("key") String key
    );

    @GET("v2/city/lookup")
    Observable<QWeatherLocationResult> lookupLocation(
            @Query("location") String location,
            @Query("key") String key
    );
}
