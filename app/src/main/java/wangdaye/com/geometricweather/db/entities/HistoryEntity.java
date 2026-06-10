package wangdaye.com.geometricweather.db.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(tableName = "history")
public class HistoryEntity {

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

    @ColumnInfo(name = "daytimeTemperature")
    public int daytimeTemperature;

    @ColumnInfo(name = "nighttimeTemperature")
    public int nighttimeTemperature;

    public HistoryEntity() {
    }

    @Ignore
    public HistoryEntity(Long id, String cityId, String weatherSource, Date date,
                         long time, int daytimeTemperature, int nighttimeTemperature) {
        this.id = id;
        this.cityId = cityId;
        this.weatherSource = weatherSource;
        this.date = date;
        this.time = time;
        this.daytimeTemperature = daytimeTemperature;
        this.nighttimeTemperature = nighttimeTemperature;
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

    public int getDaytimeTemperature() {
        return this.daytimeTemperature;
    }

    public void setDaytimeTemperature(int daytimeTemperature) {
        this.daytimeTemperature = daytimeTemperature;
    }

    public int getNighttimeTemperature() {
        return this.nighttimeTemperature;
    }

    public void setNighttimeTemperature(int nighttimeTemperature) {
        this.nighttimeTemperature = nighttimeTemperature;
    }
}



