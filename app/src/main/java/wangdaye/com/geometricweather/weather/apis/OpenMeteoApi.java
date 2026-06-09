package wangdaye.com.geometricweather.weather.apis;

import io.reactivex.Observable;
import retrofit2.http.GET;
import retrofit2.http.Query;
import wangdaye.com.geometricweather.weather.json.openmeteo.OpenMeteoResult;

/**
 * Open-Meteo API.
 * Free, no API key required.
 * https://open-meteo.com/en/docs
 */

public interface OpenMeteoApi {

    @GET("v1/forecast")
    Observable<OpenMeteoResult> getForecast(
            @Query("latitude") double latitude,
            @Query("longitude") double longitude,
            @Query("current") String current,
            @Query("hourly") String hourly,
            @Query("daily") String daily,
            @Query("timezone") String timezone,
            @Query("forecast_days") int forecastDays,
            @Query("past_days") int pastDays
    );
}
