package wangdaye.com.geometricweather.main.adapters.main.holder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;

import wangdaye.com.geometricweather.R;
import wangdaye.com.geometricweather.common.basic.GeoActivity;
import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.main.adapters.main.FirstCardHeaderController;
import wangdaye.com.geometricweather.main.utils.MainThemeColorProvider;
import wangdaye.com.geometricweather.theme.ThemeManager;
import wangdaye.com.geometricweather.theme.resource.providers.ResourceProvider;
import wangdaye.com.geometricweather.theme.weatherView.WeatherThemeDelegate;

public abstract class AbstractMainCardViewHolder extends AbstractMainViewHolder {

    private FirstCardHeaderController mFirstCardHeaderController;

    @SuppressLint("ObjectAnimatorBinding")
    public AbstractMainCardViewHolder(@NonNull View view) {
        super(view);
    }

    @CallSuper
    public void onBindView(GeoActivity activity, @NonNull Location location,
                           @NonNull ResourceProvider provider,
                           boolean listAnimationEnabled, boolean itemAnimationEnabled, boolean firstCard) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled);

        CardView card = (CardView) itemView;
        styleAsHomeCard(card, location);

        if (firstCard) {
            mFirstCardHeaderController = new FirstCardHeaderController(activity, location);
            mFirstCardHeaderController.bind((LinearLayout) card.getChildAt(0));
        }
    }

    /** Also used for the alert / minutely cards, which the header block hosts rather than the list. */
    public static void styleAsHomeCard(CardView card, @NonNull Location location) {
        Context context = card.getContext();
        WeatherThemeDelegate delegate = ThemeManager
                .getInstance(context)
                .getWeatherThemeDelegate();

        card.setRadius(delegate.getHomeCardRadius(context));
        card.setElevation(delegate.getHomeCardElevation(context));
        card.setCardBackgroundColor(
                MainThemeColorProvider.getColor(location, R.attr.colorMainCardBackground)
        );

        int margins = delegate.getHomeCardMargins(context);
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) card.getLayoutParams();
        params.setMargins(margins, 0, margins, margins);
        card.setLayoutParams(params);
    }

    @SuppressLint("MissingSuperCall")
    @Deprecated
    @Override
    public void onBindView(Context context, @NonNull Location location,
                           @NonNull ResourceProvider provider,
                           boolean listAnimationEnabled, boolean itemAnimationEnabled) {
        throw new RuntimeException("Deprecated method.");
    }

    @Override
    public void onRecycleView() {
        super.onRecycleView();
        if (mFirstCardHeaderController != null) {
            mFirstCardHeaderController.unbind();
            mFirstCardHeaderController = null;
        }
    }
}
