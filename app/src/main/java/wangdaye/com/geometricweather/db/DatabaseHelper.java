package wangdaye.com.geometricweather.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import wangdaye.com.geometricweather.common.basic.models.ChineseCity;
import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.weather.History;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.db.dao.WeatherDatabaseDao;
import wangdaye.com.geometricweather.db.entities.AlertEntity;
import wangdaye.com.geometricweather.db.entities.ChineseCityEntity;
import wangdaye.com.geometricweather.db.entities.DailyEntity;
import wangdaye.com.geometricweather.db.entities.HistoryEntity;
import wangdaye.com.geometricweather.db.entities.HourlyEntity;
import wangdaye.com.geometricweather.db.entities.LocationEntity;
import wangdaye.com.geometricweather.db.entities.MinutelyEntity;
import wangdaye.com.geometricweather.db.entities.WeatherEntity;
import wangdaye.com.geometricweather.db.generators.AlertEntityGenerator;
import wangdaye.com.geometricweather.db.generators.ChineseCityEntityGenerator;
import wangdaye.com.geometricweather.db.generators.DailyEntityGenerator;
import wangdaye.com.geometricweather.db.generators.HistoryEntityGenerator;
import wangdaye.com.geometricweather.db.generators.HourlyEntityGenerator;
import wangdaye.com.geometricweather.db.generators.LocationEntityGenerator;
import wangdaye.com.geometricweather.db.generators.MinutelyEntityGenerator;
import wangdaye.com.geometricweather.db.generators.WeatherEntityGenerator;
import wangdaye.com.geometricweather.common.utils.FileUtils;

public class DatabaseHelper {

    private static volatile DatabaseHelper sInstance;
    public static DatabaseHelper getInstance(Context c) {
        if (sInstance == null) {
            synchronized (DatabaseHelper.class) {
                sInstance = new DatabaseHelper(c);
            }
        }
        return sInstance;
    }

    private final WeatherDatabaseDao mDao;
    private final Object mWritingLock;

    private DatabaseHelper(Context c) {
        mDao = GeometricWeatherDatabase.getInstance(c).weatherDatabaseDao();
        mWritingLock = new Object();
    }

    // location.

    public void writeLocation(@NonNull Location location) {
        LocationEntity entity = LocationEntityGenerator.generate(location);
        synchronized (mWritingLock) {
            if (mDao.selectLocationByFormattedId(location.getFormattedId()) == null) {
                mDao.insertLocation(entity);
            } else {
                mDao.updateLocation(
                        location.getFormattedId(), entity.cityId, entity.latitude,
                        entity.longitude, entity.timeZone.getID(), entity.country,
                        entity.province, entity.city, entity.district,
                        entity.weatherSource.getId(), entity.currentPosition,
                        entity.residentPosition, entity.china);
            }
        }
    }

    public void writeLocationList(@NonNull List<Location> list) {
        synchronized (mWritingLock) {
            mDao.deleteAllLocation();
            mDao.insertLocationList(LocationEntityGenerator.generateEntityList(list));
        }
    }

    public void deleteLocation(@NonNull Location location) {
        mDao.deleteLocation(LocationEntityGenerator.generate(location));
    }

    @Nullable
    public Location readLocation(@NonNull Location location) {
        return readLocation(location.getFormattedId());
    }

    @Nullable
    public Location readLocation(@NonNull String formattedId) {
        LocationEntity entity = mDao.selectLocationByFormattedId(formattedId);
        return entity != null ? LocationEntityGenerator.generate(entity) : null;
    }

    @NonNull
    public List<Location> readLocationList() {
        List<LocationEntity> entityList = mDao.selectAllLocation();
        if (entityList.isEmpty()) {
            synchronized (mWritingLock) {
                if (mDao.countLocation() == 0) {
                    LocationEntity entity = LocationEntityGenerator.generate(Location.buildLocal());
                    mDao.insertLocation(entity);
                    entityList = new ArrayList<>();
                    entityList.add(entity);
                    return LocationEntityGenerator.generateModuleList(entityList);
                }
            }
        }
        return LocationEntityGenerator.generateModuleList(entityList);
    }

    public int countLocation() {
        return mDao.countLocation();
    }

    // weather.

    public void writeWeather(@NonNull Location location, @NonNull Weather weather) {
        deleteWeather(location);

        mDao.insertWeather(WeatherEntityGenerator.generate(location, weather));
        mDao.insertDailyList(DailyEntityGenerator.generate(
                location.getCityId(), location.getWeatherSource(), weather.getDailyForecast()));
        mDao.insertHourlyList(HourlyEntityGenerator.generateEntityList(
                location.getCityId(), location.getWeatherSource(), weather.getHourlyForecast()));
        mDao.insertMinutelyList(MinutelyEntityGenerator.generate(
                location.getCityId(), location.getWeatherSource(), weather.getMinutelyForecast()));
        mDao.insertAlertList(AlertEntityGenerator.generate(
                location.getCityId(), location.getWeatherSource(), weather.getAlertList()));
        mDao.insertHistory(HistoryEntityGenerator.generate(
                location.getCityId(), location.getWeatherSource(), weather));
        if (weather.getYesterday() != null) {
            mDao.insertHistory(HistoryEntityGenerator.generate(
                    location.getCityId(), location.getWeatherSource(), weather.getYesterday()));
        }
    }

    @Nullable
    public Weather readWeather(@NonNull Location location) {
        String cityId = location.getCityId();
        String sourceId = location.getWeatherSource().getId();

        WeatherEntity weatherEntity = mDao.selectWeatherByCityIdAndSource(cityId, sourceId);
        if (weatherEntity == null) return null;

        HistoryEntity historyEntity = null;
        if (weatherEntity.publishDate != null) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(weatherEntity.publishDate);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            java.util.Date yesterday = new java.util.Date(cal.getTimeInMillis() - 86400000);
            List<HistoryEntity> historyList = mDao.selectHistoryListByCityIdAndSource(cityId, sourceId);
            for (HistoryEntity h : historyList) {
                if (h.date != null && h.date.equals(yesterday)
                        || h.date != null && Math.abs(h.date.getTime() - yesterday.getTime()) < 86400000) {
                    historyEntity = h;
                    break;
                }
            }
        }

        List<DailyEntity> dailyList = mDao.selectDailyListByCityIdAndSource(cityId, sourceId);
        List<HourlyEntity> hourlyList = mDao.selectHourlyListByCityIdAndSource(cityId, sourceId);
        List<MinutelyEntity> minutelyList = mDao.selectMinutelyListByCityIdAndSource(cityId, sourceId);
        List<AlertEntity> alertList = mDao.selectAlertListByCityIdAndSource(cityId, sourceId);

        return WeatherEntityGenerator.generate(weatherEntity, historyEntity,
                dailyList, hourlyList, minutelyList, alertList);
    }

    public void deleteWeather(@NonNull Location location) {
        String cityId = location.getCityId();
        String source = location.getWeatherSource().getId();

        List<WeatherEntity> weatherList = mDao.selectWeatherListByCityIdAndSource(cityId, source);
        if (!weatherList.isEmpty()) mDao.deleteWeatherList(weatherList);

        List<HistoryEntity> historyList = mDao.selectHistoryListByCityIdAndSource(cityId, source);
        if (!historyList.isEmpty()) mDao.deleteHistoryList(historyList);

        List<DailyEntity> dailyList = mDao.selectDailyListByCityIdAndSource(cityId, source);
        if (!dailyList.isEmpty()) mDao.deleteDailyList(dailyList);

        List<HourlyEntity> hourlyList = mDao.selectHourlyListByCityIdAndSource(cityId, source);
        if (!hourlyList.isEmpty()) mDao.deleteHourlyList(hourlyList);

        List<MinutelyEntity> minutelyList = mDao.selectMinutelyListByCityIdAndSource(cityId, source);
        if (!minutelyList.isEmpty()) mDao.deleteMinutelyList(minutelyList);
    }

    // history.

    public History readHistory(@NonNull Location location, @NonNull Weather weather) {
        return HistoryEntityGenerator.generate(
                mDao.selectYesterdayHistory(
                        location.getCityId(),
                        location.getWeatherSource().getId(),
                        weather.getBase().getPublishDate(),
                        new java.util.Date(weather.getBase().getPublishDate().getTime() + 86400000)
                )
        );
    }

    // chinese city.

    public void ensureChineseCityList(Context context) {
        if (countChineseCity() < 3216) {
            synchronized (mWritingLock) {
                if (countChineseCity() < 3216) {
                    mDao.deleteAllChineseCity();
                    mDao.insertChineseCityList(
                            ChineseCityEntityGenerator.generateEntityList(
                                    FileUtils.readCityList(context)));
                }
            }
        }
    }

    @Nullable
    public ChineseCity readChineseCity(@NonNull String name) {
        ChineseCityEntity entity = mDao.selectChineseCityByName(name);
        return entity != null ? ChineseCityEntityGenerator.generate(entity) : null;
    }

    @Nullable
    public ChineseCity readChineseCity(@NonNull String province,
                                       @NonNull String city,
                                       @NonNull String district) {
        ChineseCityEntity entity = mDao.selectChineseCityByRegion(province, city, district);
        return entity != null ? ChineseCityEntityGenerator.generate(entity) : null;
    }

    @Nullable
    public ChineseCity readChineseCity(float latitude, float longitude) {
        List<ChineseCityEntity> all = mDao.selectAllChineseCity();
        if (all.isEmpty()) return null;
        ChineseCityEntity best = null;
        float bestDist = Float.MAX_VALUE;
        for (ChineseCityEntity c : all) {
            try {
                float lat = Float.parseFloat(c.latitude);
                float lon = Float.parseFloat(c.longitude);
                float dist = (lat - latitude) * (lat - latitude) + (lon - longitude) * (lon - longitude);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = c;
                }
            } catch (Exception ignored) {}
        }
        return best != null ? ChineseCityEntityGenerator.generate(best) : null;
    }

    @NonNull
    public List<ChineseCity> readChineseCityList(@NonNull String name) {
        return ChineseCityEntityGenerator.generateModuleList(
                mDao.selectChineseCityListByName(name));
    }

    public int countChineseCity() {
        return mDao.countChineseCity();
    }
}
