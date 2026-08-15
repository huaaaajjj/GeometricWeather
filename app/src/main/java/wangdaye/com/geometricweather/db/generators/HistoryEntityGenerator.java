package wangdaye.com.geometricweather.db.generators;

import androidx.annotation.Nullable;

import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.common.basic.models.weather.History;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.db.entities.HistoryEntity;
public class HistoryEntityGenerator {

    public static HistoryEntity generate(String cityId, WeatherSource source, History history) {
        HistoryEntity entity = new HistoryEntity();
        entity.cityId = cityId;
        entity.weatherSource = source.getId();
        entity.date = history.getDate();
        entity.time = history.getTime();
        entity.daytimeTemperature = history.getDaytimeTemperature();
        entity.nighttimeTemperature = history.getNighttimeTemperature();
        return entity;
    }

    /**
     * The history row records today's day/night temperatures, which only the first daily entry can
     * supply. A weather with no daily entries therefore has no history to record — say so rather
     * than indexing into an empty list, which takes the process down.
     *
     * WeatherHelper already refuses to accept such a weather, so this is the second line rather
     * than the first; it exists because the crash here is fatal, not graceful.
     */
    @Nullable
    public static HistoryEntity generate(String cityId, WeatherSource source, Weather weather) {
        if (weather.getDailyForecast().isEmpty()) {
            return null;
        }
        HistoryEntity entity = new HistoryEntity();
        entity.cityId = cityId;
        entity.weatherSource = source.getId();
        entity.date = weather.getBase().getPublishDate();
        entity.time = weather.getBase().getPublishTime();
        entity.daytimeTemperature = weather.getDailyForecast().get(0).day().getTemperature().getTemperature();
        entity.nighttimeTemperature = weather.getDailyForecast().get(0).night().getTemperature().getTemperature();
        return entity;
    }

    public static History generate(@Nullable HistoryEntity entity) {
        if (entity == null) {
            return null;
        }
        return new History(
                entity.date,
                entity.time,
                entity.daytimeTemperature,
                entity.nighttimeTemperature
        );
    }
}
