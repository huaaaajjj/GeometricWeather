package wangdaye.com.geometricweather.main.adapters.main.holder;

import android.annotation.SuppressLint;
import android.text.TextUtils;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import wangdaye.com.geometricweather.R;
import wangdaye.com.geometricweather.common.basic.GeoActivity;
import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.appearance.HourlyTrendDisplay;
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.common.basic.models.weather.Hourly;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.ui.adapters.TagAdapter;
import wangdaye.com.geometricweather.common.ui.decotarions.GridMarginsDecoration;
import wangdaye.com.geometricweather.common.ui.widgets.trend.TrendRecyclerView;
import wangdaye.com.geometricweather.common.utils.DisplayUtils;
import wangdaye.com.geometricweather.main.adapters.main.MainTag;
import wangdaye.com.geometricweather.main.adapters.trend.HourlyTrendAdapter;
import wangdaye.com.geometricweather.main.layouts.TrendHorizontalLinearLayoutManager;
import wangdaye.com.geometricweather.main.utils.MainThemeColorProvider;
import wangdaye.com.geometricweather.main.widgets.TrendRecyclerViewScrollBar;
import wangdaye.com.geometricweather.settings.SettingsManager;
import wangdaye.com.geometricweather.theme.ThemeManager;
import wangdaye.com.geometricweather.common.basic.models.options.provider.CompositeBlock;
import wangdaye.com.geometricweather.theme.resource.providers.ResourceProvider;
import wangdaye.com.geometricweather.theme.weatherView.WeatherViewController;

public class HourlyViewHolder extends AbstractMainCardViewHolder {

    /** The card never shows hours past day three — see the trim in {@link #onBindView}. */
    private static final long HOURLY_HORIZON_MILLIS = 3 * 24 * 60 * 60 * 1000L;

    private final TextView mTitle;
    private final TextView mSubtitle;
    private final RecyclerView mTagView;

    private final TrendRecyclerView mTrendRecyclerView;
    private final HourlyTrendAdapter mTrendAdapter;
    private final TrendRecyclerViewScrollBar mScrollBar;

    @SuppressLint("NotifyDataSetChanged")
    public HourlyViewHolder(ViewGroup parent) {
        super(
                LayoutInflater
                        .from(parent.getContext())
                        .inflate(R.layout.container_main_hourly_trend_card, parent, false)
        );

        mTitle = itemView.findViewById(R.id.container_main_hourly_trend_card_title);
        mSubtitle = itemView.findViewById(R.id.container_main_hourly_trend_card_subtitle);
        mTagView = itemView.findViewById(R.id.container_main_hourly_trend_card_tagView);

        mTrendRecyclerView = itemView.findViewById(R.id.container_main_hourly_trend_card_trendRecyclerView);
        mTrendRecyclerView.setHasFixedSize(true);

        mTrendAdapter = new HourlyTrendAdapter();
        mScrollBar = new TrendRecyclerViewScrollBar();
        mTrendRecyclerView.addItemDecoration(mScrollBar);
    }

    @Override
    public void onBindView(Context context, @NonNull Location location, @NonNull ResourceProvider provider,
                           boolean listAnimationEnabled, boolean itemAnimationEnabled) {
        super.onBindView(context, location, provider, listAnimationEnabled, itemAnimationEnabled);

        Weather weather = location.getWeather();
        assert weather != null;

        // Every provider answers in whole days, so the series still opens at 00:00 late in the
        // evening. This card is a forecast, not a log: start it at the hour it is now. The trend
        // adapters read the hourly list off the location they are handed, so trimming it here is
        // enough for the whole card — tags included.
        //
        // Three days is the whole useful horizon: the sources that answer far beyond it (Open-Meteo
        // alone serves 384 points) are chart filler by day four.
        long now = System.currentTimeMillis();
        Location hourlyLocation = Location.copy(
                location,
                weather.withHoursFrom(now).withHoursUntil(now + HOURLY_HORIZON_MILLIS));
        Weather hourlyWeather = hourlyLocation.getWeather();
        assert hourlyWeather != null;

        int[] colors = ThemeManager
                .getInstance(context)
                .getWeatherThemeDelegate()
                .getThemeColors(
                        context,
                        WeatherViewController.getWeatherKind(weather),
                        location.isDaylight()
                );
        mTitle.setTextColor(colors[0]);
        mTitle.setText(CompositeBlock.title(
                context, location, CompositeBlock.HOURLY, R.string.hourly_overview));

        if (TextUtils.isEmpty(weather.getCurrent().getHourlyForecast())) {
            mSubtitle.setVisibility(View.GONE);
        } else {
            mSubtitle.setVisibility(View.VISIBLE);
            mSubtitle.setText(weather.getCurrent().getHourlyForecast());
        }

        List<TagAdapter.Tag> tagList = getTagList(hourlyWeather, location.getWeatherSource());
        if (tagList.size() < 2) {
            mTagView.setVisibility(View.GONE);
        } else {
            int decorCount = mTagView.getItemDecorationCount();
            for (int i = 0; i < decorCount; i++) {
                mTagView.removeItemDecorationAt(0);
            }
            mTagView.addItemDecoration(
                    new GridMarginsDecoration(
                            context.getResources().getDimension(R.dimen.little_margin),
                            context.getResources().getDimension(R.dimen.normal_margin),
                            mTagView
                    )
            );

            mTagView.setLayoutManager(new TrendHorizontalLinearLayoutManager(context));
            mTagView.setAdapter(
                    new TagAdapter(
                            tagList,
                            MainThemeColorProvider.getColor(location, R.attr.colorOnPrimary),
                            MainThemeColorProvider.getColor(location, R.attr.colorOnSurface),
                            MainThemeColorProvider.getColor(location, R.attr.colorPrimary),
                            DisplayUtils.getWidgetSurfaceColor(
                                    DisplayUtils.DEFAULT_CARD_LIST_ITEM_ELEVATION_DP,
                                    MainThemeColorProvider.getColor(location, R.attr.colorPrimary),
                                    MainThemeColorProvider.getColor(location, R.attr.colorSurface)
                            ),
                            (checked, oldPosition, newPosition) -> {
                                setTrendAdapterByTag(hourlyLocation, (MainTag) tagList.get(newPosition));
                                return false;
                            },
                            0
                    )
            );
        }

        mTrendRecyclerView.setLayoutManager(
                new TrendHorizontalLinearLayoutManager(
                        context,
                        DisplayUtils.isLandscape(context) ? 7 : 5
                )
        );
        mTrendRecyclerView.setLineColor(MainThemeColorProvider.getColor(location, R.attr.colorOutline));
        mTrendRecyclerView.setAdapter(mTrendAdapter);
        mTrendRecyclerView.setKeyLineVisibility(
                SettingsManager.getInstance(context).isTrendHorizontalLinesEnabled());
        setTrendAdapterByTag(hourlyLocation, (MainTag) tagList.get(0));

        mScrollBar.resetColor(location);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void setTrendAdapterByTag(Location location, MainTag tag) {
        switch (tag.getType()) {
            case TEMPERATURE:
                mTrendAdapter.temperature(
                        (GeoActivity) context, mTrendRecyclerView,
                        location,
                        provider,
                        SettingsManager.getInstance(context).getTemperatureUnit()
                );
                break;

            case PRECIPITATION:
                mTrendAdapter.precipitation(
                        (GeoActivity) context, mTrendRecyclerView,
                        location,
                        provider,
                        SettingsManager.getInstance(context).getPrecipitationUnit()
                );
                break;

            case WIND:
                mTrendAdapter.wind(
                        (GeoActivity) context,
                        mTrendRecyclerView,
                        location,
                        SettingsManager.getInstance(context).getSpeedUnit()
                );
                break;

            case UV_INDEX:
                mTrendAdapter.uv((GeoActivity) context, mTrendRecyclerView, location);
                break;
        }
        mTrendAdapter.notifyDataSetChanged();
    }

    private List<TagAdapter.Tag> getTagList(Weather weather, WeatherSource source) {
        List<TagAdapter.Tag> tagList = new ArrayList<>();
        List<HourlyTrendDisplay> displayList
                = SettingsManager.getInstance(context).getHourlyTrendDisplayList();
        for (HourlyTrendDisplay display : displayList) {
            switch (display) {
                case TAG_TEMPERATURE:
                    tagList.add(new MainTag(context.getString(R.string.tag_temperature), MainTag.Type.TEMPERATURE));
                    break;

                case TAG_WIND:
                    for (Hourly hourly : weather.getHourlyForecast()) {
                        if (hourly.getWind().isValidSpeed()) {
                            tagList.add(new MainTag(context.getString(R.string.tag_wind), MainTag.Type.WIND));
                            break;
                        }
                    }
                    break;

                case TAG_UV_INDEX:
                    for (Hourly hourly : weather.getHourlyForecast()) {
                        if (hourly.getUV().isValid()) {
                            tagList.add(new MainTag(context.getString(R.string.tag_uv), MainTag.Type.UV_INDEX));
                            break;
                        }
                    }
                    break;

                case TAG_PRECIPITATION:
                    tagList.addAll(getPrecipitationTagList(weather));
                    break;
            }
        }
        if (tagList.size() == 0) {
            tagList.add(new MainTag(context.getString(R.string.tag_temperature), MainTag.Type.TEMPERATURE));
        }
        return tagList;
    }

    private List<TagAdapter.Tag> getPrecipitationTagList(Weather weather) {
        int precipitationCount = 0;
        for (Hourly h : weather.getHourlyForecast()) {
            if (h.getWeatherCode().isPrecipitation() && h.getPrecipitation().isValid()) {
                precipitationCount ++;
            }
        }
        if (precipitationCount < 3) {
            return new ArrayList<>();
        } else {
            List<TagAdapter.Tag> list = new ArrayList<>();
            list.add(new MainTag(context.getString(R.string.tag_precipitation), MainTag.Type.PRECIPITATION));
            return list;
        }
    }
}
