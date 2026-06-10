package wangdaye.com.geometricweather.weather.apis;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;
import wangdaye.com.geometricweather.weather.json.mf.*;
import java.util.List;

public interface MfWeatherApi {
    @GET("v2/forecast")
    Call<List<MfLocationResult>> callWeatherLocation(@Query("q") String q, @Query("lat") double lat, @Query("lon") double lon, @Query("token") String token);

    @GET("v2/forecast")
    Call<List<MfLocationResult>> getWeatherLocation(@Query("q") String q, @Query("lat") double lat, @Query("lon") double lon, @Query("token") String token);

    @GET("v2/forecast")
    Call<MfForecastResult> getForecast(@Query("lat") double lat, @Query("lon") double lon, @Query("lang") String lang, @Query("token") String token);

    @GET("v2/forecast")
    Call<MfForecastV2Result> getForecastV2(@Query("lat") double lat, @Query("lon") double lon, @Query("lang") String lang, @Query("token") String token);

    @GET("v2/forecast")
    Call<MfForecastResult> getForecastInstants(@Query("lat") double lat, @Query("lon") double lon, @Query("lang") String lang, @Query("instants") int instants, @Query("token") String token);

    @GET("v2/forecast/site/{id}")
    Call<MfForecastResult> getForecastInseepp(@Path("id") String id, @Query("lang") String lang, @Query("token") String token);

    @GET("v2/observation")
    Call<MfCurrentResult> getCurrent(@Query("lat") double lat, @Query("lon") double lon, @Query("lang") String lang, @Query("token") String token);

    @GET("v3/nowcast/rain")
    Call<MfRainResult> getRain(@Query("lat") double lat, @Query("lon") double lon, @Query("lang") String lang, @Query("token") String token);

    @GET("v2/ephemeris")
    Call<MfEphemerisResult> getEphemeris(@Query("lat") double lat, @Query("lon") double lon, @Query("lang") String lang, @Query("token") String token);

    @GET("v3/warnings")
    Call<MfWarningsResult> getWarnings(@Query("domain") String domain, @Query("formatDate") String formatDate, @Query("token") String token);
}
