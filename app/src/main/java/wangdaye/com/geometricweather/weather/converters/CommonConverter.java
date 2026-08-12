package wangdaye.com.geometricweather.weather.converters;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.util.Calendar;
import java.util.Date;

import wangdaye.com.geometricweather.R;
import wangdaye.com.geometricweather.common.basic.models.weather.AirQuality;
import wangdaye.com.geometricweather.common.basic.models.weather.Wind;

public class CommonConverter {

    public static String getWindLevel(Context c, double speed) {
        if (speed <= Wind.WIND_SPEED_0) {
            return c.getString(R.string.wind_0);
        } else if (speed <= Wind.WIND_SPEED_1) {
            return c.getString(R.string.wind_1);
        } else if (speed <= Wind.WIND_SPEED_2) {
            return c.getString(R.string.wind_2);
        } else if (speed <= Wind.WIND_SPEED_3) {
            return c.getString(R.string.wind_3);
        } else if (speed <= Wind.WIND_SPEED_4) {
            return c.getString(R.string.wind_4);
        } else if (speed <= Wind.WIND_SPEED_5) {
            return c.getString(R.string.wind_5);
        } else if (speed <= Wind.WIND_SPEED_6) {
            return c.getString(R.string.wind_6);
        } else if (speed <= Wind.WIND_SPEED_7) {
            return c.getString(R.string.wind_7);
        } else if (speed <= Wind.WIND_SPEED_8) {
            return c.getString(R.string.wind_8);
        } else if (speed <= Wind.WIND_SPEED_9) {
            return c.getString(R.string.wind_9);
        } else if (speed <= Wind.WIND_SPEED_10) {
            return c.getString(R.string.wind_10);
        } else if (speed <= Wind.WIND_SPEED_11) {
            return c.getString(R.string.wind_11);
        } else {
            return c.getString(R.string.wind_12);
        }
    }

    @Nullable
    public static String getAqiQuality(Context c, @Nullable Integer index) {
        if (index == null || index < 0) {
            return null;
        } if (index <= AirQuality.AQI_INDEX_1) {
            return c.getString(R.string.aqi_1);
        } else if (index <= AirQuality.AQI_INDEX_2) {
            return c.getString(R.string.aqi_2);
        } else if (index <= AirQuality.AQI_INDEX_3) {
            return c.getString(R.string.aqi_3);
        } else if (index <= AirQuality.AQI_INDEX_4) {
            return c.getString(R.string.aqi_4);
        } else if (index <= AirQuality.AQI_INDEX_5) {
            return c.getString(R.string.aqi_5);
        } else {
            return c.getString(R.string.aqi_6);
        }
    }

    // IAQI 分档与对应的 PM2.5 / PM10 浓度限值（HJ 633-2012）。浓度分段与 AirQuality
    // 的 getPm25Color / getPm10Color 同源，两者必须保持一致，否则同一张卡片上的
    // 「数字」和「颜色」会各说各话。
    private static final int[] AQI_LEVELS = {0, 50, 100, 150, 200, 300, 400, 500};
    private static final float[] PM25_LIMITS = {0, 35, 75, 115, 150, 250, 350, 500};
    private static final float[] PM10_LIMITS = {0, 50, 150, 250, 350, 420, 500, 600};

    /**
     * 由原始浓度换算中国 AQI。本 app 全程用中国标准（彩云取的是 aqi.chn 而非 usa，
     * 各 *Color 阈值也是 GB 3095-2012），所以拿到浓度的源一律走这里，而不是照抄
     * provider 自己的档位号 —— WeatherAPI 的 us-epa-index、OWM 的 main.aqi 都是
     * 1~6 的档位，直接写进 aqiIndex 会被当成 0~500 的 AQI 用，导致再脏的空气也
     * 落在第一档（≤50）而恒显示为绿色。
     *
     * 只用 PM2.5 / PM10 计算：中国 AQI 的这两项是 24h 均值，而气体项另有 1h/24h
     * 两套限值，接口给的是瞬时浓度，严格说对不上任何一套；国内首要污染物绝大多数
     * 时候也是这两项。其余气体照常填进 AirQuality 供分项颜色显示，但不参与 index。
     * 因此结果是指示性的，不等同官方发布值。
     */
    @Nullable
    public static Integer getAqiIndexFromConcentration(@Nullable Float pm25, @Nullable Float pm10) {
        Integer pm25Index = getIaqi(pm25, PM25_LIMITS);
        Integer pm10Index = getIaqi(pm10, PM10_LIMITS);
        if (pm25Index == null) {
            return pm10Index;
        }
        if (pm10Index == null) {
            return pm25Index;
        }
        return Math.max(pm25Index, pm10Index);
    }

    /** 单项污染物的 IAQI：在所属浓度段内线性插值。 */
    @Nullable
    private static Integer getIaqi(@Nullable Float concentration, float[] limits) {
        if (concentration == null || concentration < 0) {
            return null;
        }
        for (int i = 1; i < limits.length; i++) {
            if (concentration <= limits[i]) {
                return Math.round(
                        AQI_LEVELS[i - 1]
                                + (AQI_LEVELS[i] - AQI_LEVELS[i - 1])
                                * (concentration - limits[i - 1])
                                / (limits[i] - limits[i - 1])
                );
            }
        }
        // 超出最高浓度段，AQI 封顶。
        return AQI_LEVELS[AQI_LEVELS.length - 1];
    }

    /**
     * 浓度缺失时的兜底：WeatherAPI 的 us-epa-index（1~6）映射到该档中值。取中值而非
     * 上界，否则「良」会顶到 100，显得比实际严重。档界与中国 AQI 一致（50/100/150/
     * 200/300），故可直接借用。
     *
     * OWM 不需要同类兜底：它的 components 一旦存在，浓度字段就是基本类型必有值，
     * 档位号那条路走不到。
     */
    @Nullable
    public static Integer getAqiIndexFromUsEpaCategory(@Nullable Integer category) {
        int[] levels = {25, 75, 125, 175, 250, 400};
        if (category == null || category < 1 || category > levels.length) {
            return null;
        }
        return levels[category - 1];
    }

    @Nullable
    public static Integer getMoonPhaseAngle(@Nullable String phase) {
        if (TextUtils.isEmpty(phase)) {
            return null;
        }
        switch (phase.toLowerCase()) {
            case "waxingcrescent":
            case "waxing crescent":
                return 45;

            case "first":
            case "firstquarter":
            case "first quarter":
                return 90;

            case "waxinggibbous":
            case "waxing gibbous":
                return 135;

            case "full":
            case "fullmoon":
            case "full moon":
                return 180;

            case "waninggibbous":
            case "waning gibbous":
                return 225;

            case "third":
            case "thirdquarter":
            case "third quarter":
            case "last":
            case "lastquarter":
            case "last quarter":
                return 270;

            case "waningcrescent":
            case "waning crescent":
                return 315;

            default:
                return 360;
        }
    }

    public static boolean isDaylight(Date sunrise, Date sunset, Date current) {
        Calendar calendar = Calendar.getInstance();

        calendar.setTime(sunrise);
        int sunriseTime = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);

        calendar.setTime(sunset);
        int sunsetTime = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);

        calendar.setTime(current);
        int currentTime = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);

        return sunriseTime < currentTime && currentTime < sunsetTime;
    }
}
