package wangdaye.com.geometricweather.db.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.Date;

import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode;
import wangdaye.com.geometricweather.common.basic.models.weather.WindDegree;

@Entity(tableName = "weather")
public class WeatherEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public Long id;

    @ColumnInfo(name = "cityId")
    public String cityId;

    @ColumnInfo(name = "weatherSource")
    public String weatherSource;

    @ColumnInfo(name = "timeStamp")
    public long timeStamp;

    @ColumnInfo(name = "publishDate")
    public Date publishDate;

    @ColumnInfo(name = "publishTime")
    public long publishTime;

    @ColumnInfo(name = "updateDate")
    public Date updateDate;

    @ColumnInfo(name = "updateTime")
    public long updateTime;

    @ColumnInfo(name = "weatherText")
    public String weatherText;

    @ColumnInfo(name = "weatherCode")
    public WeatherCode weatherCode;

    @ColumnInfo(name = "temperature")
    public int temperature;

    @ColumnInfo(name = "realFeelTemperature")
    public Integer realFeelTemperature;

    @ColumnInfo(name = "realFeelShaderTemperature")
    public Integer realFeelShaderTemperature;

    @ColumnInfo(name = "apparentTemperature")
    public Integer apparentTemperature;

    @ColumnInfo(name = "windChillTemperature")
    public Integer windChillTemperature;

    @ColumnInfo(name = "wetBulbTemperature")
    public Integer wetBulbTemperature;

    @ColumnInfo(name = "degreeDayTemperature")
    public Integer degreeDayTemperature;

    @ColumnInfo(name = "totalPrecipitation")
    public Float totalPrecipitation;

    @ColumnInfo(name = "thunderstormPrecipitation")
    public Float thunderstormPrecipitation;

    @ColumnInfo(name = "rainPrecipitation")
    public Float rainPrecipitation;

    @ColumnInfo(name = "snowPrecipitation")
    public Float snowPrecipitation;

    @ColumnInfo(name = "icePrecipitation")
    public Float icePrecipitation;

    @ColumnInfo(name = "totalPrecipitationProbability")
    public Float totalPrecipitationProbability;

    @ColumnInfo(name = "thunderstormPrecipitationProbability")
    public Float thunderstormPrecipitationProbability;

    @ColumnInfo(name = "rainPrecipitationProbability")
    public Float rainPrecipitationProbability;

    @ColumnInfo(name = "snowPrecipitationProbability")
    public Float snowPrecipitationProbability;

    @ColumnInfo(name = "icePrecipitationProbability")
    public Float icePrecipitationProbability;

    @ColumnInfo(name = "windDirection")
    public String windDirection;

    @ColumnInfo(name = "windDegree")
    public WindDegree windDegree;

    @ColumnInfo(name = "windSpeed")
    public Float windSpeed;

    @ColumnInfo(name = "windLevel")
    public String windLevel;

    @ColumnInfo(name = "uvIndex")
    public Integer uvIndex;

    @ColumnInfo(name = "uvLevel")
    public String uvLevel;

    @ColumnInfo(name = "uvDescription")
    public String uvDescription;

    @ColumnInfo(name = "aqiText")
    public String aqiText;

    @ColumnInfo(name = "aqiIndex")
    public Integer aqiIndex;

    @ColumnInfo(name = "pm25")
    public Float pm25;

    @ColumnInfo(name = "pm10")
    public Float pm10;

    @ColumnInfo(name = "so2")
    public Float so2;

    @ColumnInfo(name = "no2")
    public Float no2;

    @ColumnInfo(name = "o3")
    public Float o3;

    @ColumnInfo(name = "co")
    public Float co;

    @ColumnInfo(name = "relativeHumidity")
    public Float relativeHumidity;

    @ColumnInfo(name = "pressure")
    public Float pressure;

    @ColumnInfo(name = "visibility")
    public Float visibility;

    @ColumnInfo(name = "dewPoint")
    public Integer dewPoint;

    @ColumnInfo(name = "cloudCover")
    public Integer cloudCover;

    @ColumnInfo(name = "ceiling")
    public Float ceiling;

    @ColumnInfo(name = "dailyForecast")
    public String dailyForecast;

    @ColumnInfo(name = "hourlyForecast")
    public String hourlyForecast;

    public WeatherEntity() {
    }

    @Ignore
    public WeatherEntity(Long id, String cityId, String weatherSource,
                         long timeStamp, Date publishDate, long publishTime,
                         Date updateDate, long updateTime,
                         String weatherText, WeatherCode weatherCode,
                         int temperature, Integer realFeelTemperature,
                         Integer realFeelShaderTemperature, Integer apparentTemperature,
                         Integer windChillTemperature, Integer wetBulbTemperature,
                         Integer degreeDayTemperature,
                         Float totalPrecipitation, Float thunderstormPrecipitation,
                         Float rainPrecipitation, Float snowPrecipitation,
                         Float icePrecipitation,
                         Float totalPrecipitationProbability,
                         Float thunderstormPrecipitationProbability,
                         Float rainPrecipitationProbability,
                         Float snowPrecipitationProbability,
                         Float icePrecipitationProbability,
                         String windDirection, WindDegree windDegree,
                         Float windSpeed, String windLevel,
                         Integer uvIndex, String uvLevel, String uvDescription,
                         String aqiText, Integer aqiIndex,
                         Float pm25, Float pm10, Float so2, Float no2, Float o3, Float co,
                         Float relativeHumidity, Float pressure, Float visibility,
                         Integer dewPoint, Integer cloudCover, Float ceiling,
                         String dailyForecast, String hourlyForecast) {
        this.id = id;
        this.cityId = cityId;
        this.weatherSource = weatherSource;
        this.timeStamp = timeStamp;
        this.publishDate = publishDate;
        this.publishTime = publishTime;
        this.updateDate = updateDate;
        this.updateTime = updateTime;
        this.weatherText = weatherText;
        this.weatherCode = weatherCode;
        this.temperature = temperature;
        this.realFeelTemperature = realFeelTemperature;
        this.realFeelShaderTemperature = realFeelShaderTemperature;
        this.apparentTemperature = apparentTemperature;
        this.windChillTemperature = windChillTemperature;
        this.wetBulbTemperature = wetBulbTemperature;
        this.degreeDayTemperature = degreeDayTemperature;
        this.totalPrecipitation = totalPrecipitation;
        this.thunderstormPrecipitation = thunderstormPrecipitation;
        this.rainPrecipitation = rainPrecipitation;
        this.snowPrecipitation = snowPrecipitation;
        this.icePrecipitation = icePrecipitation;
        this.totalPrecipitationProbability = totalPrecipitationProbability;
        this.thunderstormPrecipitationProbability = thunderstormPrecipitationProbability;
        this.rainPrecipitationProbability = rainPrecipitationProbability;
        this.snowPrecipitationProbability = snowPrecipitationProbability;
        this.icePrecipitationProbability = icePrecipitationProbability;
        this.windDirection = windDirection;
        this.windDegree = windDegree;
        this.windSpeed = windSpeed;
        this.windLevel = windLevel;
        this.uvIndex = uvIndex;
        this.uvLevel = uvLevel;
        this.uvDescription = uvDescription;
        this.aqiText = aqiText;
        this.aqiIndex = aqiIndex;
        this.pm25 = pm25;
        this.pm10 = pm10;
        this.so2 = so2;
        this.no2 = no2;
        this.o3 = o3;
        this.co = co;
        this.relativeHumidity = relativeHumidity;
        this.pressure = pressure;
        this.visibility = visibility;
        this.dewPoint = dewPoint;
        this.cloudCover = cloudCover;
        this.ceiling = ceiling;
        this.dailyForecast = dailyForecast;
        this.hourlyForecast = hourlyForecast;
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

    public long getTimeStamp() {
        return this.timeStamp;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }

    public Date getPublishDate() {
        return this.publishDate;
    }

    public void setPublishDate(Date publishDate) {
        this.publishDate = publishDate;
    }

    public long getPublishTime() {
        return this.publishTime;
    }

    public void setPublishTime(long publishTime) {
        this.publishTime = publishTime;
    }

    public Date getUpdateDate() {
        return this.updateDate;
    }

    public void setUpdateDate(Date updateDate) {
        this.updateDate = updateDate;
    }

    public long getUpdateTime() {
        return this.updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
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

    public int getTemperature() {
        return this.temperature;
    }

    public void setTemperature(int temperature) {
        this.temperature = temperature;
    }

    public Integer getRealFeelTemperature() {
        return this.realFeelTemperature;
    }

    public void setRealFeelTemperature(Integer realFeelTemperature) {
        this.realFeelTemperature = realFeelTemperature;
    }

    public Integer getRealFeelShaderTemperature() {
        return this.realFeelShaderTemperature;
    }

    public void setRealFeelShaderTemperature(Integer realFeelShaderTemperature) {
        this.realFeelShaderTemperature = realFeelShaderTemperature;
    }

    public Integer getApparentTemperature() {
        return this.apparentTemperature;
    }

    public void setApparentTemperature(Integer apparentTemperature) {
        this.apparentTemperature = apparentTemperature;
    }

    public Integer getWindChillTemperature() {
        return this.windChillTemperature;
    }

    public void setWindChillTemperature(Integer windChillTemperature) {
        this.windChillTemperature = windChillTemperature;
    }

    public Integer getWetBulbTemperature() {
        return this.wetBulbTemperature;
    }

    public void setWetBulbTemperature(Integer wetBulbTemperature) {
        this.wetBulbTemperature = wetBulbTemperature;
    }

    public Integer getDegreeDayTemperature() {
        return this.degreeDayTemperature;
    }

    public void setDegreeDayTemperature(Integer degreeDayTemperature) {
        this.degreeDayTemperature = degreeDayTemperature;
    }

    public Float getTotalPrecipitation() {
        return this.totalPrecipitation;
    }

    public void setTotalPrecipitation(Float totalPrecipitation) {
        this.totalPrecipitation = totalPrecipitation;
    }

    public Float getThunderstormPrecipitation() {
        return this.thunderstormPrecipitation;
    }

    public void setThunderstormPrecipitation(Float thunderstormPrecipitation) {
        this.thunderstormPrecipitation = thunderstormPrecipitation;
    }

    public Float getRainPrecipitation() {
        return this.rainPrecipitation;
    }

    public void setRainPrecipitation(Float rainPrecipitation) {
        this.rainPrecipitation = rainPrecipitation;
    }

    public Float getSnowPrecipitation() {
        return this.snowPrecipitation;
    }

    public void setSnowPrecipitation(Float snowPrecipitation) {
        this.snowPrecipitation = snowPrecipitation;
    }

    public Float getIcePrecipitation() {
        return this.icePrecipitation;
    }

    public void setIcePrecipitation(Float icePrecipitation) {
        this.icePrecipitation = icePrecipitation;
    }

    public Float getTotalPrecipitationProbability() {
        return this.totalPrecipitationProbability;
    }

    public void setTotalPrecipitationProbability(Float totalPrecipitationProbability) {
        this.totalPrecipitationProbability = totalPrecipitationProbability;
    }

    public Float getThunderstormPrecipitationProbability() {
        return this.thunderstormPrecipitationProbability;
    }

    public void setThunderstormPrecipitationProbability(Float thunderstormPrecipitationProbability) {
        this.thunderstormPrecipitationProbability = thunderstormPrecipitationProbability;
    }

    public Float getRainPrecipitationProbability() {
        return this.rainPrecipitationProbability;
    }

    public void setRainPrecipitationProbability(Float rainPrecipitationProbability) {
        this.rainPrecipitationProbability = rainPrecipitationProbability;
    }

    public Float getSnowPrecipitationProbability() {
        return this.snowPrecipitationProbability;
    }

    public void setSnowPrecipitationProbability(Float snowPrecipitationProbability) {
        this.snowPrecipitationProbability = snowPrecipitationProbability;
    }

    public Float getIcePrecipitationProbability() {
        return this.icePrecipitationProbability;
    }

    public void setIcePrecipitationProbability(Float icePrecipitationProbability) {
        this.icePrecipitationProbability = icePrecipitationProbability;
    }

    public String getWindDirection() {
        return this.windDirection;
    }

    public void setWindDirection(String windDirection) {
        this.windDirection = windDirection;
    }

    public WindDegree getWindDegree() {
        return this.windDegree;
    }

    public void setWindDegree(WindDegree windDegree) {
        this.windDegree = windDegree;
    }

    public Float getWindSpeed() {
        return this.windSpeed;
    }

    public void setWindSpeed(Float windSpeed) {
        this.windSpeed = windSpeed;
    }

    public String getWindLevel() {
        return this.windLevel;
    }

    public void setWindLevel(String windLevel) {
        this.windLevel = windLevel;
    }

    public Integer getUvIndex() {
        return this.uvIndex;
    }

    public void setUvIndex(Integer uvIndex) {
        this.uvIndex = uvIndex;
    }

    public String getUvLevel() {
        return this.uvLevel;
    }

    public void setUvLevel(String uvLevel) {
        this.uvLevel = uvLevel;
    }

    public String getUvDescription() {
        return this.uvDescription;
    }

    public void setUvDescription(String uvDescription) {
        this.uvDescription = uvDescription;
    }

    public String getAqiText() {
        return this.aqiText;
    }

    public void setAqiText(String aqiText) {
        this.aqiText = aqiText;
    }

    public Integer getAqiIndex() {
        return this.aqiIndex;
    }

    public void setAqiIndex(Integer aqiIndex) {
        this.aqiIndex = aqiIndex;
    }

    public Float getPm25() {
        return this.pm25;
    }

    public void setPm25(Float pm25) {
        this.pm25 = pm25;
    }

    public Float getPm10() {
        return this.pm10;
    }

    public void setPm10(Float pm10) {
        this.pm10 = pm10;
    }

    public Float getSo2() {
        return this.so2;
    }

    public void setSo2(Float so2) {
        this.so2 = so2;
    }

    public Float getNo2() {
        return this.no2;
    }

    public void setNo2(Float no2) {
        this.no2 = no2;
    }

    public Float getO3() {
        return this.o3;
    }

    public void setO3(Float o3) {
        this.o3 = o3;
    }

    public Float getCo() {
        return this.co;
    }

    public void setCo(Float co) {
        this.co = co;
    }

    public Float getRelativeHumidity() {
        return this.relativeHumidity;
    }

    public void setRelativeHumidity(Float relativeHumidity) {
        this.relativeHumidity = relativeHumidity;
    }

    public Float getPressure() {
        return this.pressure;
    }

    public void setPressure(Float pressure) {
        this.pressure = pressure;
    }

    public Float getVisibility() {
        return this.visibility;
    }

    public void setVisibility(Float visibility) {
        this.visibility = visibility;
    }

    public Integer getDewPoint() {
        return this.dewPoint;
    }

    public void setDewPoint(Integer dewPoint) {
        this.dewPoint = dewPoint;
    }

    public Integer getCloudCover() {
        return this.cloudCover;
    }

    public void setCloudCover(Integer cloudCover) {
        this.cloudCover = cloudCover;
    }

    public Float getCeiling() {
        return this.ceiling;
    }

    public void setCeiling(Float ceiling) {
        this.ceiling = ceiling;
    }

    public String getDailyForecast() {
        return this.dailyForecast;
    }

    public void setDailyForecast(String dailyForecast) {
        this.dailyForecast = dailyForecast;
    }

    public String getHourlyForecast() {
        return this.hourlyForecast;
    }

    public void setHourlyForecast(String hourlyForecast) {
        this.hourlyForecast = hourlyForecast;
    }
}



