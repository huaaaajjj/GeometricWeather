package wangdaye.com.geometricweather.weather.apis;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import wangdaye.com.geometricweather.weather.json.owm.*;
import java.util.List;

public interface OwmApi {
    // Geocoding API returns a flat JSON array of {name, lat, lon, country}, which is what
    // OwmLocationResult models. data/2.5/find returns {"list":[...]} instead, so decoding it as
    // List<OwmLocationResult> always threw JsonSyntaxException and city search silently came back empty.
    @GET("geo/1.0/direct")
    Call<List<OwmLocationResult>> callWeatherLocation(@Query("appid") String apikey, @Query("q") String q, @Query("limit") int limit);

    @GET("geo/1.0/reverse")
    Call<List<OwmLocationResult>> getWeatherLocationByGeoPosition(@Query("appid") String apikey, @Query("lat") double lat, @Query("lon") double lon, @Query("limit") int limit);

    @GET("data/2.5/weather")
    Call<OwmCurrentResult> getCurrentWeather(@Query("appid") String apikey, @Query("lat") double lat, @Query("lon") double lon, @Query("units") String units, @Query("lang") String lang);

    @GET("data/2.5/forecast")
    Call<OwmForecastResult> getForecast(@Query("appid") String apikey, @Query("lat") double lat, @Query("lon") double lon, @Query("units") String units, @Query("lang") String lang, @Query("cnt") int count);

    @GET("data/2.5/air_pollution")
    Call<OwmAirPollutionResult> getAirPollutionCurrent(@Query("appid") String apikey, @Query("lat") double lat, @Query("lon") double lon);

    @GET("data/2.5/air_pollution/forecast")
    Call<OwmAirPollutionResult> getAirPollutionForecast(@Query("appid") String apikey, @Query("lat") double lat, @Query("lon") double lon);
}
