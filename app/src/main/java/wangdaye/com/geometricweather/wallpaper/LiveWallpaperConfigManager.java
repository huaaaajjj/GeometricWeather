package wangdaye.com.geometricweather.wallpaper;

import android.content.Context;

import wangdaye.com.geometricweather.settings.ConfigStore;

public class LiveWallpaperConfigManager {

    private final String mWeatherKind;
    private final String mDayNightType;
    private final String mFrameRate;

    private static final String SP_LIVE_WALLPAPER_CONFIG = "live_wallpaper_config";
    private static final String KEY_WEATHER_KIND = "weather_kind";
    private static final String KEY_DAY_NIGHT_TYPE = "day_night_type";
    private static final String KEY_FRAME_RATE = "frame_rate";

    private LiveWallpaperConfigManager(Context context) {
        ConfigStore config = ConfigStore.getInstance(context, SP_LIVE_WALLPAPER_CONFIG);
        mWeatherKind = config.getString(KEY_WEATHER_KIND, "auto");
        mDayNightType = config.getString(KEY_DAY_NIGHT_TYPE, "auto");
        // Empty = follow the panel's max refresh rate. Otherwise a target fps ("30".."144").
        mFrameRate = config.getString(KEY_FRAME_RATE, "");
    }

    public static LiveWallpaperConfigManager getInstance(Context context) {
        return new LiveWallpaperConfigManager(context);
    }

    public String getWeatherKind() {
        return mWeatherKind;
    }

    public String getDayNightType() {
        return mDayNightType;
    }

    public String getFrameRate() {
        return mFrameRate;
    }

    public static void update(Context context, String weatherKind, String dayNightType,
                              String frameRate) {
        ConfigStore.getInstance(context, SP_LIVE_WALLPAPER_CONFIG)
                .edit()
                .putString(KEY_WEATHER_KIND, weatherKind)
                .putString(KEY_DAY_NIGHT_TYPE, dayNightType)
                .putString(KEY_FRAME_RATE, frameRate)
                .apply();
    }
}
