package wangdaye.com.geometricweather.weather.apis;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import wangdaye.com.geometricweather.weather.json.atmoaura.AtmoAuraQAResult;

public interface AtmoAuraIqaApi {
    @GET("api/v1/iqa/full")
    Call<AtmoAuraQAResult> getQAFull(@Query("api_token") String api_token, @Query("lat") double lat, @Query("lon") double lon);
}
