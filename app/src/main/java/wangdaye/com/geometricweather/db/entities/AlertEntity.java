package wangdaye.com.geometricweather.db.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(tableName = "alert")
public class AlertEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public Long id;

    @ColumnInfo(name = "cityId")
    public String cityId;

    @ColumnInfo(name = "weatherSource")
    public String weatherSource;

    @ColumnInfo(name = "alertId")
    public long alertId;

    @ColumnInfo(name = "date")
    public Date date;

    @ColumnInfo(name = "time")
    public long time;

    @ColumnInfo(name = "description")
    public String description;

    @ColumnInfo(name = "content")
    public String content;

    @ColumnInfo(name = "type")
    public String type;

    @ColumnInfo(name = "priority")
    public int priority;

    @ColumnInfo(name = "color")
    public int color;

    public AlertEntity() {
    }

    @Ignore
    public AlertEntity(Long id, String cityId, String weatherSource, long alertId,
                       Date date, long time, String description, String content,
                       String type, int priority, int color) {
        this.id = id;
        this.cityId = cityId;
        this.weatherSource = weatherSource;
        this.alertId = alertId;
        this.date = date;
        this.time = time;
        this.description = description;
        this.content = content;
        this.type = type;
        this.priority = priority;
        this.color = color;
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

    public long getAlertId() {
        return this.alertId;
    }

    public void setAlertId(long alertId) {
        this.alertId = alertId;
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

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContent() {
        return this.content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getType() {
        return this.type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getPriority() {
        return this.priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getColor() {
        return this.color;
    }

    public void setColor(int color) {
        this.color = color;
    }
}



