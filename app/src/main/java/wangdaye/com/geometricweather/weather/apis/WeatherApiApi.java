package wangdaye.com.geometricweather.weather.apis;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import wangdaye.com.geometricweather.weather.json.weatherapi.WeatherApiResult;

public interface WeatherApiApi {
    @GET("v1/forecast.json")
    Call<WeatherApiResult> getForecast(@Query("key") String key, @Query("q") String q, @Query("days") int days, @Query("aqi") String aqi, @Query("alerts") String alerts);
}
