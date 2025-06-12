package com.yuyan;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class SignaturePad extends View {
    private static final String TAG = SignaturePad.class.getName();
    //View state
    private List<TimedPoint> mPoints;
    private boolean mIsEmpty;
    private float mLastTouchX;
    private float mLastTouchY;
    private float mLastVelocity;
    private float mLastWidth;
    private RectF mDirtyRect;
    // Cache
    private List<TimedPoint> mPointsCache = new ArrayList<>();
    private ControlTimedPoints mControlTimedPointsCached = new ControlTimedPoints();
    private Bezier mBezierCached = new Bezier();

    //Configurable parameters
    private int mMinWidth;
    private int mMaxWidth;
    private float mVelocityFilterWeight;
    private boolean mClearOnDoubleClick;

    //Double click detector
    private GestureDetector mGestureDetector;

    //Default attribute values
    private final int DEFAULT_ATTR_PEN_MAX_WIDTH_PX = 12;
    private final int DEFAULT_ATTR_PEN_MIN_WIDTH_PX = 6;
    private final int DEFAULT_ATTR_PEN_COLOR = Color.BLACK;
    private final float DEFAULT_ATTR_VELOCITY_FILTER_WEIGHT = 0.7f;
    private final boolean DEFAULT_ATTR_CLEAR_ON_DOUBLE_CLICK = false;

    private Paint mPaint = new Paint();
    private Bitmap mSignatureBitmap = null;
    private Canvas mSignatureBitmapCanvas = null;

    public SignaturePad(Context context, AttributeSet attrs) {
        super(context, attrs);
        mMinWidth =  convertDpToPx(DEFAULT_ATTR_PEN_MIN_WIDTH_PX);
        mMaxWidth =  convertDpToPx(DEFAULT_ATTR_PEN_MAX_WIDTH_PX);
        mPaint.setColor( DEFAULT_ATTR_PEN_COLOR);
        mVelocityFilterWeight = DEFAULT_ATTR_VELOCITY_FILTER_WEIGHT;
        mClearOnDoubleClick =  DEFAULT_ATTR_CLEAR_ON_DOUBLE_CLICK;
        //Fixed parameters
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        //Dirty rectangle to update only the changed portion of the view
        mDirtyRect = new RectF();
        clearView();
        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                return onDoubleClick();
            }
        });
    }

    public void clearView() {
        mPoints = new ArrayList<>();
        mLastVelocity = 0;
        mLastWidth = (mMinWidth + mMaxWidth) / 2;

        if (mSignatureBitmap != null) {
            mSignatureBitmap = null;
            ensureSignatureBitmap();
        }

        setIsEmpty(true);

        invalidate();
    }

    public void clear() {
        this.clearView();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled())
            return false;

        float eventX = event.getX();
        float eventY = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                mPoints.clear();
                if (mGestureDetector.onTouchEvent(event)) break;
                mLastTouchX = eventX;
                mLastTouchY = eventY;
                addPoint(getNewPoint(eventX, eventY));
            case MotionEvent.ACTION_MOVE:
                resetDirtyRect(eventX, eventY);
                addPoint(getNewPoint(eventX, eventY));
                setIsEmpty(false);
                break;

            case MotionEvent.ACTION_UP:
                resetDirtyRect(eventX, eventY);
                addPoint(getNewPoint(eventX, eventY));
                getParent().requestDisallowInterceptTouchEvent(true);
                break;

            default:
                return false;
        }
        invalidate(
                (int) (mDirtyRect.left - mMaxWidth),
                (int) (mDirtyRect.top - mMaxWidth),
                (int) (mDirtyRect.right + mMaxWidth),
                (int) (mDirtyRect.bottom + mMaxWidth));

        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mSignatureBitmap != null) {
            canvas.drawBitmap(mSignatureBitmap, 0, 0, mPaint);
        }
    }

    public boolean isEmpty() {
        return mIsEmpty;
    }

    private boolean onDoubleClick() {
        if (mClearOnDoubleClick) {
            this.clearView();
            return true;
        }
        return false;
    }

    private TimedPoint getNewPoint(float x, float y) {
        int mCacheSize = mPointsCache.size();
        TimedPoint timedPoint;
        if (mCacheSize == 0) {
            // Cache is empty, create a new point
            timedPoint = new TimedPoint();
        } else {
            // Get point from cache
            timedPoint = mPointsCache.remove(mCacheSize - 1);
        }

        return timedPoint.set(x, y);
    }

    private void recyclePoint(TimedPoint point) {
        mPointsCache.add(point);
    }

    private void addPoint(TimedPoint newPoint) {
        mPoints.add(newPoint);

        int pointsCount = mPoints.size();
        if (pointsCount > 3) {

            ControlTimedPoints tmp = calculateCurveControlPoints(mPoints.get(0), mPoints.get(1), mPoints.get(2));
            TimedPoint c2 = tmp.c2;
            recyclePoint(tmp.c1);

            tmp = calculateCurveControlPoints(mPoints.get(1), mPoints.get(2), mPoints.get(3));
            TimedPoint c3 = tmp.c1;
            recyclePoint(tmp.c2);

            Bezier curve = mBezierCached.set(mPoints.get(1), c2, c3, mPoints.get(2));

            TimedPoint startPoint = curve.startPoint;
            TimedPoint endPoint = curve.endPoint;

            float velocity = endPoint.velocityFrom(startPoint);
            velocity = Float.isNaN(velocity) ? 0.0f : velocity;

            velocity = mVelocityFilterWeight * velocity
                    + (1 - mVelocityFilterWeight) * mLastVelocity;

            // The new width is a function of the velocity. Higher velocities
            // correspond to thinner strokes.
            float newWidth = strokeWidth(velocity);

            // The Bezier's width starts out as last curve's final width, and
            // gradually changes to the stroke width just calculated. The new
            // width calculation is based on the velocity between the Bezier's
            // start and end mPoints.
            addBezier(curve, mLastWidth, newWidth);

            mLastVelocity = velocity;
            mLastWidth = newWidth;

            // Remove the first element from the list,
            // so that we always have no more than 4 mPoints in mPoints array.
            recyclePoint(mPoints.remove(0));

            recyclePoint(c2);
            recyclePoint(c3);

        } else if (pointsCount == 1) {
            // To reduce the initial lag make it work with 3 mPoints
            // by duplicating the first point
            TimedPoint firstPoint = mPoints.get(0);
            mPoints.add(getNewPoint(firstPoint.x, firstPoint.y));
        }
    }

    private void addBezier(Bezier curve, float startWidth, float endWidth) {
        ensureSignatureBitmap();
        float originalWidth = mPaint.getStrokeWidth();
        float widthDelta = endWidth - startWidth;
        float drawSteps = (float) Math.ceil(curve.length());

        for (int i = 0; i < drawSteps; i++) {
            // Calculate the Bezier (x, y) coordinate for this step.
            float t = ((float) i) / drawSteps;
            float tt = t * t;
            float ttt = tt * t;
            float u = 1 - t;
            float uu = u * u;
            float uuu = uu * u;

            float x = uuu * curve.startPoint.x;
            x += 3 * uu * t * curve.control1.x;
            x += 3 * u * tt * curve.control2.x;
            x += ttt * curve.endPoint.x;

            float y = uuu * curve.startPoint.y;
            y += 3 * uu * t * curve.control1.y;
            y += 3 * u * tt * curve.control2.y;
            y += ttt * curve.endPoint.y;

            // Set the incremental stroke width and draw.
            mPaint.setStrokeWidth(startWidth + ttt * widthDelta);
            mSignatureBitmapCanvas.drawPoint(x, y, mPaint);
            expandDirtyRect(x, y);
        }

        mPaint.setStrokeWidth(originalWidth);
    }

    private ControlTimedPoints calculateCurveControlPoints(TimedPoint s1, TimedPoint s2, TimedPoint s3) {
        float dx1 = s1.x - s2.x;
        float dy1 = s1.y - s2.y;
        float dx2 = s2.x - s3.x;
        float dy2 = s2.y - s3.y;

        float m1X = (s1.x + s2.x) / 2.0f;
        float m1Y = (s1.y + s2.y) / 2.0f;
        float m2X = (s2.x + s3.x) / 2.0f;
        float m2Y = (s2.y + s3.y) / 2.0f;

        float l1 = (float) Math.sqrt(dx1 * dx1 + dy1 * dy1);
        float l2 = (float) Math.sqrt(dx2 * dx2 + dy2 * dy2);

        float dxm = (m1X - m2X);
        float dym = (m1Y - m2Y);
        float k = l2 / (l1 + l2);
        if (Float.isNaN(k)) k = 0.0f;
        float cmX = m2X + dxm * k;
        float cmY = m2Y + dym * k;

        float tx = s2.x - cmX;
        float ty = s2.y - cmY;

        return mControlTimedPointsCached.set(getNewPoint(m1X + tx, m1Y + ty), getNewPoint(m2X + tx, m2Y + ty));
    }

    private float strokeWidth(float velocity) {
        return Math.max(mMaxWidth / (velocity + 1), mMinWidth);
    }

    /**
     * Called when replaying history to ensure the dirty region includes all
     * mPoints.
     *
     * @param historicalX the previous x coordinate.
     * @param historicalY the previous y coordinate.
     */
    private void expandDirtyRect(float historicalX, float historicalY) {
        if (historicalX < mDirtyRect.left) {
            mDirtyRect.left = historicalX;
        } else if (historicalX > mDirtyRect.right) {
            mDirtyRect.right = historicalX;
        }
        if (historicalY < mDirtyRect.top) {
            mDirtyRect.top = historicalY;
        } else if (historicalY > mDirtyRect.bottom) {
            mDirtyRect.bottom = historicalY;
        }
    }

    /**
     * Resets the dirty region when the motion event occurs.
     *
     * @param eventX the event x coordinate.
     * @param eventY the event y coordinate.
     */
    private void resetDirtyRect(float eventX, float eventY) {

        // The mLastTouchX and mLastTouchY were set when the ACTION_DOWN motion event occurred.
        mDirtyRect.left = Math.min(mLastTouchX, eventX);
        mDirtyRect.right = Math.max(mLastTouchX, eventX);
        mDirtyRect.top = Math.min(mLastTouchY, eventY);
        mDirtyRect.bottom = Math.max(mLastTouchY, eventY);
    }

    private void setIsEmpty(boolean newValue) {
        mIsEmpty = newValue;
    }

    private void ensureSignatureBitmap() {
        if (mSignatureBitmap == null) {
            mSignatureBitmap = Bitmap.createBitmap(getWidth(), getHeight(),
                    Bitmap.Config.ARGB_8888);
            mSignatureBitmapCanvas = new Canvas(mSignatureBitmap);
        }
    }

    private int convertDpToPx(float dp) {
        return Math.round(getContext().getResources().getDisplayMetrics().density * dp);
    }
}
