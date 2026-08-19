package wangdaye.com.geometricweather.main.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.cardview.widget.CardView;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

import wangdaye.com.geometricweather.R;
import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.unit.CloudCoverUnit;
import wangdaye.com.geometricweather.common.basic.models.options.unit.RelativeHumidityUnit;
import wangdaye.com.geometricweather.common.basic.models.options.unit.SpeedUnit;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.ui.widgets.ArcProgress;
import wangdaye.com.geometricweather.common.utils.DisplayUtils;
import wangdaye.com.geometricweather.main.utils.MainThemeColorProvider;
import wangdaye.com.geometricweather.settings.SettingsManager;

/**
 * The details, as tiles: each reading is a label with its value, and beside it a gauge whenever the
 * reading has a range worth drawing (humidity out of 100, UV out of the scale, pressure around
 * standard, …). Readings with no natural range — dew point, ceiling — keep the icon they always had,
 * in the same spot, so the grid stays even.
 */
public class DetailsAdapter extends RecyclerView.Adapter<DetailsAdapter.ViewHolder> {

    private final boolean mLightTheme;
    private final int mTileColor;
    private final List<Index> mIndexList;

    private static class Index {
        @DrawableRes int iconId;
        String title;
        String content;
        String talkBack;

        /** Null leaves the gauge out and the icon in. */
        @Nullable Float progress;
        float max;
        /** Drawn inside the gauge; the value on the left is not repeated unless it helps. */
        String gaugeText;
        String gaugeBottomText;

        Index(@DrawableRes int iconId, String title, String content) {
            this(iconId, title, content, title + ", " + content);
        }

        Index(@DrawableRes int iconId, String title, String content, String talkBack) {
            this.iconId = iconId;
            this.title = title;
            this.content = content;
            this.talkBack = talkBack;
            this.progress = null;
            this.max = 100;
            this.gaugeText = "";
            this.gaugeBottomText = "";
        }

        Index withGauge(float progress, float max, String text, String bottomText) {
            this.progress = progress;
            this.max = max;
            this.gaugeText = text;
            this.gaugeBottomText = bottomText;
            return this;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final CardView mTile;
        private final AppCompatImageView mIcon;
        private final TextView mTitle;
        private final TextView mContent;
        private final ArcProgress mProgress;

        ViewHolder(View itemView) {
            super(itemView);
            mTile = itemView.findViewById(R.id.item_details);
            mIcon = itemView.findViewById(R.id.item_details_icon);
            mTitle = itemView.findViewById(R.id.item_details_title);
            mContent = itemView.findViewById(R.id.item_details_content);
            mProgress = itemView.findViewById(R.id.item_details_progress);
        }

        void onBindView(boolean lightTheme, int tileColor, Index index) {
            itemView.setContentDescription(index.talkBack);

            mTile.setCardBackgroundColor(tileColor);

            mTitle.setText(index.title);
            mContent.setText(index.content);
            mTitle.setTextColor(
                    MainThemeColorProvider.getColor(lightTheme, R.attr.colorCaptionText)
            );
            mContent.setTextColor(
                    MainThemeColorProvider.getColor(lightTheme, R.attr.colorTitleText)
            );

            if (index.progress == null) {
                mProgress.setVisibility(View.GONE);
                mIcon.setVisibility(View.VISIBLE);
                mIcon.setImageResource(index.iconId);
                ImageViewCompat.setImageTintList(
                        mIcon,
                        ColorStateList.valueOf(
                                MainThemeColorProvider.getColor(lightTheme, R.attr.colorTitleText)
                        )
                );
                return;
            }

            mIcon.setVisibility(View.GONE);
            mProgress.setVisibility(View.VISIBLE);
            mProgress.setMax(index.max);
            mProgress.setProgress(index.progress);
            mProgress.setText(index.gaugeText);
            mProgress.setBottomText(index.gaugeBottomText);
            mProgress.setProgressColor(
                    MainThemeColorProvider.getColor(lightTheme, R.attr.colorPrimary),
                    lightTheme
            );
            mProgress.setArcBackgroundColor(
                    MainThemeColorProvider.getColor(lightTheme, R.attr.colorOutline)
            );
            mProgress.setTextColor(
                    MainThemeColorProvider.getColor(lightTheme, R.attr.colorTitleText)
            );
            mProgress.setBottomTextColor(
                    MainThemeColorProvider.getColor(lightTheme, R.attr.colorCaptionText)
            );
        }
    }

    public DetailsAdapter(Context context, Location location) {
        mLightTheme = MainThemeColorProvider.isLightTheme(context, location);
        // The same elevated surface the tag chips sit on, so a tile reads as a layer of this card
        // rather than a card of its own.
        mTileColor = DisplayUtils.getWidgetSurfaceColor(
                DisplayUtils.DEFAULT_CARD_LIST_ITEM_ELEVATION_DP,
                MainThemeColorProvider.getColor(mLightTheme, R.attr.colorPrimary),
                MainThemeColorProvider.getColor(mLightTheme, R.attr.colorSurface)
        );

        mIndexList = new ArrayList<>();
        SettingsManager settings = SettingsManager.getInstance(context);
        SpeedUnit speedUnit = settings.getSpeedUnit();
        Weather weather = location.getWeather();
        assert weather != null;

        // Wind: speed is the value, the direction sits in the gauge (the providers give it as a
        // compass abbreviation, not translated prose), and the arc fills over the Beaufort scale's
        // practical top end rather than an arbitrary maximum.
        String windDirection = weather.getCurrent().getWind().getDirection();
        float windSpeed = weather.getCurrent().getWind().getSpeed() == null
                ? 0
                : weather.getCurrent().getWind().getSpeed();
        mIndexList.add(
                new Index(
                        R.drawable.ic_wind,
                        context.getString(R.string.wind),
                        speedUnit.getValueTextWithoutUnit(windSpeed),
                        context.getString(R.string.wind)
                                + ", " + weather.getCurrent().getWind()
                                        .getWindDescription(context, speedUnit)
                ).withGauge(
                        Math.min(windSpeed, WIND_SPEED_TOP),
                        WIND_SPEED_TOP,
                        windDirection == null ? "" : windDirection,
                        speedUnit.getName(context)
                )
        );

        if (weather.getCurrent().getRelativeHumidity() != null) {
            int humidity = (int) (float) weather.getCurrent().getRelativeHumidity();
            mIndexList.add(
                    new Index(
                            R.drawable.ic_water_percent,
                            context.getString(R.string.humidity),
                            RelativeHumidityUnit.PERCENT.getValueText(context, humidity)
                    ).withGauge(humidity, 100, "", "%")
            );
        }

        if (weather.getCurrent().getUV().isValid()) {
            Integer index = weather.getCurrent().getUV().getIndex();
            String level = weather.getCurrent().getUV().getLevel();
            // Not the same number twice: with a level to name it ("moderate"), that is the value and
            // the index goes in the gauge; without one, the index itself is the value.
            boolean hasLevel = level != null && !level.isEmpty();
            mIndexList.add(
                    new Index(
                            R.drawable.ic_uv,
                            context.getString(R.string.uv_index),
                            hasLevel
                                    ? level
                                    : weather.getCurrent().getUV().getUVDescription()
                    ).withGauge(
                            index == null ? 0 : Math.min(index, UV_TOP),
                            UV_TOP,
                            hasLevel && index != null ? String.valueOf(index) : "",
                            "UV"
                    )
            );
        }

        if (weather.getCurrent().getPressure() != null) {
            float pressure = weather.getCurrent().getPressure();
            mIndexList.add(
                    new Index(
                            R.drawable.ic_gauge,
                            context.getString(R.string.pressure),
                            String.valueOf(Math.round(
                                    settings.getPressureUnit().getValueWithoutUnit(pressure))),
                            context.getString(R.string.pressure)
                                    + ", " + settings.getPressureUnit().getValueVoice(context, pressure)
                    ).withGauge(
                            // Only the span either side of standard pressure carries information.
                            Math.min(Math.max(pressure - PRESSURE_FLOOR, 0), PRESSURE_SPAN),
                            PRESSURE_SPAN,
                            "",
                            settings.getPressureUnit().getName(context)
                    )
            );
        }

        if (weather.getCurrent().getVisibility() != null) {
            float visibility = weather.getCurrent().getVisibility();
            mIndexList.add(
                    new Index(
                            R.drawable.ic_eye,
                            context.getString(R.string.visibility),
                            String.format(Locale.getDefault(), "%.1f",
                                    settings.getDistanceUnit().getValueWithoutUnit(visibility)),
                            context.getString(R.string.visibility)
                                    + ", " + settings.getDistanceUnit().getValueVoice(context, visibility)
                    ).withGauge(
                            // Past this much, "clear" is the only thing left to say.
                            Math.min(visibility, VISIBILITY_TOP),
                            VISIBILITY_TOP,
                            "",
                            settings.getDistanceUnit().getName(context)
                    )
            );
        }

        if (weather.getCurrent().getDewPoint() != null) {
            mIndexList.add(
                    new Index(
                            R.drawable.ic_water,
                            context.getString(R.string.dew_point),
                            settings.getTemperatureUnit().getValueText(
                                    context,
                                    weather.getCurrent().getDewPoint()
                            )
                    )
            );
        }

        if (weather.getCurrent().getCloudCover() != null) {
            int cloudCover = weather.getCurrent().getCloudCover();
            mIndexList.add(
                    new Index(
                            R.drawable.ic_cloud,
                            context.getString(R.string.cloud_cover),
                            CloudCoverUnit.PERCENT.getValueText(context, cloudCover)
                    ).withGauge(cloudCover, 100, "", "%")
            );
        }

        if (weather.getCurrent().getCeiling() != null) {
            mIndexList.add(
                    new Index(
                            R.drawable.ic_top,
                            context.getString(R.string.ceiling),
                            settings.getDistanceUnit().getValueText(
                                    context,
                                    weather.getCurrent().getCeiling()
                            ),
                            context.getString(R.string.ceiling) + ", " + settings.getDistanceUnit().getValueVoice(
                                    context, weather.getCurrent().getCeiling())
                    )
            );
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_details, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.onBindView(mLightTheme, mTileColor, mIndexList.get(position));
    }

    @Override
    public int getItemCount() {
        return mIndexList.size();
    }

    /** Beaufort 12 starts here (m/s * 3.6 = km/h, the model's own unit). */
    private static final float WIND_SPEED_TOP = 118;
    /** The scale runs 0-11+; anything above is "extreme" all the same. */
    private static final float UV_TOP = 11;
    private static final float PRESSURE_FLOOR = 950;
    private static final float PRESSURE_SPAN = 130;
    /** Kilometres, the model's own unit. */
    private static final float VISIBILITY_TOP = 20;
}
