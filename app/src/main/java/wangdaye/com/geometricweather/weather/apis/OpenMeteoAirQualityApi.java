package wangdaye.com.geometricweather.weather.apis;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import wangdaye.com.geometricweather.weather.json.openmeteo.OpenMeteoAirQualityResult;

/**
 * Air quality lives on its own host (air-quality-api.open-meteo.com), hence a second Retrofit
 * instance next to {@link OpenMeteoApi}.
 */
public interface OpenMeteoAirQualityApi {
    @GET("v1/air-quality")
    Call<OpenMeteoAirQualityResult> getAirQuality(
            @Query("latitude") double lat,
            @Query("longitude") double lon,
            @Query("current") String current,
            @Query("hourly") String hourly,
            @Query("timezone") String timezone,
            @Query("forecast_days") int forecastDays);
}
