package wangdaye.com.geometricweather.main.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.cardview.widget.CardView;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import wangdaye.com.geometricweather.R;
import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.unit.CloudCoverUnit;
import wangdaye.com.geometricweather.common.basic.models.options.unit.RelativeHumidityUnit;
import wangdaye.com.geometricweather.common.basic.models.options.unit.SpeedUnit;
import wangdaye.com.geometricweather.common.basic.models.weather.UV;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.ui.widgets.ArcProgress;
import wangdaye.com.geometricweather.common.utils.DisplayUtils;
import wangdaye.com.geometricweather.main.utils.MainThemeColorProvider;
import wangdaye.com.geometricweather.settings.SettingsManager;
import wangdaye.com.geometricweather.weather.converters.CommonConverter;

/**
 * The details, as tiles: on the left the reading said in words — the band it falls in, not the
 * digits — and on the right a gauge holding the number itself, whenever the reading has a range
 * worth drawing (humidity out of 100, UV out of the scale, pressure around standard, …). Readings
 * with no natural range — dew point, ceiling — have nowhere to put the number, so they keep the
 * value on the left and the icon they always had, in the same spot, so the grid stays even.
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
        /** The number, drawn inside the gauge; {@link #content} says the same reading in words. */
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
        // One neutral elevation step above the card the tiles actually sit on. The list-item helper
        // is the app's own idiom, but its usual tint is colorPrimary, which left these warm pink on
        // a neutral card (#F7EEF2 on #FDFBFF by day, #312222 on #1C1B1F by night); colorOnSurface
        // lifts the same surface without bending its hue, and keeps the caption contrast that a
        // jump to colorSurfaceVariant threw away.
        mTileColor = DisplayUtils.getWidgetSurfaceColor(
                DisplayUtils.DEFAULT_CARD_LIST_ITEM_ELEVATION_DP,
                MainThemeColorProvider.getColor(mLightTheme, R.attr.colorOnSurface),
                MainThemeColorProvider.getColor(mLightTheme, R.attr.colorMainCardBackground)
        );

        mIndexList = new ArrayList<>();
        SettingsManager settings = SettingsManager.getInstance(context);
        SpeedUnit speedUnit = settings.getSpeedUnit();
        Weather weather = location.getWeather();
        assert weather != null;

        // Wind: the Beaufort level is the words, the speed is the number in the gauge, and the
        // compass abbreviation rides along with the label (the providers give "NNE", not prose).
        // The arc fills over the scale's practical top end rather than an arbitrary maximum.
        String windDirection = weather.getCurrent().getWind().getDirection();
        float windSpeed = weather.getCurrent().getWind().getSpeed() == null
                ? 0
                : weather.getCurrent().getWind().getSpeed();
        // Derived here, not read from Wind.getLevel(): every converter fills that field with this
        // same function, so the stored string says nothing extra — it only freezes the language the
        // forecast was fetched in. Cached data outlives a language change; this label should not.
        String windLevel = CommonConverter.getWindLevel(context, windSpeed);
        mIndexList.add(
                new Index(
                        R.drawable.ic_wind,
                        TextUtils.isEmpty(windDirection)
                                ? context.getString(R.string.wind)
                                : context.getString(R.string.wind) + " · " + windDirection,
                        windLevel,
                        context.getString(R.string.wind)
                                + ", " + weather.getCurrent().getWind()
                                        .getWindDescription(context, speedUnit)
                ).withGauge(
                        Math.min(windSpeed, WIND_SPEED_TOP),
                        WIND_SPEED_TOP,
                        speedUnit.getValueTextWithoutUnit(windSpeed),
                        speedUnit.getName(context)
                )
        );

        if (weather.getCurrent().getRelativeHumidity() != null) {
            int humidity = (int) (float) weather.getCurrent().getRelativeHumidity();
            mIndexList.add(
                    new Index(
                            R.drawable.ic_water_percent,
                            context.getString(R.string.humidity),
                            context.getString(humidityLevel(humidity)),
                            context.getString(R.string.humidity) + ", "
                                    + RelativeHumidityUnit.PERCENT.getValueText(context, humidity)
                    ).withGauge(humidity, 100, String.valueOf(humidity), "%")
            );
        }

        if (weather.getCurrent().getUV().isValid()) {
            Integer index = weather.getCurrent().getUV().getIndex();
            String level = weather.getCurrent().getUV().getLevel();
            // The provider's own wording when it has one, our own bands when it does not.
            String uvWord;
            if (level != null && !level.isEmpty()) {
                uvWord = level;
            } else if (index != null) {
                uvWord = context.getString(uvLevel(index));
            } else {
                uvWord = weather.getCurrent().getUV().getUVDescription();
            }
            mIndexList.add(
                    new Index(
                            R.drawable.ic_uv,
                            context.getString(R.string.uv_index),
                            uvWord,
                            context.getString(R.string.uv_index) + ", "
                                    + weather.getCurrent().getUV().getShortUVDescription()
                    ).withGauge(
                            index == null ? 0 : Math.min(index, UV_TOP),
                            UV_TOP,
                            index == null ? "" : String.valueOf(index),
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
                            context.getString(pressureLevel(pressure)),
                            context.getString(R.string.pressure)
                                    + ", " + settings.getPressureUnit().getValueVoice(context, pressure)
                    ).withGauge(
                            // Only the span either side of standard pressure carries information.
                            Math.min(Math.max(pressure - PRESSURE_FLOOR, 0), PRESSURE_SPAN),
                            PRESSURE_SPAN,
                            gaugeNumber(settings.getPressureUnit().getValueWithoutUnit(pressure)),
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
                            context.getString(visibilityLevel(visibility)),
                            context.getString(R.string.visibility)
                                    + ", " + settings.getDistanceUnit().getValueVoice(context, visibility)
                    ).withGauge(
                            // Past this much, "clear" is the only thing left to say.
                            Math.min(visibility, VISIBILITY_TOP),
                            VISIBILITY_TOP,
                            gaugeNumber(settings.getDistanceUnit().getValueWithoutUnit(visibility)),
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
                            context.getString(cloudCoverLevel(cloudCover)),
                            context.getString(R.string.cloud_cover) + ", "
                                    + CloudCoverUnit.PERCENT.getValueText(context, cloudCover)
                    ).withGauge(cloudCover, 100, String.valueOf(cloudCover), "%")
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

    /**
     * The reading as it goes inside the gauge, where there is room for about four digits. How many
     * of them should be decimals depends on the unit the user picked, not on the reading: 1006 mb
     * wants none, 100.6 kPa would lose ~1 mb by rounding, 0.99 atm is nothing without decimals, and
     * 10600 m has no business printing a tenth. Spending the four digits on whatever is left of the
     * point gives exactly that.
     *
     * <p>{@link NumberFormat} rather than {@code String.format("%.1f")}: it drops decimals that come
     * out empty ("10.6", not "10.60") while still writing the separator the user's locale uses.
     */
    static String gaugeNumber(float valueInDisplayUnit) {
        float abs = Math.abs(valueInDisplayUnit);
        NumberFormat format = NumberFormat.getInstance();
        format.setMaximumFractionDigits(abs >= 1000 ? 0 : abs >= 10 ? 1 : 2);
        format.setMinimumFractionDigits(0);
        // A gauge is not a table: 10600 reads better than 10,600 in a 72dp circle.
        format.setGroupingUsed(false);
        return format.format(valueInDisplayUnit);
    }

    // The bands below read the model's own units — percent, millibars, kilometres — not whatever
    // unit the tile happens to be displaying in. Package-private so DetailsLevelTest can check the
    // edges without a Context.

    /** Comfort bands: the middle one is the range you notice nothing in. */
    @StringRes
    static int humidityLevel(int percent) {
        if (percent <= 30) {
            return R.string.humidity_level_1;
        } else if (percent <= 60) {
            return R.string.humidity_level_2;
        } else if (percent <= 80) {
            return R.string.humidity_level_3;
        } else {
            return R.string.humidity_level_4;
        }
    }

    /** The three sectors a barometer dial has always been printed with, in millibars. */
    @StringRes
    static int pressureLevel(float mb) {
        if (mb < 1009) {
            return R.string.pressure_level_1;
        } else if (mb <= 1022) {
            return R.string.pressure_level_2;
        } else {
            return R.string.pressure_level_3;
        }
    }

    /** Fog, haze, then the point past which "clear" is all there is to say. Kilometres. */
    @StringRes
    static int visibilityLevel(float km) {
        if (km < 1) {
            return R.string.visibility_level_1;
        } else if (km < 4) {
            return R.string.visibility_level_2;
        } else if (km < 10) {
            return R.string.visibility_level_3;
        } else {
            return R.string.visibility_level_4;
        }
    }

    /** Roughly the octas a sky report is written in: clear, few, broken, overcast. */
    @StringRes
    static int cloudCoverLevel(int percent) {
        if (percent <= 20) {
            return R.string.cloud_cover_level_1;
        } else if (percent <= 50) {
            return R.string.cloud_cover_level_2;
        } else if (percent <= 85) {
            return R.string.cloud_cover_level_3;
        } else {
            return R.string.cloud_cover_level_4;
        }
    }

    /** The index's own bands — already the thresholds {@link UV} keeps for its colours. */
    @StringRes
    static int uvLevel(int index) {
        if (index <= UV.UV_INDEX_LOW) {
            return R.string.uv_level_1;
        } else if (index <= UV.UV_INDEX_MIDDLE) {
            return R.string.uv_level_2;
        } else if (index <= UV.UV_INDEX_HIGH) {
            return R.string.uv_level_3;
        } else if (index <= UV.UV_INDEX_EXCESSIVE) {
            return R.string.uv_level_4;
        } else {
            return R.string.uv_level_5;
        }
    }
}
