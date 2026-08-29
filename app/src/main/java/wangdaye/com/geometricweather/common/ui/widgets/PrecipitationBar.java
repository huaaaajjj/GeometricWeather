package wangdaye.com.geometricweather.common.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import java.util.List;

import wangdaye.com.geometricweather.common.basic.models.weather.Minutely;

/**
 * The two-hour minute-by-minute precipitation window as a bar chart: one thin column per minute,
 * its height proportional to the reported intensity (mm/min). Wet minutes without an intensity
 * (a cache read — intensity is not persisted) fall back to a uniform full height, which is what
 * the pre-chart version drew for every wet minute.
 */
public class PrecipitationBar extends View {

    /** Column width as a fraction of its slot, so adjacent bars stay visually separate. */
    private static final float BAR_WIDTH_FRACTION = 0.7f;

    @Nullable private List<Minutely> mMinutelyList;
    private final Paint mPaint;
    @ColorInt private int mPrecipitationColor;
    @ColorInt private int mBackgroundColor;

    public PrecipitationBar(Context context) {
        this(context, null);
    }

    public PrecipitationBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PrecipitationBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mPaint = new Paint();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setClipToOutline(true);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(
                            0,
                            0,
                            view.getMeasuredWidth(),
                            view.getMeasuredHeight(),
                            view.getMeasuredHeight() / 12f
                    );
                }
            });
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mMinutelyList == null || mMinutelyList.size() == 0) {
            return;
        }

        // Bars scale against the heaviest minute in the window: light rain still shows shape
        // instead of flat-lining against an absolute mm/min scale.
        float maxIntensity = 0f;
        boolean anyIntensity = false;
        for (Minutely m : mMinutelyList) {
            if (m.getIntensity() != null) {
                anyIntensity = true;
                maxIntensity = Math.max(maxIntensity, m.getIntensity());
            }
        }
        if (maxIntensity <= 0) {
            maxIntensity = 1f;
        }

        float itemWidth = 1.f * getMeasuredWidth() / mMinutelyList.size();
        float barWidth = itemWidth * BAR_WIDTH_FRACTION;
        float barMargin = (itemWidth - barWidth) / 2f;
        float height = getMeasuredHeight();

        canvas.drawColor(mBackgroundColor);
        mPaint.setColor(mPrecipitationColor);

        if (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            float right = getMeasuredWidth();
            for (Minutely m : mMinutelyList) {
                float left = right - itemWidth;
                if (m.isPrecipitation()) {
                    float top = height * columnFraction(m, anyIntensity, maxIntensity);
                    canvas.drawRect(left + barMargin, top, right - barMargin, height, mPaint);
                }
                right = left;
            }
        } else {
            float x = 0;
            for (Minutely m : mMinutelyList) {
                if (m.isPrecipitation()) {
                    float top = height * columnFraction(m, anyIntensity, maxIntensity);
                    canvas.drawRect(x + barMargin, top, x + barMargin + barWidth, height, mPaint);
                }
                x += itemWidth;
            }
        }
    }

    /**
     * Top of the column as a fraction of the view height: intensity relative to the window's
     * heaviest minute; a wet minute without an intensity (cache read) keeps the full height the
     * old flat bar drew.
     */
    private float columnFraction(Minutely m, boolean anyIntensity, float maxIntensity) {
        if (!anyIntensity || m.getIntensity() == null) {
            return 0f;
        }
        return 1f - (m.getIntensity() / maxIntensity);
    }

    public void setMinutelyList(@Nullable List<Minutely> minutelyList) {
        mMinutelyList = minutelyList;
        invalidate();
    }

    public void setPrecipitationColor(@ColorInt int precipitationColor) {
        mPrecipitationColor = precipitationColor;
        invalidate();
    }

    @Override
    public void setBackgroundColor(@ColorInt int backgroundColor) {
        mBackgroundColor = backgroundColor;
        invalidate();
    }
}
