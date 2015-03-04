package jp.tkgktyk.flyinglayout;

import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.FrameLayout;

/**
 * Created by tkgktyk on 2014/01/01.
 */
public class FlyingLayout extends FrameLayout {
    public static final float DEFAULT_SPEED = 1.5f;
    public static final int DEFAULT_HORIZONTAL_PADDING = 0;
    public static final int DEFAULT_VERTICAL_PADDING = 0;
    public static final boolean DEFAULT_ENABLE_TOUCH_EVENT = true;
    public static final float DEFAULT_SLOP_SCALE = 1.0f;
    public static final boolean DEFAULT_USE_CONTAINER = false;
    private static final String TAG = FlyingLayout.class.getSimpleName();
    /**
     * Sentinel value for no current active pointer. Used by
     * {@link #mActivePointerId}.
     */
    private static final int INVALID_POINTER = -1;
    /**
     * ID of the active pointer. This is used to retain consistency during
     * drags/flings if multiple pointers are used.
     */
    private int mActivePointerId = INVALID_POINTER;
    private static int DEFAULT_CHILD_GRAVITY = Gravity.TOP | Gravity.START;
    private int mDefaultTouchSlop;
    private int mTouchSlop;
    /**
     * True if the user is currently dragging this ScrollView around. This is
     * not the same as 'is being flinged', which can be checked by
     * mScroller.isFinished() (flinging begins when the user lifts his finger).
     */
    private boolean mIsBeingDragged = false;
    /**
     * Position of the last motion event.
     */
    private int mLastMotionX;
    private int mLastMotionY;

    private float mSpeed;
    private int mHorizontalPadding;
    private int mVerticalPadding;
    private boolean mEnableTouchEventX;
    private boolean mEnableTouchEventY;
    private float mSlopScale;
    private boolean mUseContainer;
    private int mOffsetX;
    private int mOffsetY;

    private Rect mChildRect;
    private Rect mBoundaryRect;
    private OnFlyingEventListener mOnFlyingEventListener = new SimpleOnFlyingEventListener();
    private GestureDetector mGestureDetector;

    public FlyingLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialize();

        fetchAttribute(context, attrs, defStyle);
    }

    public FlyingLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FlyingLayout(Context context) {
        super(context);
        initialize();

        setSpeed(DEFAULT_SPEED);
        setHorizontalPadding(DEFAULT_HORIZONTAL_PADDING);
        setVerticalPadding(DEFAULT_VERTICAL_PADDING);
        setEnableTouchEventX(DEFAULT_ENABLE_TOUCH_EVENT);
        setEnableTouchEventY(DEFAULT_ENABLE_TOUCH_EVENT);
        setSlopScale(DEFAULT_SLOP_SCALE);
        setUseContainer(DEFAULT_USE_CONTAINER);
    }

    private void fetchAttribute(Context context, AttributeSet attrs,
                                int defStyle) {
        // get attributes specified in XML
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs,
                R.styleable.FlyingLayout, defStyle, 0);
        try {
            setSpeed(a.getFloat(R.styleable.FlyingLayout_speed, DEFAULT_SPEED));
            setHorizontalPadding(a.getDimensionPixelSize(
                    R.styleable.FlyingLayout_horizontalPadding,
                    DEFAULT_HORIZONTAL_PADDING));
            setVerticalPadding(a.getDimensionPixelSize(
                    R.styleable.FlyingLayout_verticalPadding,
                    DEFAULT_VERTICAL_PADDING));
            setEnableTouchEventX(a.getBoolean(
                    R.styleable.FlyingLayout_enableTouchEventX,
                    DEFAULT_ENABLE_TOUCH_EVENT));
            setEnableTouchEventY(a.getBoolean(
                    R.styleable.FlyingLayout_enableTouchEventY,
                    DEFAULT_ENABLE_TOUCH_EVENT));
            setSlopScale(a.getFloat(R.styleable.FlyingLayout_slopScale,
                    DEFAULT_SLOP_SCALE));
            setUseContainer(a.getBoolean(R.styleable.FlyingLayout_useContainer,
                    DEFAULT_USE_CONTAINER));
        } finally {
            a.recycle();
        }
    }

    private void initialize() {
        mDefaultTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        mChildRect = new Rect();
        mBoundaryRect = new Rect();

        mGestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (!insideOfContents(e)) {
                    mOnFlyingEventListener.onDoubleClickOutside(FlyingLayout.this);
                    return true;
                }
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (!insideOfContents(e)) {
                    mOnFlyingEventListener.onLongPressOutside(FlyingLayout.this);
                }
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (!insideOfContents(e)) {
                    mOnFlyingEventListener.onClickOutside(FlyingLayout.this);
                    return true;
                }
                return false;
            }

        });
    }

    public float getSpeed() {
        return mSpeed;
    }

    public void setSpeed(float speed) {
        mSpeed = speed;
    }

    public int getHorizontalPadding() {
        return mHorizontalPadding;
    }

    public void setHorizontalPadding(int padding) {
        mHorizontalPadding = padding;
    }

    public int getVerticalPadding() {
        return mVerticalPadding;
    }

    public void setVerticalPadding(int padding) {
        mVerticalPadding = padding;
    }

    public boolean getEnableTouchEventX() {
        return mEnableTouchEventX;
    }

    public void setEnableTouchEventX(boolean enable) {
        mEnableTouchEventX = enable;
    }

    public boolean getEnableTouchEventY() {
        return mEnableTouchEventY;
    }

    public void setEnableTouchEventY(boolean enable) {
        mEnableTouchEventY = enable;
    }

    public float getSlopScale() {
        return mSlopScale;
    }

    public void setSlopScale(float scale) {
        mSlopScale = scale;
        mTouchSlop = Math.round(mDefaultTouchSlop * scale);
    }

    public boolean getUseContainer() {
        return mUseContainer;
    }

    public void setUseContainer(boolean use) {
        mUseContainer = use;
    }

    public int getOffsetX() {
        return mOffsetX;
    }

    public void setOffsetX(int offset) {
        mOffsetX = offset;
        requestLayout();
    }

    public int getOffsetY() {
        return mOffsetY;
    }

    public void setOffsetY(int offset) {
        mOffsetY = offset;
        requestLayout();
    }

    public void setOffset(int x, int y) {
        mOffsetX = x;
        mOffsetY = y;
        requestLayout();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        /*
         * This method JUST determines whether we want to intercept the motion.
		 * If we return true, onMotionEvent will be called and we do the actual
		 * scrolling there.
		 */

		/*
         * Shortcut the most recurring case: the user is in the dragging state
		 * and he is moving his finger. We want to intercept this motion.
		 */
        final int action = ev.getAction();
        if ((action == MotionEvent.ACTION_MOVE) && mIsBeingDragged) {
            return true;
        }

        boolean locallyDrag = false;

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_MOVE: {
            /*
             * mIsBeingDragged == false, otherwise the shortcut would have
			 * caught it. Check whether the user has moved far enough from his
			 * original down touch.
			 */

			/*
             * Locally do absolute value. mLastMotionY is set to the y value of
			 * the down event.
			 */
                final int activePointerId = mActivePointerId;
                if (activePointerId == INVALID_POINTER) {
                    // If we don't have a valid id, the touch down wasn't on
                    // content.
                    break;
                }

                final int pointerIndex = ev.findPointerIndex(activePointerId);
                if (pointerIndex == -1) {
                    Log.e(TAG, "Invalid pointerId=" + activePointerId
                            + " in onInterceptTouchEvent");
                    break;
                }

                boolean isBeingDraggedX = false;
                boolean isBeingDraggedY = false;
                final int x = (int) ev.getX(pointerIndex);
                final int xDiff = Math.abs(x - mLastMotionX);
                final int y = (int) ev.getY(pointerIndex);
                final int yDiff = Math.abs(y - mLastMotionY);
                if (mEnableTouchEventX && (xDiff > mTouchSlop)) {
                    isBeingDraggedX = true;
                }
                if (mEnableTouchEventY && (yDiff > mTouchSlop)) {
                    isBeingDraggedY = true;
                }
                if (isBeingDraggedX || isBeingDraggedY) {
                    final ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                    locallyDrag = true;
                }
                break;
            }

            case MotionEvent.ACTION_DOWN: {
                final int x = (int) ev.getX();
                final int y = (int) ev.getY();
            /*
             * Remember location of down touch. ACTION_DOWN always refers to
			 * pointer index 0.
			 */
                mLastMotionX = x;
                mLastMotionY = y;
                mActivePointerId = ev.getPointerId(0);
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                // always does not intercept

			/* Release the drag */
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
        }
        /*
         * The only time we want to intercept motion events is if we are in the
		 * drag mode.
		 */
        return mIsBeingDragged || locallyDrag;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent ev) {
        final int action = ev.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                if (getChildCount() == 0) {
                    return false;
                }

                // Remember where the motion event started
                mLastMotionX = (int) ev.getX();
                mLastMotionY = (int) ev.getY();
                mActivePointerId = ev.getPointerId(0);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                final int activePointerIndex = ev
                        .findPointerIndex(mActivePointerId);
                if (activePointerIndex == -1) {
                    Log.e(TAG, "Invalid pointerId=" + mActivePointerId
                            + " in onTouchEvent");
                    break;
                }

                final int x = (int) ev.getX(activePointerIndex);
                int deltaX = x - mLastMotionX;
                final int y = (int) ev.getY(activePointerIndex);
                int deltaY = y - mLastMotionY;
                if (!mIsBeingDragged) {
                    boolean isBeingDraggedX = false;
                    boolean isBeingDraggedY = false;
                    if (mEnableTouchEventX && (Math.abs(deltaX) > mTouchSlop)) {
                        isBeingDraggedX = true;
                    }
                    if (mEnableTouchEventY && (Math.abs(deltaY) > mTouchSlop)) {
                        isBeingDraggedY = true;
                    }
                    if (isBeingDraggedX || isBeingDraggedY) {
                        final ViewParent parent = getParent();
                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                        mIsBeingDragged = true;
                        mOnFlyingEventListener.onDragStarted(this);
                    }
                }
                if (mIsBeingDragged) {
                    // Scroll to follow the motion event

                    move(deltaX, deltaY, false);
                    mLastMotionX = x;
                    mLastMotionY = y;
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (mIsBeingDragged) {
                    mOnFlyingEventListener.onDragFinished(this);
                    mActivePointerId = INVALID_POINTER;
                    mIsBeingDragged = false;
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                if (mIsBeingDragged && getChildCount() > 0) {
                    mOnFlyingEventListener.onDragFinished(this);
                    mActivePointerId = INVALID_POINTER;
                    mIsBeingDragged = false;
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int index = ev.getActionIndex();
                mLastMotionX = (int) ev.getX(index);
                mLastMotionY = (int) ev.getY(index);
                mActivePointerId = ev.getPointerId(index);
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                mLastMotionX = (int) ev.getX(ev.findPointerIndex(mActivePointerId));
                mLastMotionY = (int) ev.getY(ev.findPointerIndex(mActivePointerId));
                break;
        }
        mGestureDetector.onTouchEvent(ev);
        return true;
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            // TODO: Make this decision more intelligent.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastMotionX = (int) ev.getX(newPointerIndex);
            mLastMotionY = (int) ev.getY(newPointerIndex);
            mActivePointerId = ev.getPointerId(newPointerIndex);
        }
    }

    @SuppressLint("NewApi")
    @Override
    protected void onLayout(boolean changed, int left, int top, int right,
                            int bottom) {
        boolean forceLeftGravity = false;
        final int count = getChildCount();

        final int parentLeft = getPaddingLeft();
        final int parentRight = right - left - getPaddingRight();

        final int parentTop = getPaddingTop();
        final int parentBottom = bottom - top - getPaddingBottom();

        mBoundaryRect.setEmpty();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                final int width = child.getMeasuredWidth();
                final int height = child.getMeasuredHeight();

                int childLeft;
                int childTop;

                int gravity = lp.gravity;
                if (gravity == -1) {
                    gravity = DEFAULT_CHILD_GRAVITY;
                }

                final int layoutDirection = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) ? getLayoutDirection()
                        : 0;
                final int absoluteGravity = Gravity.getAbsoluteGravity(gravity,
                        layoutDirection);
                final int verticalGravity = gravity
                        & Gravity.VERTICAL_GRAVITY_MASK;

                switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.CENTER_HORIZONTAL:
                        childLeft = parentLeft + (parentRight - parentLeft - width)
                                / 2 + lp.leftMargin - lp.rightMargin;
                        break;
                    case Gravity.RIGHT:
                        if (!forceLeftGravity) {
                            childLeft = parentRight - width - lp.rightMargin;
                            break;
                        }
                    case Gravity.LEFT:
                    default:
                        childLeft = parentLeft + lp.leftMargin;
                }

                switch (verticalGravity) {
                    case Gravity.TOP:
                        childTop = parentTop + lp.topMargin;
                        break;
                    case Gravity.CENTER_VERTICAL:
                        childTop = parentTop + (parentBottom - parentTop - height)
                                / 2 + lp.topMargin - lp.bottomMargin;
                        break;
                    case Gravity.BOTTOM:
                        childTop = parentBottom - height - lp.bottomMargin;
                        break;
                    default:
                        childTop = parentTop + lp.topMargin;
                }

                mChildRect.set(childLeft, childTop, childLeft + width, childTop
                        + height);
                if (!getUseContainer() || i == 0) {
                    mChildRect.offset(mOffsetX, mOffsetY);
                    mBoundaryRect.union(mChildRect);
                }
                child.layout(mChildRect.left, mChildRect.top, mChildRect.right,
                        mChildRect.bottom);
            }
        }
    }

    protected int clamp(int src, int limit) {
        if (src > limit) {
            return limit;
        } else if (src < -limit) {
            return -limit;
        }
        return src;
    }

    public void move(int deltaX, int deltaY, boolean animation) {
        deltaX = Math.round(deltaX * mSpeed);
        deltaY = Math.round(deltaY * mSpeed);
        moveWithoutSpeed(deltaX, deltaY, animation);
    }

    public void moveWithoutSpeed(int deltaX, int deltaY, boolean animation) {
        int hLimit = getWidth() - getHorizontalPadding();
        int vLimit = getHeight() - getVerticalPadding();
        int newX = clamp(mOffsetX + deltaX, hLimit);
        int newY = clamp(mOffsetY + deltaY, vLimit);
        if (!animation) {
            setOffset(newX, newY);
        } else {
            Point start = new Point(mOffsetX, mOffsetY);
            Point end = new Point(newX, newY);
            ValueAnimator anim = ValueAnimator.ofObject(
                    new TypeEvaluator<Point>() {
                        @Override
                        public Point evaluate(float fraction, Point startValue,
                                              Point endValue) {
                            return new Point(Math.round(startValue.x
                                    + (endValue.x - startValue.x) * fraction),
                                    Math.round(startValue.y
                                            + (endValue.y - startValue.y)
                                            * fraction));
                        }
                    }, start, end);
            anim.setDuration(250);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    Point offset = (Point) animation.getAnimatedValue();
                    setOffset(offset.x, offset.y);
                }
            });
            anim.start();
        }
    }

    public void goHome(boolean animation) {
        moveWithoutSpeed(-mOffsetX, -mOffsetY, animation);
    }

    public boolean staysHome() {
        return mOffsetX == 0 && mOffsetY == 0;
    }

    public void rotate() {
        mOffsetX = Math.round(mOffsetX * 1f / getWidth() * getHeight());
        mOffsetY = Math.round(mOffsetY * 1f / getHeight() * getWidth());
    }

    private boolean insideOfContents(MotionEvent ev) {
        final int x = (int) ev.getX();
        final int y = (int) ev.getY();

        return mBoundaryRect.contains(x, y);
    }

    public OnFlyingEventListener getOnFlyingEventListener() {
        return mOnFlyingEventListener;
    }

    public void setOnFlyingEventListener(OnFlyingEventListener listener) {
        mOnFlyingEventListener = listener;
    }

    public interface OnFlyingEventListener {

        public void onDragStarted(FlyingLayout v);

        /**
         * callback when a moving event is finished.
         *
         * @param v
         */
        public void onDragFinished(FlyingLayout v);

        /**
         * callback when click event is happen at outside of contents.
         *
         * @param v
         */
        public void onClickOutside(FlyingLayout v);

        /**
         * callback when double click event is happen at outside of contents.
         *
         * @param v
         */
        public void onDoubleClickOutside(FlyingLayout v);

        /**
         * callback when long press event is happen at outside of contents.
         *
         * @param v
         */
        public void onLongPressOutside(FlyingLayout v);
    }

    public static class SimpleOnFlyingEventListener implements OnFlyingEventListener {
        @Override
        public void onDragStarted(FlyingLayout v) {
            // nothing to do
        }

        @Override
        public void onDragFinished(FlyingLayout v) {
            // nothing to do
        }

        @Override
        public void onClickOutside(FlyingLayout v) {
            // nothing to do
        }

        @Override
        public void onDoubleClickOutside(FlyingLayout v) {
            // nothing to do
        }

        @Override
        public void onLongPressOutside(FlyingLayout v) {
            // nothing to do
        }
    }
}