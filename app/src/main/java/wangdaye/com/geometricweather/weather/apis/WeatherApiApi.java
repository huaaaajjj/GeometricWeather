package wangdaye.com.geometricweather.weather.apis;

import io.reactivex.Observable;
import retrofit2.http.GET;
import retrofit2.http.Query;
import wangdaye.com.geometricweather.weather.json.weatherapi.WeatherApiResult;

/**
 * WeatherAPI.com API.
 * https://www.weatherapi.com/docs/
 */

public interface WeatherApiApi {

    @GET("v1/forecast.json")
    Observable<WeatherApiResult> getForecast(
            @Query("key") String key,
            @Query("q") String query,
            @Query("days") int days,
            @Query("aqi") String aqi,
            @Query("alerts") String alerts
    );
}
