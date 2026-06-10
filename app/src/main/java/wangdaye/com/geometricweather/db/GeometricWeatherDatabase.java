package wangdaye.com.geometricweather.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import wangdaye.com.geometricweather.db.converters.RoomTypeConverters;
import wangdaye.com.geometricweather.db.dao.WeatherDatabaseDao;
import wangdaye.com.geometricweather.db.entities.AlertEntity;
import wangdaye.com.geometricweather.db.entities.ChineseCityEntity;
import wangdaye.com.geometricweather.db.entities.DailyEntity;
import wangdaye.com.geometricweather.db.entities.HistoryEntity;
import wangdaye.com.geometricweather.db.entities.HourlyEntity;
import wangdaye.com.geometricweather.db.entities.LocationEntity;
import wangdaye.com.geometricweather.db.entities.MinutelyEntity;
import wangdaye.com.geometricweather.db.entities.WeatherEntity;

@Database(
        entities = {
                ChineseCityEntity.class,
                LocationEntity.class,
                HistoryEntity.class,
                AlertEntity.class,
                MinutelyEntity.class,
                HourlyEntity.class,
                DailyEntity.class,
                WeatherEntity.class
        },
        version = 63,
        exportSchema = false
)
@TypeConverters(RoomTypeConverters.class)
public abstract class GeometricWeatherDatabase extends RoomDatabase {

    private static volatile GeometricWeatherDatabase sInstance;

    private static final String DATABASE_NAME = "Geometric_Weather_db";

    public abstract WeatherDatabaseDao weatherDatabaseDao();

    public static GeometricWeatherDatabase getInstance(@NonNull Context context) {
        if (sInstance == null) {
            synchronized (GeometricWeatherDatabase.class) {
                if (sInstance == null) {
                    sInstance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            GeometricWeatherDatabase.class,
                            DATABASE_NAME
                    ).fallbackToDestructiveMigration().build();
                }
            }
        }
        return sInstance;
    }
}
