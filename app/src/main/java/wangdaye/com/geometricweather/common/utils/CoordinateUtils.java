package wangdaye.com.geometricweather.common.utils;

/**
 * Coordinate system conversion utilities.
 *
 * WGS-84: World Geodetic System (GPS, international standard)
 * GCJ-02: Chinese national standard (Mars coordinates, used by AMap/Tencent Maps)
 *
 * Rules:
 * - Weather APIs must use WGS-84
 * - Maps (AMap, Tencent) must use GCJ-02
 * - GPS/LocationManager returns WGS-84
 * - AMap SDK returns GCJ-02
 * - Baidu IP API returns GCJ-02
 */
public class CoordinateUtils {

    private static final double PI = Math.PI;
    private static final double A = 6378245.0; // Semi-major axis
    private static final double EE = 0.00669342162296594323; // Eccentricity squared

    /**
     * Check if coordinates are in China (rough bounding box).
     * Coordinates outside China don't need conversion.
     */
    public static boolean isInChina(double latitude, double longitude) {
        return latitude >= 0.8293 && latitude <= 55.8271
                && longitude >= 72.004 && longitude <= 137.8347;
    }

    /**
     * Check if coordinates need conversion from GCJ-02 to WGS-84.
     * If the offset is small (< 1 meter), skip conversion.
     */
    public static boolean needsGcj02ToWgs84(double latitude, double longitude) {
        if (!isInChina(latitude, longitude)) {
            return false;
        }
        double[] wgs84 = gcj02ToWgs84(latitude, longitude);
        double latDiff = Math.abs(wgs84[0] - latitude);
        double lonDiff = Math.abs(wgs84[1] - longitude);
        return latDiff > 0.00001 || lonDiff > 0.00001; // ~1 meter
    }

    /**
     * Convert GCJ-02 to WGS-84.
     *
     * @param gcjLat GCJ-02 latitude
     * @param gcjLon GCJ-02 longitude
     * @return WGS-84 coordinates [latitude, longitude]
     */
    public static double[] gcj02ToWgs84(double gcjLat, double gcjLon) {
        double[] delta = delta(gcjLat, gcjLon);
        return new double[]{
                gcjLat - delta[0],
                gcjLon - delta[1]
        };
    }

    /**
     * Convert WGS-84 to GCJ-02.
     *
     * @param wgsLat WGS-84 latitude
     * @param wgsLon WGS-84 longitude
     * @return GCJ-02 coordinates [latitude, longitude]
     */
    public static double[] wgs84ToGcj02(double wgsLat, double wgsLon) {
        if (!isInChina(wgsLat, wgsLon)) {
            return new double[]{wgsLat, wgsLon};
        }
        double[] delta = delta(wgsLat, wgsLon);
        return new double[]{
                wgsLat + delta[0],
                wgsLon + delta[1]
        };
    }

    /**
     * Calculate offset between WGS-84 and GCJ-02.
     */
    private static double[] delta(double lat, double lon) {
        double dLat = transformLat(lon - 105.0, lat - 35.0);
        double dLon = transformLon(lon - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLon = (dLon * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);
        return new double[]{dLat, dLon};
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLon(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }
}
