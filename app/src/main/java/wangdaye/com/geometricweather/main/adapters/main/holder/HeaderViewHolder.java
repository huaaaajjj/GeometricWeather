package wangdaye.com.geometricweather.main.adapters.main.holder;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.ColorUtils;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.util.List;
import java.util.TimeZone;

import wangdaye.com.geometricweather.R;
import wangdaye.com.geometricweather.common.basic.GeoActivity;
import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.unit.TemperatureUnit;
import wangdaye.com.geometricweather.common.basic.models.weather.Alert;
import wangdaye.com.geometricweather.common.basic.models.weather.Base;
import wangdaye.com.geometricweather.common.basic.models.weather.Minutely;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.ui.widgets.InkPageIndicator;
import wangdaye.com.geometricweather.common.ui.widgets.NumberAnimTextView;
import wangdaye.com.geometricweather.common.ui.widgets.PrecipitationBar;
import wangdaye.com.geometricweather.common.ui.widgets.horizontal.HorizontalRecyclerView;
import wangdaye.com.geometricweather.common.utils.helpers.IntentHelper;
import wangdaye.com.geometricweather.main.utils.MainThemeColorProvider;
import wangdaye.com.geometricweather.settings.SettingsManager;
import wangdaye.com.geometricweather.theme.ThemeManager;
import wangdaye.com.geometricweather.theme.resource.providers.ResourceProvider;
import wangdaye.com.geometricweather.theme.weatherView.WeatherView;
import wangdaye.com.geometricweather.theme.weatherView.WeatherViewController;

public class HeaderViewHolder extends AbstractMainViewHolder {

    private final LinearLayout mContainer;
    private final LinearLayout mTextBlock;
    private final NumberAnimTextView mTemperature;
    private final TextView mWeather;
    private final TextView mAqiOrWind;
    private final TextView mRefreshTime;
    private final TextClock mLocalTime;

    private final CardView mAlertCard;
    private final TextView mAlertTitle;
    private final InkPageIndicator mAlertIndicator;
    private final HorizontalRecyclerView mAlertPager;

    private final CardView mMinutelyCard;
    private final TextView mMinutelyTitle;
    private final PrecipitationBar mPrecipitationBar;
    private final TextView mMinutelyTime;

    private int mTemperatureCFrom;
    private int mTemperatureCTo;
    private TemperatureUnit mTemperatureUnit;
    private int mAlertCount;

    // The minutely nowcast: which kind of line to show and, for the countdown kinds, the absolute
    // time the rain starts / eases. The count-down text is recomputed against the clock every minute
    // (mMinutelyTicker) so it stays live; the kind and the transition time are frozen until a refresh
    // rebinds — the wet/dry sequence is a forecast, only the clock moves between refreshes.
    private static final int SUMMARY_STARTING = 0;
    private static final int SUMMARY_STOPPING = 1;
    private static final int SUMMARY_CONTINUOUS = 2;
    private static final int SUMMARY_WITHIN = 3;
    private int mMinutelySummaryType;
    private long mMinutelyTransitionTime;
    private final Runnable mMinutelyTicker;

    public HeaderViewHolder(ViewGroup parent, WeatherView weatherView) {
        super(
                LayoutInflater
                        .from(parent.getContext())
                        .inflate(R.layout.container_main_header, parent, false)
        );

        mContainer = itemView.findViewById(R.id.container_main_header);
        mTextBlock = itemView.findViewById(R.id.container_main_header_textBlock);
        mTemperature = itemView.findViewById(R.id.container_main_header_tempTxt);
        mWeather = itemView.findViewById(R.id.container_main_header_weatherTxt);
        mAqiOrWind = itemView.findViewById(R.id.container_main_header_aqiOrWindTxt);
        mRefreshTime = itemView.findViewById(R.id.container_main_header_refreshTxt);
        mLocalTime = itemView.findViewById(R.id.container_main_header_localTimeText);

        mAlertCard = itemView.findViewById(R.id.container_main_alert);
        mAlertTitle = itemView.findViewById(R.id.container_main_alert_title);
        mAlertIndicator = itemView.findViewById(R.id.container_main_alert_indicator);
        mAlertPager = itemView.findViewById(R.id.container_main_alert_pager);

        mAlertPager.setLayoutManager(
                new LinearLayoutManager(parent.getContext(), RecyclerView.HORIZONTAL, false));
        // Full-width pages plus a snap helper is the paging: one alert stops under the finger.
        new PagerSnapHelper().attachToRecyclerView(mAlertPager);
        // The indicator starts transparent so the location switcher can fade it in and out; here it
        // is part of the card and simply stays.
        mAlertIndicator.setAlpha(1f);
        mAlertPager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                followAlertPager();
            }
        });

        mMinutelyCard = itemView.findViewById(R.id.container_main_minutely);
        mMinutelyTitle = itemView.findViewById(R.id.container_main_minutely_title);
        mPrecipitationBar = itemView.findViewById(R.id.container_main_minutely_bar);
        mMinutelyTime = itemView.findViewById(R.id.container_main_minutely_timeText);

        mTemperatureCFrom = 0;
        mTemperatureCTo = 0;
        mTemperatureUnit = null;

        mContainer.setOnClickListener(v -> weatherView.onClick());
        mMinutelyTicker = this::updateMinutelySummary;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindView(Context context, @NonNull Location location, @NonNull ResourceProvider provider,
                           boolean listAnimationEnabled, boolean itemAnimationEnabled) {
        super.onBindView(context, location, provider, listAnimationEnabled, itemAnimationEnabled);

        mTextBlock.setTranslationY(0f);

        // The card list starts where this block ends, so the height stays the full header height
        // even with the alert / minutely cards hanging at the bottom: they eat the empty space
        // under the temperature instead of pushing the first card off the first screen. Only when
        // the two of them genuinely do not fit does the block grow past it.
        mContainer.setMinimumHeight(
                ThemeManager
                        .getInstance(context)
                        .getWeatherThemeDelegate()
                        .getHeaderHeight(context)
        );

        int textColor = ThemeManager
                .getInstance(context)
                .getWeatherThemeDelegate()
                .getHeaderTextColor(context);
        mTemperature.setTextColor(textColor);
        mWeather.setTextColor(textColor);
        mAqiOrWind.setTextColor(textColor);
        // The two meta lines read as captions against the weather background, so they take the
        // header text colour dimmed instead of a card-level theme colour.
        int captionColor = ColorUtils.setAlphaComponent(textColor, (int) (0.7 * 255));
        mRefreshTime.setTextColor(captionColor);
        mLocalTime.setTextColor(captionColor);

        mTemperatureUnit = SettingsManager.getInstance(context).getTemperatureUnit();
        if (location.getWeather() != null) {
            mTemperatureCFrom = mTemperatureCTo;
            mTemperatureCTo = location.getWeather().getCurrent().getTemperature().getTemperature();

            mTemperature.setEnableAnim(itemAnimationEnabled);
            mTemperature.setDuration(
                    (long) Math.min(
                            2000, // no longer than 2 seconds.
                            Math.abs(mTemperatureCTo - mTemperatureCFrom) / 10f * 1000
                    )
            );
            mTemperature.setPostfixString(mTemperatureUnit.getShortName(context));

            StringBuilder title = new StringBuilder(location.getWeather().getCurrent().getWeatherText());
            if (location.getWeather().getCurrent().getTemperature().getRealFeelTemperature() != null) {
                title.append(", ")
                        .append(context.getString(R.string.feels_like))
                        .append(" ")
                        .append(location.getWeather().getCurrent().getTemperature().getShortRealFeeTemperature(context, mTemperatureUnit));
            }
            mWeather.setText(title.toString());

            if (location.getWeather().getCurrent().getAirQuality().getAqiText() == null) {
                mAqiOrWind.setText(
                        context.getString(R.string.wind)
                                + " - "
                                + location.getWeather().getCurrent().getWind().getShortWindDescription()
                );
            } else {
                mAqiOrWind.setText(
                        context.getString(R.string.air_quality)
                                + " - "
                                + location.getWeather().getCurrent().getAirQuality().getAqiText()
                );
            }

            // Base.getTime reads the device clock on purpose: 「更新于」 says when *you* refreshed.
            mRefreshTime.setText(
                    context.getString(R.string.refresh_at)
                            + " "
                            + Base.getTime(context, location.getWeather().getBase().getUpdateDate())
            );

            // The location's own clock, only worth a line when it differs from the device's.
            long time = System.currentTimeMillis();
            if (TimeZone.getDefault().getOffset(time) == location.getTimeZone().getOffset(time)) {
                mLocalTime.setVisibility(View.GONE);
            } else {
                mLocalTime.setVisibility(View.VISIBLE);
                mLocalTime.setTimeZone(location.getTimeZone().getID());
                mLocalTime.setFormat12Hour(
                        context.getString(R.string.date_format_widget_long) + ", h:mm aa"
                );
                mLocalTime.setFormat24Hour(
                        context.getString(R.string.date_format_widget_long) + ", HH:mm"
                );
            }

            itemView.setContentDescription(location.getCityName(context)
                    + ", " + location.getWeather().getCurrent().getTemperature().getTemperature(context, mTemperatureUnit)
                    + ", " + mWeather.getText()
                    + ", " + mAqiOrWind.getText());

            Weather weather = location.getWeather();
            int themeColor = ThemeManager
                    .getInstance(context)
                    .getWeatherThemeDelegate()
                    .getThemeColors(
                            context,
                            WeatherViewController.getWeatherKind(weather),
                            location.isDaylight()
                    )[0];
            bindAlert(location, weather, themeColor);
            bindMinutely(location, weather, themeColor);
        }
    }

    /** Sits above the first card, in the space the temperature block leaves. */
    private void bindAlert(@NonNull Location location, @NonNull Weather weather, int themeColor) {
        List<Alert> alertList = weather.getAlertList();
        if (alertList.isEmpty()) {
            mAlertCard.setVisibility(View.GONE);
            mAlertPager.setAdapter(null);
            return;
        }
        mAlertCard.setVisibility(View.VISIBLE);
        AbstractMainCardViewHolder.styleAsHomeCard(mAlertCard, location);

        mAlertTitle.setTextColor(themeColor);

        String formattedId = location.getFormattedId();
        View.OnClickListener open =
                v -> IntentHelper.startAlertActivity((GeoActivity) context, formattedId);

        // A fresh adapter, then a jump into the middle of the loop so the very first alert can be
        // swiped backwards to the last one. Which page was tapped goes to the detail page too, so
        // it can jump to that alert and flash it.
        AlertPagerAdapter adapter = new AlertPagerAdapter(
                alertList,
                MainThemeColorProvider.getColor(location, R.attr.colorBodyText),
                MainThemeColorProvider.getColor(location, R.attr.colorCaptionText),
                index -> IntentHelper.startAlertActivity((GeoActivity) context, formattedId, index)
        );
        mAlertCount = alertList.size();
        mAlertPager.setAdapter(adapter);
        mAlertPager.scrollToPosition(adapter.firstPage());

        // A single dot says nothing, so the row is only there once there is somewhere to swipe to.
        mAlertIndicator.setVisibility(mAlertCount > 1 ? View.VISIBLE : View.GONE);
        // Setting the count also puts the filled dot back on the first page, matching the pager.
        mAlertIndicator.setPageCount(mAlertCount);
        int dotColor = MainThemeColorProvider.getColor(location, R.attr.colorBodyText);
        mAlertIndicator.setCurrentIndicatorColor(dotColor);
        mAlertIndicator.setIndicatorColor(ColorUtils.setAlphaComponent(dotColor, (int) (0.4 * 255)));

        mAlertCard.setContentDescription(
                context.getString(R.string.content_desc_weather_alert_button)
                        .replace("$", "" + mAlertCount)
        );
        mAlertCard.setOnClickListener(open);
    }

    /**
     * Keeps the dots on the finger rather than on the settled page: the ink stretches with the drag
     * and the filled dot sets off as soon as the next page is the nearer one.
     */
    private void followAlertPager() {
        LinearLayoutManager manager = (LinearLayoutManager) mAlertPager.getLayoutManager();
        if (manager == null || mAlertCount < 2) {
            return;
        }
        int first = manager.findFirstVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION) {
            return;
        }
        View page = manager.findViewByPosition(first);
        if (page == null || page.getWidth() == 0) {
            return;
        }
        int scrolled = -page.getLeft();
        float fraction = scrolled / (float) page.getWidth();

        mAlertIndicator.onPageSelected((fraction > 0.5f ? first + 1 : first) % mAlertCount);
        mAlertIndicator.onPageScrolled(first % mAlertCount, fraction, scrolled);
    }

    /** One page per alert: the headline on one line, its publish time under it. */
    public static class AlertPagerAdapter extends RecyclerView.Adapter<AlertPagerAdapter.PageHolder> {

        public interface AlertClickCallback {
            void onClick(int index);
        }

        private final List<Alert> mAlertList;
        private final int mDescriptionColor;
        private final int mDateColor;
        private final AlertClickCallback mClickListener;

        /**
         * The list is repeated this many times so a swipe never reaches an end: it carries on from
         * the last alert to the first and back. Only two pages exist at a time, so the count costs
         * nothing.
         */
        private static final int LOOPS = 200;

        public AlertPagerAdapter(List<Alert> alertList, int descriptionColor, int dateColor,
                                 AlertClickCallback clickListener) {
            mAlertList = alertList;
            mDescriptionColor = descriptionColor;
            mDateColor = dateColor;
            mClickListener = clickListener;
        }

        /** Halfway into the loop, on the first alert: there is room to swipe either way from here. */
        public int firstPage() {
            return mAlertList.size() < 2 ? 0 : mAlertList.size() * (LOOPS / 2);
        }

        public static class PageHolder extends RecyclerView.ViewHolder {

            private final TextView description;
            private final TextView date;

            PageHolder(@NonNull View view) {
                super(view);
                description = view.findViewById(R.id.item_main_alert_description);
                date = view.findViewById(R.id.item_main_alert_date);
            }
        }

        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PageHolder(
                    LayoutInflater
                            .from(parent.getContext())
                            .inflate(R.layout.item_main_alert, parent, false)
            );
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            Alert alert = mAlertList.get(position % mAlertList.size());

            holder.description.setText(alert.getDescription());
            holder.description.setTextColor(mDescriptionColor);
            // The same format the alert page uses, so the card and the page agree.
            holder.date.setText(
                    DateFormat
                            .getDateTimeInstance(DateFormat.LONG, DateFormat.DEFAULT)
                            .format(alert.getDate())
            );
            holder.date.setTextColor(mDateColor);
            // The adapter loops the list, so map the tapped position back to the real alert index.
            holder.itemView.setOnClickListener(v -> {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION && mClickListener != null) {
                    mClickListener.onClick(adapterPosition % mAlertList.size());
                }
            });
        }

        @Override
        public int getItemCount() {
            // Nothing to loop through with a single alert, and an endless list of one page would
            // let it be dragged sideways for no reason.
            return mAlertList.size() < 2 ? mAlertList.size() : mAlertList.size() * LOOPS;
        }
    }

    /** Same idea as the alert card: only there when there is rain to draw. */
    @SuppressLint("SetTextI18n")
    private void bindMinutely(@NonNull Location location, @NonNull Weather weather, int themeColor) {
        // Any previous card's live countdown must stop before this holder is rebound to new data.
        mMinutelyTime.removeCallbacks(mMinutelyTicker);

        List<Minutely> minutelyList = weather.getMinutelyForecast();
        if (!hasPrecipitation(minutelyList)) {
            mMinutelyCard.setVisibility(View.GONE);
            return;
        }
        mMinutelyCard.setVisibility(View.VISIBLE);
        AbstractMainCardViewHolder.styleAsHomeCard(mMinutelyCard, location);

        mMinutelyTitle.setTextColor(themeColor);

        mPrecipitationBar.setMinutelyList(minutelyList);
        mPrecipitationBar.setFrameColor(MainThemeColorProvider.getColor(location, R.attr.colorOutline));
        mPrecipitationBar.setPrecipitationColor(themeColor);
        // The 大/中/小 axis reads like a caption, so it takes the caption colour.
        mPrecipitationBar.setAxisColor(MainThemeColorProvider.getColor(location, R.attr.colorCaptionText));

        // The chart carries its own :00/:30 bottom axis, so the title row shows a one-line nowcast
        // summary (starts / eases / lasts through). Its countdown ticks against the clock — see
        // prepareMinutelySummary / updateMinutelySummary.
        prepareMinutelySummary(minutelyList);
        updateMinutelySummary();
        mMinutelyTime.setTextColor(MainThemeColorProvider.getColor(location, R.attr.colorCaptionText));

        String start = Base.getTime(context, minutelyList.get(0).getDate());
        String end = Base.getTime(context, minutelyList.get(minutelyList.size() - 1).getDate());
        mMinutelyCard.setContentDescription(
                context.getString(R.string.content_des_minutely_precipitation)
                        .replace("$1", start)
                        .replace("$2", end)
        );
    }

    /**
     * Reads the wet/dry sequence once into {@link #mMinutelySummaryType} and, for the two countdown
     * kinds, the absolute time the rain starts / eases into {@link #mMinutelyTransitionTime}. Frozen
     * until the next rebind; {@link #updateMinutelySummary} turns it into live text.
     */
    private void prepareMinutelySummary(List<Minutely> minutelyList) {
        int size = minutelyList.size();
        boolean rainingNow = minutelyList.get(0).isPrecipitation();
        if (rainingNow) {
            for (int i = 1; i < size; i++) {
                if (!minutelyList.get(i).isPrecipitation()) {
                    mMinutelySummaryType = SUMMARY_STOPPING;
                    mMinutelyTransitionTime = minutelyList.get(i).getDate().getTime();
                    return;
                }
            }
            mMinutelySummaryType = SUMMARY_CONTINUOUS;
            return;
        }
        for (int i = 1; i < size; i++) {
            if (minutelyList.get(i).isPrecipitation()) {
                mMinutelySummaryType = SUMMARY_STARTING;
                mMinutelyTransitionTime = minutelyList.get(i).getDate().getTime();
                return;
            }
        }
        mMinutelySummaryType = SUMMARY_WITHIN;
    }

    /**
     * Writes the nowcast line for the current clock and, while a start/ease is still ahead, schedules
     * itself to run again exactly when the whole-minute count next changes — so the countdown stays
     * live without a refresh. Stops on its own once the transition time has passed.
     */
    private void updateMinutelySummary() {
        mMinutelyTime.removeCallbacks(mMinutelyTicker);
        switch (mMinutelySummaryType) {
            case SUMMARY_STARTING:
            case SUMMARY_STOPPING: {
                long deltaMs = mMinutelyTransitionTime - System.currentTimeMillis();
                int res = mMinutelySummaryType == SUMMARY_STARTING
                        ? R.string.precipitation_summary_starting
                        : R.string.precipitation_summary_stopping;
                mMinutelyTime.setText(
                        context.getString(res).replace("$1", String.valueOf(minutesUntil(deltaMs))));
                if (deltaMs > 0) {
                    // Next whole-minute boundary: one tick per minute, not a busy poll.
                    mMinutelyTime.postDelayed(mMinutelyTicker, nextMinuteTickDelay(deltaMs));
                }
                break;
            }
            case SUMMARY_CONTINUOUS:
                mMinutelyTime.setText(context.getString(R.string.precipitation_summary_continuous));
                break;
            default:
                mMinutelyTime.setText(context.getString(R.string.precipitation_summary_within));
                break;
        }
    }

    /** Whole minutes until the transition, clamped to 1 so a passed forecast never shows 0 / negative. */
    static int minutesUntil(long deltaMs) {
        return (int) Math.max(1, Math.ceil(deltaMs / 60000.0));
    }

    /** ms until the ceil-minute countdown next changes value; a full minute when exactly on a boundary. */
    static long nextMinuteTickDelay(long deltaMs) {
        return ((deltaMs - 1) % 60000L) + 1L;
    }

    /** An empty list answers false, so the card stays away when the source has no minutely block. */
    private static boolean hasPrecipitation(List<Minutely> minutelyList) {
        for (Minutely m : minutelyList) {
            if (m.isPrecipitation()) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    @Override
    protected Animator getEnterAnimator(List<Animator> pendingAnimatorList) {
        Animator a = ObjectAnimator.ofFloat(itemView, "alpha", 0f, 1f);
        a.setDuration(300);
        a.setStartDelay(100);
        a.setInterpolator(new FastOutSlowInInterpolator());
        return a;
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void onEnterScreen() {
        super.onEnterScreen();
        mTemperature.setNumberString(
                String.format("%d", mTemperatureUnit.getValueWithoutUnit(mTemperatureCFrom)),
                String.format("%d", mTemperatureUnit.getValueWithoutUnit(mTemperatureCTo))
        );
    }

    @Override
    public void onRecycleView() {
        mMinutelyTime.removeCallbacks(mMinutelyTicker);
    }

    /**
     * Pins the text block on screen while the list scrolls: the cards slide up underneath it until
     * one of them — the alert or minutely card when present, else the card list itself — reaches
     * the block's bottom and carries it away. Up to that point the translation grows with the
     * scroll to hold the block still; from then on it holds at the collision distance and the
     * block, being part of the same item, simply scrolls with the list again.
     */
    public void pinTextBlock(int scrollY) {
        mTextBlock.setTranslationY(
                Math.min(Math.max(scrollY, 0), getTextPinDistance())
        );
    }

    /** Scroll offset at which the first card below touches the text block's bottom. */
    public int getTextPinDistance() {
        int textBottom = mTextBlock.getBottom();
        int distance = mContainer.getMeasuredHeight() - textBottom;
        if (mAlertCard.getVisibility() == View.VISIBLE) {
            distance = Math.min(distance, mAlertCard.getTop() - textBottom);
        }
        if (mMinutelyCard.getVisibility() == View.VISIBLE) {
            distance = Math.min(distance, mMinutelyCard.getTop() - textBottom);
        }
        return Math.max(distance, 0);
    }
}
