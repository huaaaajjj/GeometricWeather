package wangdaye.com.geometricweather.common.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import wangdaye.com.geometricweather.R;
import wangdaye.com.geometricweather.common.basic.models.weather.Minutely;
import wangdaye.com.geometricweather.common.utils.DisplayUtils;

/**
 * The two-hour minute-by-minute precipitation window as one combined chart: a soft filled intensity
 * envelope, one column per minute over it (coloured by rain grade), and a trend line skimming the
 * column tops. It carries the absolute mm/min scale (小/中/大 guide lines + labels in a right-hand
 * gutter), dashed :00/:30 time guides with their clock labels along the bottom axis, and a centered
 * rounded frame.
 *
 * Intensity (mm/min) is only present on a live fetch — it is not persisted, so a cache read comes
 * back wet-or-dry only; wet minutes then fall back to a fixed "unknown" height ({@link
 * #UNKNOWN_COLUMN_FRACTION}) rather than a fabricated grade.
 */
public class PrecipitationBar extends View {

    /** Column width as a fraction of its slot. */
    private static final float BAR_WIDTH_FRACTION = 0.7f;

    /** Hourly-rain grades converted to mm/min: the 小/中/大 boundaries and the chart ceiling. */
    private static final float THRESHOLD_LIGHT = 2.5f / 60f;
    private static final float THRESHOLD_MODERATE = 8f / 60f;
    private static final float THRESHOLD_HEAVY = 16f / 60f;
    private static final float SCALE_MAX = 24f / 60f;

    /** Height for wet minutes whose intensity never reached us (a cache read). */
    private static final float UNKNOWN_COLUMN_FRACTION = 0.45f;

    /** Alpha for 小/中/大/暴 so a column's colour reads its grade; index by intensity level. */
    private static final int[] LEVEL_ALPHA = {130, 180, 215, 255};
    private static final int UNKNOWN_ALPHA = 150;

    @Nullable private List<Minutely> mMinutelyList;

    /** Precomputed :00 / :30 marks: the slot index each falls on, and its clock-time label. */
    private int[] mMarkIndices = new int[0];
    private String[] mMarkLabels = new String[0];
    private final SimpleDateFormat mMarkTimeFormat;

    private final Paint mBarPaint;
    private final Paint mFramePaint;
    private final Paint mAxisLinePaint;
    private final Paint mAxisTextPaint;
    private final Paint mCurveFillPaint;
    private final Paint mCurveStrokePaint;

    private final String mLabelHeavy;
    private final String mLabelModerate;
    private final String mLabelLight;
    private final float mCornerRadius;

    @ColorInt private int mPrecipitationColor;
    @ColorInt private int mFrameColor;
    @ColorInt private int mAxisColor;

    // Cached curve gradient, rebuilt only when the plot height or colour changes (never per frame).
    @Nullable private Shader mCurveShader;
    private float mShaderBottom = -1f;
    @ColorInt private int mShaderColor;

    public PrecipitationBar(Context context) {
        this(context, null);
    }

    public PrecipitationBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PrecipitationBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = getResources().getDisplayMetrics().density;
        mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFramePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mAxisLinePaint = new Paint();
        mAxisLinePaint.setStyle(Paint.Style.STROKE);
        mAxisLinePaint.setStrokeWidth(density);
        mAxisLinePaint.setPathEffect(new DashPathEffect(new float[]{4f, 4f}, 0f));
        mCurveFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCurveFillPaint.setStyle(Paint.Style.FILL);
        mCurveStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCurveStrokePaint.setStyle(Paint.Style.STROKE);
        mCurveStrokePaint.setStrokeWidth(density * 1.5f);
        mCurveStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        mCurveStrokePaint.setStrokeJoin(Paint.Join.ROUND);
        mAxisTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mAxisTextPaint.setTextSize(getResources().getDisplayMetrics().scaledDensity * 10f);
        mMarkTimeFormat = new SimpleDateFormat(
                DisplayUtils.is12Hour(context) ? "h:mm" : "HH:mm", Locale.getDefault());
        // Cached once: onDraw runs per frame during animations and must not touch resources.
        mLabelHeavy = context.getString(R.string.precipitation_level_heavy);
        mLabelModerate = context.getString(R.string.precipitation_level_moderate);
        mLabelLight = context.getString(R.string.precipitation_level_light);
        mCornerRadius = density * 6f;
        mAxisColor = mPrecipitationColor;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mMinutelyList == null || mMinutelyList.isEmpty()) {
            return;
        }

        // 大/中/小 labels live in a gutter OUTSIDE the frame on the right; the same width is inset on
        // the left so the frame stays centered. The bottom strip holds the :00/:30 clock labels.
        float gutter = axisGutter();
        float width = getMeasuredWidth();
        float height = getMeasuredHeight();
        float axisHeight = mAxisTextPaint.getTextSize() * 1.7f;
        float plotLeft = gutter;
        float plotWidth = width - gutter * 2f;
        float plotBottom = height - axisHeight;
        float itemWidth = plotWidth / mMinutelyList.size();
        boolean rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;

        RectF frame = new RectF(plotLeft, 0, plotLeft + plotWidth, plotBottom);
        Path clip = new Path();
        clip.addRoundRect(frame, mCornerRadius, mCornerRadius, Path.Direction.CW);
        mFramePaint.setColor(mFrameColor);
        canvas.drawRoundRect(frame, mCornerRadius, mCornerRadius, mFramePaint);

        canvas.save();
        canvas.clipPath(clip);
        mAxisLinePaint.setColor(mAxisColor);
        drawThresholdLine(canvas, plotLeft, plotWidth, plotBottom, THRESHOLD_HEAVY);
        drawThresholdLine(canvas, plotLeft, plotWidth, plotBottom, THRESHOLD_MODERATE);
        drawThresholdLine(canvas, plotLeft, plotWidth, plotBottom, THRESHOLD_LIGHT);
        drawTimeGuides(canvas, plotLeft, plotWidth, itemWidth, plotBottom, rtl);
        // Combined: a soft filled envelope, per-minute columns over it, then a trend line on top.
        drawCurveFill(canvas, plotLeft, plotWidth, itemWidth, plotBottom, rtl);
        drawBars(canvas, plotLeft, plotWidth, itemWidth, plotBottom, rtl);
        drawCurveStroke(canvas, plotLeft, plotWidth, itemWidth, plotBottom, rtl);
        canvas.restore();

        drawGutterLabels(canvas, width, plotBottom);
        drawTimeAxisLabels(canvas, plotLeft, plotWidth, itemWidth, plotBottom, rtl);
    }

    private void drawThresholdLine(Canvas canvas, float plotLeft, float plotWidth,
                                   float plotBottom, float threshold) {
        float y = yFor(threshold, plotBottom);
        canvas.drawLine(plotLeft, y, plotLeft + plotWidth, y, mAxisLinePaint);
    }

    /** Vertical dashed guides up the plot at each :00 / :30 slot. */
    private void drawTimeGuides(Canvas canvas, float plotLeft, float plotWidth,
                                float itemWidth, float plotBottom, boolean rtl) {
        for (int index : mMarkIndices) {
            float x = sampleCenterX(index, plotLeft, plotWidth, itemWidth, rtl);
            canvas.drawLine(x, 0, x, plotBottom, mAxisLinePaint);
        }
    }

    /** The intensity polyline over every minute's top; dry minutes sit on the baseline. */
    private Path buildLinePath(float plotLeft, float plotWidth, float itemWidth,
                               float plotBottom, boolean rtl) {
        Path line = new Path();
        for (int i = 0; i < mMinutelyList.size(); i++) {
            float x = sampleCenterX(i, plotLeft, plotWidth, itemWidth, rtl);
            float y = columnTop(mMinutelyList.get(i), plotBottom);
            if (i == 0) {
                line.moveTo(x, y);
            } else {
                line.lineTo(x, y);
            }
        }
        return line;
    }

    /** Soft gradient envelope under the intensity curve. */
    private void drawCurveFill(Canvas canvas, float plotLeft, float plotWidth,
                               float itemWidth, float plotBottom, boolean rtl) {
        int n = mMinutelyList.size();
        Path fill = buildLinePath(plotLeft, plotWidth, itemWidth, plotBottom, rtl);
        fill.lineTo(sampleCenterX(n - 1, plotLeft, plotWidth, itemWidth, rtl), plotBottom);
        fill.lineTo(sampleCenterX(0, plotLeft, plotWidth, itemWidth, rtl), plotBottom);
        fill.close();
        ensureCurveShader(plotBottom);
        canvas.drawPath(fill, mCurveFillPaint);
    }

    /** Trend line skimming the column tops, drawn over the bars. */
    private void drawCurveStroke(Canvas canvas, float plotLeft, float plotWidth,
                                 float itemWidth, float plotBottom, boolean rtl) {
        mCurveStrokePaint.setColor(mPrecipitationColor);
        canvas.drawPath(buildLinePath(plotLeft, plotWidth, itemWidth, plotBottom, rtl),
                mCurveStrokePaint);
    }

    /** One rounded column per wet minute, its colour reading the rain grade. */
    private void drawBars(Canvas canvas, float plotLeft, float plotWidth,
                          float itemWidth, float plotBottom, boolean rtl) {
        float barWidth = itemWidth * BAR_WIDTH_FRACTION;
        float barMargin = (itemWidth - barWidth) / 2f;
        float radius = barWidth / 2f;
        for (int i = 0; i < mMinutelyList.size(); i++) {
            Minutely m = mMinutelyList.get(i);
            if (!m.isPrecipitation()) {
                continue;
            }
            float left = sampleCenterX(i, plotLeft, plotWidth, itemWidth, rtl) - itemWidth / 2f;
            float top = columnTop(m, plotBottom);
            mBarPaint.setColor(levelColor(m));
            canvas.drawRoundRect(
                    left + barMargin, top, left + barMargin + barWidth, plotBottom,
                    radius, radius, mBarPaint);
        }
    }

    /** 大/中/小 right-aligned inside the right-hand gutter, at their threshold heights. */
    private void drawGutterLabels(Canvas canvas, float width, float plotBottom) {
        mAxisTextPaint.setColor(mAxisColor);
        float right = width - mAxisTextPaint.getTextSize() / 4f;
        float textSize = mAxisTextPaint.getTextSize();
        drawRightLabel(canvas, mLabelHeavy, right, yFor(THRESHOLD_HEAVY, plotBottom) + textSize / 2f);
        drawRightLabel(canvas, mLabelModerate, right, yFor(THRESHOLD_MODERATE, plotBottom) + textSize / 2f);
        drawRightLabel(canvas, mLabelLight, right, yFor(THRESHOLD_LIGHT, plotBottom) + textSize / 2f);
    }

    private void drawRightLabel(Canvas canvas, String text, float right, float baselineY) {
        canvas.drawText(text, right - mAxisTextPaint.measureText(text), baselineY, mAxisTextPaint);
    }

    /** The clock time under each :00 / :30 guide, along the bottom axis, centered on its line. */
    private void drawTimeAxisLabels(Canvas canvas, float plotLeft, float plotWidth,
                                    float itemWidth, float plotBottom, boolean rtl) {
        mAxisTextPaint.setColor(mAxisColor);
        float baseline = plotBottom + mAxisTextPaint.getTextSize() * 1.2f;
        float plotRight = plotLeft + plotWidth;
        for (int k = 0; k < mMarkIndices.length; k++) {
            float x = sampleCenterX(mMarkIndices[k], plotLeft, plotWidth, itemWidth, rtl);
            String label = mMarkLabels[k];
            float w = mAxisTextPaint.measureText(label);
            float left = Math.max(plotLeft, Math.min(x - w / 2f, plotRight - w));
            canvas.drawText(label, left, baseline, mAxisTextPaint);
        }
    }

    /** Height of intensity {@code t} above the baseline (plot top = 0, so plotHeight = plotBottom). */
    private float yFor(float t, float plotBottom) {
        return plotBottom * (1f - Math.min(t, SCALE_MAX) / SCALE_MAX);
    }

    /** Top y of a minute's column; dry minutes sit on the baseline, unknown intensity at a fixed band. */
    private float columnTop(Minutely m, float plotBottom) {
        if (!m.isPrecipitation()) {
            return plotBottom;
        }
        Float intensity = m.getIntensity();
        float t = intensity == null ? SCALE_MAX * UNKNOWN_COLUMN_FRACTION
                : Math.min(intensity, SCALE_MAX);
        return yFor(t, plotBottom);
    }

    /** Center x of slot {@code index}, left→right or right→left, matching the column layout. */
    private float sampleCenterX(int index, float plotLeft, float plotWidth,
                                float itemWidth, boolean rtl) {
        return rtl ? plotLeft + plotWidth - (index + 0.5f) * itemWidth
                : plotLeft + (index + 0.5f) * itemWidth;
    }

    /** Width of the right-hand gutter that holds the 大/中/小 labels, outside the frame. */
    private float axisGutter() {
        return mAxisTextPaint.measureText(mLabelHeavy) + mAxisTextPaint.getTextSize() / 4f;
    }

    /** Vertical fill gradient for the curve, rebuilt only when the plot height or colour changes. */
    private void ensureCurveShader(float plotBottom) {
        if (mCurveShader != null && mShaderBottom == plotBottom && mShaderColor == mPrecipitationColor) {
            return;
        }
        mCurveShader = new LinearGradient(
                0, 0, 0, plotBottom,
                ColorUtils.setAlphaComponent(mPrecipitationColor, 110),
                ColorUtils.setAlphaComponent(mPrecipitationColor, 12),
                Shader.TileMode.CLAMP);
        mShaderBottom = plotBottom;
        mShaderColor = mPrecipitationColor;
        mCurveFillPaint.setShader(mCurveShader);
    }

    /** Precipitation colour at an alpha that reads the minute's grade (bars style). */
    @ColorInt
    private int levelColor(Minutely m) {
        Float intensity = m.getIntensity();
        int alpha;
        if (intensity == null) {
            alpha = UNKNOWN_ALPHA;
        } else if (intensity >= THRESHOLD_HEAVY) {
            alpha = LEVEL_ALPHA[3];
        } else if (intensity >= THRESHOLD_MODERATE) {
            alpha = LEVEL_ALPHA[2];
        } else if (intensity >= THRESHOLD_LIGHT) {
            alpha = LEVEL_ALPHA[1];
        } else {
            alpha = LEVEL_ALPHA[0];
        }
        return ColorUtils.setAlphaComponent(mPrecipitationColor, alpha);
    }

    public void setMinutelyList(@Nullable List<Minutely> minutelyList) {
        mMinutelyList = minutelyList;
        computeTimeMarks();
        invalidate();
    }

    /**
     * Find the slots whose wall-clock minute is :00 or :30 and cache their index + label, once per
     * bind — onDraw runs per frame during animations and must not allocate a Calendar each pass.
     */
    private void computeTimeMarks() {
        if (mMinutelyList == null || mMinutelyList.isEmpty()) {
            mMarkIndices = new int[0];
            mMarkLabels = new String[0];
            return;
        }
        Calendar calendar = Calendar.getInstance();
        List<Integer> indices = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < mMinutelyList.size(); i++) {
            calendar.setTime(mMinutelyList.get(i).getDate());
            int minute = calendar.get(Calendar.MINUTE);
            if (minute == 0 || minute == 30) {
                indices.add(i);
                labels.add(mMarkTimeFormat.format(mMinutelyList.get(i).getDate()));
            }
        }
        mMarkIndices = new int[indices.size()];
        for (int k = 0; k < indices.size(); k++) {
            mMarkIndices[k] = indices.get(k);
        }
        mMarkLabels = labels.toArray(new String[0]);
    }

    public void setPrecipitationColor(@ColorInt int precipitationColor) {
        mPrecipitationColor = precipitationColor;
        invalidate();
    }

    /** Fill of the rounded frame the chart lives in. */
    public void setFrameColor(@ColorInt int frameColor) {
        mFrameColor = frameColor;
        invalidate();
    }

    public void setAxisColor(@ColorInt int axisColor) {
        mAxisColor = axisColor;
        invalidate();
    }
}
