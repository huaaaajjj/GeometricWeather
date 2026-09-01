package wangdaye.com.geometricweather.weather.apis;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import wangdaye.com.geometricweather.weather.json.openmeteo.OpenMeteoGeocodingResult;

/**
 * Place search. Lives on its own host (geocoding-api.open-meteo.com), hence a third Retrofit
 * instance next to {@link OpenMeteoApi} and {@link OpenMeteoAirQualityApi}.
 *
 * It belongs to no weather source: the search screen asks it where a name is, and the selected
 * source then supplies the weather for that coordinate.
 */
public interface OpenMeteoGeocodingApi {
    @GET("v1/search")
    Call<OpenMeteoGeocodingResult> getLocations(
            @Query("name") String name,
            @Query("count") int count,
            @Query("language") String language,
            @Query("format") String format);
}
