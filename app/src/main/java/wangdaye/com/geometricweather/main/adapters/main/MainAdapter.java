package wangdaye.com.geometricweather.main.adapters.main;

import android.animation.Animator;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import wangdaye.com.geometricweather.common.basic.GeoActivity;
import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.appearance.CardDisplay;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.theme.weatherView.WeatherView;
import wangdaye.com.geometricweather.main.adapters.main.holder.AbstractMainViewHolder;
import wangdaye.com.geometricweather.main.adapters.main.holder.AirQualityViewHolder;
import wangdaye.com.geometricweather.main.adapters.main.holder.AllergenViewHolder;
import wangdaye.com.geometricweather.main.adapters.main.holder.AstroViewHolder;
import wangdaye.com.geometricweather.main.adapters.main.holder.DailyViewHolder;
import wangdaye.com.geometricweather.main.adapters.main.holder.DetailsViewHolder;
import wangdaye.com.geometricweather.main.adapters.main.holder.FooterViewHolder;
import wangdaye.com.geometricweather.main.adapters.main.holder.HeaderViewHolder;
import wangdaye.com.geometricweather.main.adapters.main.holder.HourlyViewHolder;
import wangdaye.com.geometricweather.theme.resource.providers.ResourceProvider;
import wangdaye.com.geometricweather.settings.SettingsManager;

public class MainAdapter extends RecyclerView.Adapter<AbstractMainViewHolder> {

    private GeoActivity mActivity;
    private RecyclerView mHost;
    private WeatherView mWeatherView;
    private @Nullable Location mLocation;
    private ResourceProvider mProvider;

    private List<Integer> mViewTypeList;
    private List<Animator> mPendingAnimatorList;
    private boolean mListAnimationEnabled;
    private boolean mItemAnimationEnabled;

    public MainAdapter(@NonNull GeoActivity activity, @NonNull RecyclerView host,
                       @NonNull WeatherView weatherView, @Nullable Location location,
                       @NonNull ResourceProvider provider,
                       boolean listAnimationEnabled, boolean itemAnimationEnabled) {
        update(activity, host, weatherView, location, provider, listAnimationEnabled, itemAnimationEnabled);
    }

    public void update(@NonNull GeoActivity activity, @NonNull RecyclerView host,
                       @NonNull WeatherView weatherView, @Nullable Location location,
                       @NonNull ResourceProvider provider,
                       boolean listAnimationEnabled, boolean itemAnimationEnabled) {
        mActivity = activity;
        mHost = host;
        mWeatherView = weatherView;
        mLocation = location;
        mProvider = provider;

        mViewTypeList = new ArrayList<>();
        mPendingAnimatorList = new ArrayList<>();
        mListAnimationEnabled = listAnimationEnabled;
        mItemAnimationEnabled = itemAnimationEnabled;

        if (location != null && location.getWeather() != null) {
            Weather weather = location.getWeather();
            List<CardDisplay> cardDisplayList = SettingsManager.getInstance(activity).getCardDisplayList();
            mViewTypeList.add(ViewType.HEADER);
            for (CardDisplay c : cardDisplayList) {
                if (c == CardDisplay.CARD_AIR_QUALITY
                        && (weather.getCurrent().getAirQuality() == null
                        || !weather.getCurrent().getAirQuality().isValid())) {
                    continue;
                }
                if (c == CardDisplay.CARD_ALLERGEN
                        && (weather.getDailyForecast().isEmpty()
                        || weather.getDailyForecast().get(0).getPollen() == null
                        || !weather.getDailyForecast().get(0).getPollen().isValid())) {
                    continue;
                }
                if (c == CardDisplay.CARD_SUNRISE_SUNSET
                        && (weather.getDailyForecast().isEmpty()
                        || weather.getDailyForecast().get(0).sun() == null
                        || !weather.getDailyForecast().get(0).sun().isValid())) {
                    continue;
                }
                mViewTypeList.add(getViewType(c));
            }
            mViewTypeList.add(ViewType.FOOTER);
        }
    }

    public void setNullWeather() {
        mViewTypeList = new ArrayList<>();
    }

    @NonNull
    @Override
    public AbstractMainViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case ViewType.HEADER:
                return new HeaderViewHolder(parent, mWeatherView);

            case ViewType.DAILY:
                return new DailyViewHolder(parent);

            case ViewType.HOURLY:
                return new HourlyViewHolder(parent);

            case ViewType.AIR_QUALITY:
                return new AirQualityViewHolder(parent);

            case ViewType.ALLERGEN:
                return new AllergenViewHolder(parent);

            case ViewType.ASTRO:
                return new AstroViewHolder(parent);

            case ViewType.DETAILS:
                return new DetailsViewHolder(parent);

            default: // FOOTER.
                return new FooterViewHolder(parent);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull AbstractMainViewHolder holder, int position) {
        assert mLocation != null;
        holder.onBindView(mActivity, mLocation, mProvider,
                mListAnimationEnabled, mItemAnimationEnabled);
        mHost.post(() -> holder.checkEnterScreen(mHost, mPendingAnimatorList, mListAnimationEnabled));
    }

    @Override
    public void onViewRecycled(@NonNull AbstractMainViewHolder holder) {
        holder.onRecycleView();
    }

    @Override
    public int getItemCount() {
        return mViewTypeList.size();
    }

    @Override
    public int getItemViewType(int position) {
        return mViewTypeList.get(position);
    }

    /** Scroll offset at which the first card reaches the header's pinned text block; -1 when the
     *  header is not attached (it is fully scrolled away or there is no weather to show). */
    public int getHeaderPinDistance() {
        AbstractMainViewHolder holder = findHeaderHolder();
        return holder == null ? -1 : ((HeaderViewHolder) holder).getTextPinDistance();
    }

    public void pinHeaderText(int scrollY) {
        AbstractMainViewHolder holder = findHeaderHolder();
        if (holder != null) {
            ((HeaderViewHolder) holder).pinTextBlock(scrollY);
        }
    }

    private AbstractMainViewHolder findHeaderHolder() {
        if (getItemCount() == 0) {
            return null;
        }
        AbstractMainViewHolder holder =
                (AbstractMainViewHolder) mHost.findViewHolderForAdapterPosition(0);
        return holder instanceof HeaderViewHolder ? holder : null;
    }

    public void onScroll() {
        AbstractMainViewHolder holder;
        for (int i = 0; i < getItemCount(); i ++) {
            holder = (AbstractMainViewHolder) mHost.findViewHolderForAdapterPosition(i);
            if (holder != null) {
                holder.checkEnterScreen(mHost, mPendingAnimatorList, mListAnimationEnabled);
            }
        }
    }

    private static int getViewType(CardDisplay cardDisplay) {
        switch (cardDisplay) {
            case CARD_DAILY_OVERVIEW:
                return ViewType.DAILY;

            case CARD_HOURLY_OVERVIEW:
                return ViewType.HOURLY;

            case CARD_AIR_QUALITY:
                return ViewType.AIR_QUALITY;

            case CARD_ALLERGEN:
                return ViewType.ALLERGEN;

            case CARD_SUNRISE_SUNSET:
                return ViewType.ASTRO;

            default: // CARD_LIFE_DETAILS.
                return ViewType.DETAILS;
        }
    }
}
