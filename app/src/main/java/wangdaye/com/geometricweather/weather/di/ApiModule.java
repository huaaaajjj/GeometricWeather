package wangdaye.com.geometricweather.weather.di;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import wangdaye.com.geometricweather.BuildConfig;
import wangdaye.com.geometricweather.weather.apis.AccuWeatherApi;
import wangdaye.com.geometricweather.weather.apis.ApihzApi;
import wangdaye.com.geometricweather.weather.apis.AtmoAuraIqaApi;
import wangdaye.com.geometricweather.weather.apis.CaiYunApi;
import wangdaye.com.geometricweather.weather.apis.CmaApi;
import wangdaye.com.geometricweather.weather.apis.MetNoApi;
import wangdaye.com.geometricweather.weather.apis.MfWeatherApi;
import wangdaye.com.geometricweather.weather.apis.OpenMeteoAirQualityApi;
import wangdaye.com.geometricweather.weather.apis.OpenMeteoApi;
import wangdaye.com.geometricweather.weather.apis.OpenMeteoGeocodingApi;
import wangdaye.com.geometricweather.weather.apis.OwmApi;
import wangdaye.com.geometricweather.weather.apis.WeatherApiApi;
import wangdaye.com.geometricweather.weather.apis.XiaomiApi;

@InstallIn(SingletonComponent.class)
@Module
public class ApiModule {

    // api.met.no requires a UA that identifies the application and offers a way to reach its
    // author; a generic or absent one gets the client blocked.
    private static final String MET_NO_USER_AGENT = "GeometricWeather/" + BuildConfig.VERSION_NAME
            + " (github.com/WuZhengyang2024/GeometricWeather)";

    @Provides
    public AccuWeatherApi provideAccuWeatherApi(OkHttpClient client,
                                                GsonConverterFactory converterFactory) {
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.ACCU_WEATHER_BASE_URL)
                .client(client)
                .addConverterFactory(converterFactory)
                .build()
                .create((AccuWeatherApi.class));
    }

    @Provides
    public OwmApi provideOpenWeatherMapApi(OkHttpClient client,
                                           GsonConverterFactory converterFactory) {
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.OWM_BASE_URL)
                .client(client)
                .addConverterFactory(converterFactory)
                .build()
                .create((OwmApi.class));
    }

    @Provides
    public CaiYunApi provideCaiYunApi(OkHttpClient client,
                                      GsonConverterFactory converterFactory) {
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.CAIYUN_WEATHER_BASE_URL)
                .client(client)
                .addConverterFactory(converterFactory)
                .build()
                .create((CaiYunApi.class));
    }

    @Provides
    public MfWeatherApi provideMfWeatherApi(OkHttpClient client) {
        // Météo France stamps everything as UTC ISO-8601 ("2026-08-10T09:00:00.000Z"). The shared
        // Gson's "yyyy-MM-dd'T'HH:mm:ss" silently drops the trailing ".000Z" and reads the value as
        // device-local, which skews every time by the device's UTC offset. The 'X' pattern that
        // would handle this is only available from API 24, so parse UTC explicitly instead.
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Date.class, (JsonDeserializer<Date>) (json, type, ctx) -> {
                    String text = json.getAsString();
                    if (TextUtils.isEmpty(text)) {
                        return null;
                    }
                    for (String pattern : new String[]{
                            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'"}) {
                        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                        format.setTimeZone(TimeZone.getTimeZone("UTC"));
                        format.setLenient(false);
                        try {
                            return format.parse(text);
                        } catch (ParseException ignored) {
                            // try the next pattern
                        }
                    }
                    return null;
                })
                .create();
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.MF_WSFT_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create((MfWeatherApi.class));
    }

    @Provides
    public AtmoAuraIqaApi provideAtmoAuraIqaApi(OkHttpClient client,
                                                GsonConverterFactory converterFactory) {
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.IQA_ATMO_AURA_URL)
                .client(client)
                .addConverterFactory(converterFactory)
                .build()
                .create((AtmoAuraIqaApi.class));
    }

    @Provides
    public OpenMeteoApi provideOpenMeteoApi(OkHttpClient client,
                                            GsonConverterFactory converterFactory) {
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.OPEN_METEO_BASE_URL)
                .client(client)
                .addConverterFactory(converterFactory)
                .build()
                .create((OpenMeteoApi.class));
    }

    @Provides
    public OpenMeteoAirQualityApi provideOpenMeteoAirQualityApi(OkHttpClient client,
                                                                GsonConverterFactory converterFactory) {
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.OPEN_METEO_AIR_QUALITY_BASE_URL)
                .client(client)
                .addConverterFactory(converterFactory)
                .build()
                .create((OpenMeteoAirQualityApi.class));
    }

    @Provides
    public OpenMeteoGeocodingApi provideOpenMeteoGeocodingApi(OkHttpClient client,
                                                              GsonConverterFactory converterFactory) {
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.OPEN_METEO_GEOCODING_BASE_URL)
                .client(client)
                .addConverterFactory(converterFactory)
                .build()
                .create((OpenMeteoGeocodingApi.class));
    }

    @Provides
    public WeatherApiApi provideWeatherApiApi(OkHttpClient client,
                                              GsonConverterFactory converterFactory) {
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.WEATHERAPI_BASE_URL)
                .client(client)
                .addConverterFactory(converterFactory)
                .build()
                .create((WeatherApiApi.class));
    }

    @Provides
    public CmaApi provideCmaApi(OkHttpClient client,
                                GsonConverterFactory converterFactory) {
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.CMA_BASE_URL)
                .client(client)
                .addConverterFactory(converterFactory)
                .build()
                .create((CmaApi.class));
    }

    @Provides
    public ApihzApi provideApihzApi(OkHttpClient client,
                                    GsonConverterFactory converterFactory) {
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.APIHZ_BASE_URL)
                .client(client)
                .addConverterFactory(converterFactory)
                .build()
                .create((ApihzApi.class));
    }

    @Provides
    public MetNoApi provideMetNoApi(OkHttpClient client,
                                    GsonConverterFactory converterFactory) {
        // api.met.no's terms of service require an identifying User-Agent on every request and
        // block clients that send none, so this provider needs its own client. Only the header
        // differs; newBuilder() keeps the shared timeouts, connection pool and interceptors.
        OkHttpClient metNoClient = client.newBuilder()
                .addInterceptor(chain -> chain.proceed(
                        chain.request()
                                .newBuilder()
                                .header("User-Agent", MET_NO_USER_AGENT)
                                .build()))
                .build();
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.METNO_BASE_URL)
                .client(metNoClient)
                .addConverterFactory(converterFactory)
                .build()
                .create((MetNoApi.class));
    }

    @Provides
    public XiaomiApi provideXiaomiApi(OkHttpClient client,
                                      GsonConverterFactory converterFactory) {
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.XIAOMI_BASE_URL)
                .client(client)
                .addConverterFactory(converterFactory)
                .build()
                .create((XiaomiApi.class));
    }

}
