package wangdaye.com.geometricweather.common.retrofit.interceptors;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class CaiYunSignatureInterceptor implements Interceptor {

    private static final String TAG = "CaiYunInterceptor";

    private final String appKey;
    private final String appSecret;

    public CaiYunSignatureInterceptor(String appKey, String appSecret) {
        this.appKey = appKey;
        this.appSecret = appSecret;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        HttpUrl url = original.url();

        if (url.host() == null || !url.host().contains("caiyunapp.com")) {
            return chain.proceed(original);
        }

        if (appKey == null || appSecret == null) {
            return chain.proceed(original);
        }

        try {
            String path = url.encodedPath();
            if (path == null) path = "/";
            String nonce = UUID.randomUUID().toString();
            long timestamp = System.currentTimeMillis() / 1000;

            TreeMap<String, String> sortedQuery = new TreeMap<>();
            for (String name : url.queryParameterNames()) {
                if (name == null) continue;
                String value = url.queryParameter(name);
                if (value != null) {
                    sortedQuery.put(name, value);
                }
            }

            StringBuilder queryStr = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, String> entry : sortedQuery.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null) continue;
                if (!first) queryStr.append("&");
                queryStr.append(URLEncoder.encode(key, "UTF-8"));
                queryStr.append("=");
                queryStr.append(URLEncoder.encode(val, "UTF-8"));
                first = false;
            }

            String stringToSign = "GET" + ":" + path + ":" + queryStr.toString()
                    + ":" + appKey + ":" + nonce + ":" + timestamp;

            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = hmac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.encodeToString(hash, Base64.URL_SAFE | Base64.NO_WRAP);

            Request signed = original.newBuilder()
                    .header("x-cy-nonce", nonce)
                    .header("x-cy-timestamp", String.valueOf(timestamp))
                    .header("x-cy-signature", signature)
                    .build();

            return chain.proceed(signed);
        } catch (Exception e) {
            Log.e(TAG, "Signature generation failed", e);
            return chain.proceed(original);
        }
    }
}
