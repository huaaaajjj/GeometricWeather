package wangdaye.com.geometricweather.db.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.Date;
import java.util.List;

import wangdaye.com.geometricweather.db.entities.AlertEntity;
import wangdaye.com.geometricweather.db.entities.ChineseCityEntity;
import wangdaye.com.geometricweather.db.entities.DailyEntity;
import wangdaye.com.geometricweather.db.entities.HistoryEntity;
import wangdaye.com.geometricweather.db.entities.HourlyEntity;
import wangdaye.com.geometricweather.db.entities.LocationEntity;
import wangdaye.com.geometricweather.db.entities.MinutelyEntity;
import wangdaye.com.geometricweather.db.entities.WeatherEntity;

@Dao
public interface WeatherDatabaseDao {

    // ---------- ChineseCity ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertChineseCityList(List<ChineseCityEntity> entityList);

    @Query("DELETE FROM chinese_city")
    void deleteAllChineseCity();

    @Query("SELECT * FROM chinese_city WHERE district = :name OR city = :name LIMIT 1")
    ChineseCityEntity selectChineseCityByName(String name);

    @Query("SELECT * FROM chinese_city WHERE district = :district AND city = :city " +
           "OR district = :district AND province = :province " +
           "OR city = :city AND province = :province " +
           "OR city = :city " +
           "OR district = :city AND province = :province " +
           "OR district = :city AND city = :province " +
           "OR district = :city " +
           "OR city = :district LIMIT 1")
    ChineseCityEntity selectChineseCityByRegion(String province, String city, String district);

    @Query("SELECT * FROM chinese_city")
    List<ChineseCityEntity> selectAllChineseCity();

    @Query("SELECT * FROM chinese_city WHERE district LIKE '%' || :name || '%' " +
           "OR city LIKE '%' || :name || '%' " +
           "OR province LIKE '%' || :name || '%'")
    List<ChineseCityEntity> selectChineseCityListByName(String name);

    @Query("SELECT COUNT(*) FROM chinese_city")
    int countChineseCity();

    // ---------- Location ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertLocation(LocationEntity entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertLocationList(List<LocationEntity> entityList);

    @Delete
    void deleteLocation(LocationEntity entity);

    @Query("DELETE FROM location")
    void deleteAllLocation();

    @Query("UPDATE location SET cityId = :cityId, latitude = :latitude, " +
           "longitude = :longitude, timeZone = :timeZone, country = :country, " +
           "province = :province, city = :city, district = :district, " +
           "weatherSource = :weatherSource, currentPosition = :currentPosition, " +
           "residentPosition = :residentPosition, china = :china " +
           "WHERE formattedId = :formattedId")
    void updateLocation(String formattedId, String cityId, float latitude,
                        float longitude, String timeZone, String country,
                        String province, String city, String district,
                        String weatherSource, boolean currentPosition,
                        boolean residentPosition, boolean china);

    @Query("SELECT * FROM location WHERE formattedId = :formattedId LIMIT 1")
    LocationEntity selectLocationByFormattedId(String formattedId);

    @Query("SELECT * FROM location")
    List<LocationEntity> selectAllLocation();

    @Query("SELECT COUNT(*) FROM location")
    int countLocation();

    // ---------- Weather ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertWeather(WeatherEntity entity);

    @Delete
    void deleteWeatherList(List<WeatherEntity> entityList);

    @Query("SELECT * FROM weather WHERE cityId = :cityId AND weatherSource = :weatherSource LIMIT 1")
    WeatherEntity selectWeatherByCityIdAndSource(String cityId, String weatherSource);

    @Query("SELECT * FROM weather WHERE cityId = :cityId AND weatherSource = :weatherSource")
    List<WeatherEntity> selectWeatherListByCityIdAndSource(String cityId, String weatherSource);

    // ---------- History ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertHistory(HistoryEntity entity);

    @Delete
    void deleteHistoryList(List<HistoryEntity> entityList);

    @Query("SELECT * FROM history WHERE date >= :yesterday AND date < :today " +
           "AND cityId = :cityId AND weatherSource = :weatherSource LIMIT 1")
    HistoryEntity selectYesterdayHistory(String cityId, String weatherSource,
                                         Date yesterday, Date today);

    @Query("SELECT * FROM history WHERE cityId = :cityId AND weatherSource = :weatherSource")
    List<HistoryEntity> selectHistoryListByCityIdAndSource(String cityId, String weatherSource);

    // ---------- Daily ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertDailyList(List<DailyEntity> entityList);

    @Delete
    void deleteDailyList(List<DailyEntity> entityList);

    @Query("SELECT * FROM daily WHERE cityId = :cityId AND weatherSource = :weatherSource ORDER BY date ASC")
    List<DailyEntity> selectDailyListByCityIdAndSource(String cityId, String weatherSource);

    @Query("DELETE FROM daily WHERE cityId = :cityId AND weatherSource = :weatherSource")
    void deleteDailyByCityIdAndSource(String cityId, String weatherSource);

    // ---------- Hourly ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertHourlyList(List<HourlyEntity> entityList);

    @Delete
    void deleteHourlyList(List<HourlyEntity> entityList);

    @Query("SELECT * FROM hourly WHERE cityId = :cityId AND weatherSource = :weatherSource ORDER BY date ASC")
    List<HourlyEntity> selectHourlyListByCityIdAndSource(String cityId, String weatherSource);

    @Query("DELETE FROM hourly WHERE cityId = :cityId AND weatherSource = :weatherSource")
    void deleteHourlyByCityIdAndSource(String cityId, String weatherSource);

    // ---------- Minutely ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMinutelyList(List<MinutelyEntity> entityList);

    @Delete
    void deleteMinutelyList(List<MinutelyEntity> entityList);

    @Query("SELECT * FROM minutely WHERE cityId = :cityId AND weatherSource = :weatherSource ORDER BY date ASC")
    List<MinutelyEntity> selectMinutelyListByCityIdAndSource(String cityId, String weatherSource);

    @Query("DELETE FROM minutely WHERE cityId = :cityId AND weatherSource = :weatherSource")
    void deleteMinutelyByCityIdAndSource(String cityId, String weatherSource);

    // ---------- Alert ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAlertList(List<AlertEntity> entityList);

    @Delete
    void deleteAlertList(List<AlertEntity> entityList);

    @Query("SELECT * FROM alert WHERE cityId = :cityId AND weatherSource = :weatherSource ORDER BY date ASC")
    List<AlertEntity> selectAlertListByCityIdAndSource(String cityId, String weatherSource);

    @Query("DELETE FROM alert WHERE cityId = :cityId AND weatherSource = :weatherSource")
    void deleteAlertByCityIdAndSource(String cityId, String weatherSource);
}
