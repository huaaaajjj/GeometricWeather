package wangdaye.com.geometricweather.common.utils;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import wangdaye.com.geometricweather.common.basic.models.ChineseCity;

/**
 * File utils.
 * */

public class FileUtils {

    public static List<ChineseCity> readCityList(Context context) {
        return new Gson().fromJson(
                readAssetFileToString(context, "city_list.txt"),
                new TypeToken<List<ChineseCity>>() {}.getType()
        );
    }

    private static String readAssetFileToString(Context context, String fileName) {
        StringBuilder result = new StringBuilder();
        InputStreamReader inputReader = null;
        BufferedReader bufReader = null;
        try {
            // city_list.txt is UTF-8. Without saying so this decodes with the platform default,
            // which is UTF-8 on Android but the host charset under JVM unit tests — on a GBK
            // machine the whole table loads as mojibake and every name lookup misses (only the
            // coordinate scan still works, since the numbers are ASCII).
            inputReader = new InputStreamReader(
                    context.getResources().getAssets().open(fileName), StandardCharsets.UTF_8);
            bufReader = new BufferedReader(inputReader);
            String line;

            while ((line = bufReader.readLine()) != null) {
                result.append(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        closeIO(inputReader, bufReader);

        return result.toString();
    }

    private static void closeIO(Closeable... closeables) {
        if (closeables == null) return;
        try {
            for (Closeable closeable : closeables) {
                if (closeable != null) {
                    closeable.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
