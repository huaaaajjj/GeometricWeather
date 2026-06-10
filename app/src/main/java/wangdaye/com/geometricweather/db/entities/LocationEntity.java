package wangdaye.com.geometricweather.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.TimeZone;

import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;

@Entity(tableName = "location")
public class LocationEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "formattedId")
    public String formattedId;

    @ColumnInfo(name = "cityId")
    public String cityId;

    @ColumnInfo(name = "latitude")
    public float latitude;

    @ColumnInfo(name = "longitude")
    public float longitude;

    @ColumnInfo(name = "timeZone")
    public TimeZone timeZone;

    @ColumnInfo(name = "country")
    public String country;

    @ColumnInfo(name = "province")
    public String province;

    @ColumnInfo(name = "city")
    public String city;

    @ColumnInfo(name = "district")
    public String district;

    @ColumnInfo(name = "weatherSource")
    public WeatherSource weatherSource;

    @ColumnInfo(name = "currentPosition")
    public boolean currentPosition;

    @ColumnInfo(name = "residentPosition")
    public boolean residentPosition;

    @ColumnInfo(name = "china")
    public boolean china;

    public LocationEntity() {
    }

    @Ignore
    public LocationEntity(@NonNull String formattedId, String cityId, float latitude,
                          float longitude, TimeZone timeZone, String country, String province,
                          String city, String district, WeatherSource weatherSource,
                          boolean currentPosition, boolean residentPosition, boolean china) {
        this.formattedId = formattedId;
        this.cityId = cityId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timeZone = timeZone;
        this.country = country;
        this.province = province;
        this.city = city;
        this.district = district;
        this.weatherSource = weatherSource;
        this.currentPosition = currentPosition;
        this.residentPosition = residentPosition;
        this.china = china;
    }

    @NonNull
    public String getFormattedId() {
        return this.formattedId;
    }

    public void setFormattedId(@NonNull String formattedId) {
        this.formattedId = formattedId;
    }

    public String getCityId() {
        return this.cityId;
    }

    public void setCityId(String cityId) {
        this.cityId = cityId;
    }

    public float getLatitude() {
        return this.latitude;
    }

    public void setLatitude(float latitude) {
        this.latitude = latitude;
    }

    public float getLongitude() {
        return this.longitude;
    }

    public void setLongitude(float longitude) {
        this.longitude = longitude;
    }

    public TimeZone getTimeZone() {
        return this.timeZone;
    }

    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    public String getCountry() {
        return this.country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getProvince() {
        return this.province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCity() {
        return this.city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getDistrict() {
        return this.district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public WeatherSource getWeatherSource() {
        return this.weatherSource;
    }

    public void setWeatherSource(WeatherSource weatherSource) {
        this.weatherSource = weatherSource;
    }

    public boolean getCurrentPosition() {
        return this.currentPosition;
    }

    public void setCurrentPosition(boolean currentPosition) {
        this.currentPosition = currentPosition;
    }

    public boolean getResidentPosition() {
        return this.residentPosition;
    }

    public void setResidentPosition(boolean residentPosition) {
        this.residentPosition = residentPosition;
    }

    public boolean getChina() {
        return this.china;
    }

    public void setChina(boolean china) {
        this.china = china;
    }
}



