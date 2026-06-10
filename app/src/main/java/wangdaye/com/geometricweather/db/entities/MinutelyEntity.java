package wangdaye.com.geometricweather.db.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.Date;

import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode;

@Entity(tableName = "minutely")
public class MinutelyEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public Long id;

    @ColumnInfo(name = "cityId")
    public String cityId;

    @ColumnInfo(name = "weatherSource")
    public String weatherSource;

    @ColumnInfo(name = "date")
    public Date date;

    @ColumnInfo(name = "time")
    public long time;

    @ColumnInfo(name = "daylight")
    public boolean daylight;

    @ColumnInfo(name = "weatherText")
    public String weatherText;

    @ColumnInfo(name = "weatherCode")
    public WeatherCode weatherCode;

    @ColumnInfo(name = "minuteInterval")
    public int minuteInterval;

    @ColumnInfo(name = "dbz")
    public Integer dbz;

    @ColumnInfo(name = "cloudCover")
    public Integer cloudCover;

    public MinutelyEntity() {
    }

    @Ignore
    public MinutelyEntity(Long id, String cityId, String weatherSource, Date date,
                          long time, boolean daylight, String weatherText,
                          WeatherCode weatherCode, int minuteInterval, Integer dbz,
                          Integer cloudCover) {
        this.id = id;
        this.cityId = cityId;
        this.weatherSource = weatherSource;
        this.date = date;
        this.time = time;
        this.daylight = daylight;
        this.weatherText = weatherText;
        this.weatherCode = weatherCode;
        this.minuteInterval = minuteInterval;
        this.dbz = dbz;
        this.cloudCover = cloudCover;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCityId() {
        return this.cityId;
    }

    public void setCityId(String cityId) {
        this.cityId = cityId;
    }

    public String getWeatherSource() {
        return this.weatherSource;
    }

    public void setWeatherSource(String weatherSource) {
        this.weatherSource = weatherSource;
    }

    public Date getDate() {
        return this.date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public long getTime() {
        return this.time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public boolean getDaylight() {
        return this.daylight;
    }

    public void setDaylight(boolean daylight) {
        this.daylight = daylight;
    }

    public String getWeatherText() {
        return this.weatherText;
    }

    public void setWeatherText(String weatherText) {
        this.weatherText = weatherText;
    }

    public WeatherCode getWeatherCode() {
        return this.weatherCode;
    }

    public void setWeatherCode(WeatherCode weatherCode) {
        this.weatherCode = weatherCode;
    }

    public int getMinuteInterval() {
        return this.minuteInterval;
    }

    public void setMinuteInterval(int minuteInterval) {
        this.minuteInterval = minuteInterval;
    }

    public Integer getDbz() {
        return this.dbz;
    }

    public void setDbz(Integer dbz) {
        this.dbz = dbz;
    }

    public Integer getCloudCover() {
        return this.cloudCover;
    }

    public void setCloudCover(Integer cloudCover) {
        this.cloudCover = cloudCover;
    }
}



