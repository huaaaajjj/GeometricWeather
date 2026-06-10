package wangdaye.com.geometricweather.weather.apis;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import wangdaye.com.geometricweather.weather.json.openmeteo.OpenMeteoResult;

public interface OpenMeteoApi {
    @GET("v1/forecast")
    Call<OpenMeteoResult> getForecast(@Query("latitude") double lat, @Query("longitude") double lon, @Query("current") String current, @Query("hourly") String hourly, @Query("daily") String daily, @Query("timezone") String timezone, @Query("forecast_days") int forecastDays, @Query("past_days") int pastDays);
}
