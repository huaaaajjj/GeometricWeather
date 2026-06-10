package wangdaye.com.geometricweather.weather.apis;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;
import wangdaye.com.geometricweather.weather.json.caiyun.CaiYunWeatherResult;

public interface CaiYunApi {
    @GET("v2.6/{token}/{lon},{lat}/weather")
    Call<CaiYunWeatherResult> getWeather(@Path("token") String token, @Path("lon") String longitude, @Path("lat") String latitude, @Query("alert") boolean alert);
}
