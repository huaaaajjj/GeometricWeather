package wangdaye.com.geometricweather.common.retrofit.interceptors;

import android.util.Base64;

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

        if (!url.host().contains("caiyunapp.com")) {
            return chain.proceed(original);
        }

        String path = url.encodedPath();
        String nonce = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis() / 1000;

        TreeMap<String, String> sortedQuery = new TreeMap<>();
        for (String name : url.queryParameterNames()) {
            sortedQuery.put(name, url.queryParameter(name));
        }

        StringBuilder queryStr = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : sortedQuery.entrySet()) {
            if (!first) queryStr.append("&");
            queryStr.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            queryStr.append("=");
            queryStr.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            first = false;
        }

        String stringToSign = String.join(":",
                "GET",
                path,
                queryStr.toString(),
                appKey,
                nonce,
                String.valueOf(timestamp)
        );

        String signature;
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = hmac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            signature = Base64.encodeToString(hash, Base64.URL_SAFE | Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IOException("Failed to compute CaiYun signature", e);
        }

        Request signed = original.newBuilder()
                .header("x-cy-nonce", nonce)
                .header("x-cy-timestamp", String.valueOf(timestamp))
                .header("x-cy-signature", signature)
                .build();

        return chain.proceed(signed);
    }
}
