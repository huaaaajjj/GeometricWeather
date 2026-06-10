package wangdaye.com.geometricweather.location.services.ip;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface BaiduIPLocationApi {
    @GET("location/ip")
    Call<BaiduIPLocationResult> getLocation(@Query("ak") String ak, @Query("coor") String coor);
}
