package wangdaye.com.geometricweather.weather.apis;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import wangdaye.com.geometricweather.weather.json.mf.*;
import java.util.List;

public interface MfWeatherApi {
    // Serves both weather and location resolution: the response carries insee/timezone/department
    // alongside the forecast.
    @GET("v2/forecast")
    Call<MfForecastV2Result> getForecast(@Query("lat") double lat, @Query("lon") double lon, @Query("lang") String lang, @Query("token") String token);

    @GET("v2/observation")
    Call<MfCurrentResult> getCurrent(@Query("lat") double lat, @Query("lon") double lon, @Query("lang") String lang, @Query("token") String token);

    @GET("v3/nowcast/rain")
    Call<MfRainResult> getRain(@Query("lat") double lat, @Query("lon") double lon, @Query("lang") String lang, @Query("token") String token);

    @GET("v2/ephemeris")
    Call<MfEphemerisResult> getEphemeris(@Query("lat") double lat, @Query("lon") double lon, @Query("lang") String lang, @Query("token") String token);

    // domain is the French department number ("75"). /v3/warnings 404s with "You haven't access to
    // this url" — the served path is /v3/warning/full.
    @GET("v3/warning/full")
    Call<MfWarningsResult> getWarnings(@Query("domain") String domain, @Query("formatDate") String formatDate, @Query("token") String token);
}
