package jp.tkgktyk.xposed.niwatori;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import jp.tkgktyk.flyinglayout.FlyingLayout;

/**
 * Created by tkgktyk on 2015/02/13.
 */
public class FlyingHelper  extends FlyingLayout.Helper {
    private final NFW.Settings mSettings;
    private final Rect mChildRect = new Rect();
    private final Method mGetPaddingLeftWithForeground;
    private final Method mGetPaddingTopWithForeground;
    private final Method mGetPaddingRightWithForeground;
    private final Method mGetPaddingBottomWithForeground;

    private final InputMethodManager mInputMethodManager;
    private Drawable mBoundaryDrawable;
    private boolean mBoundaryShown = false;

    public FlyingHelper(ViewGroup view, int frameLayoutHierarchy, NFW.Settings settings)
            throws NoSuchMethodException {
        super(view);
        mSettings = settings;

        mGetPaddingLeftWithForeground = getPaddingMethod(view, frameLayoutHierarchy, "getPaddingLeftWithForeground");
        mGetPaddingTopWithForeground =  getPaddingMethod(view, frameLayoutHierarchy, "getPaddingTopWithForeground");
        mGetPaddingRightWithForeground =  getPaddingMethod(view, frameLayoutHierarchy, "getPaddingRightWithForeground");
        mGetPaddingBottomWithForeground =  getPaddingMethod(view, frameLayoutHierarchy, "getPaddingBottomWithForeground");

        mInputMethodManager = (InputMethodManager) view.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);

        reinitialize();
    }

    private Method getPaddingMethod(Object object, int hier, String methodName)
            throws NoSuchMethodException{
        Class<?> cls = object.getClass();
        for (int i = 0; i < hier; ++i) {
            cls = cls.getSuperclass();
        }
        final Method method = cls.getDeclaredMethod(methodName);
        if (!method.isAccessible()) {
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                public Object run() {
                    method.setAccessible(true);
                    return null;
                }
            });
        }
        return method;
    }

    private void reinitialize() {
        setSpeed(mSettings.speed);
        setTouchEventEnabled(false);
        setUseContainer(true);
        setOnFlyingEventListener(new FlyingLayout.SimpleOnFlyingEventListener() {
            @Override
            public void onClickOutside(ViewGroup v) {
                resetState();
            }

            @Override
            public void onDoubleClickOutside(ViewGroup v) {
                pin();
            }
        });

        prepareBoundary();
    }

    private void prepareBoundary() {
        Context flyContext = null;
        try {
            flyContext = getAttachedView().getContext().createPackageContext(
                    NFW.PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
        if (flyContext != null) {
            mBoundaryDrawable = flyContext.getResources().getDrawable(R.drawable.notify_flying);
        }
    }

    private void setBoundaryShown(boolean shown) {
        mBoundaryShown = shown;
        getAttachedView().invalidate();
    }

    public boolean isFlying() {
        return getTouchEventEnabled();
    }

    private void disableFlying() {
        setTouchEventEnabled(false);
        setBoundaryShown(false);
    }

    private void enableFlying() {
        setTouchEventEnabled(true);
        setBoundaryShown(true);
    }

    public void performAction(String action) {
        if (action.equals(NFW.ACTION_TOGGLE)) {
            toggle();
        } else if (action.equals(NFW.ACTION_PIN)) {
            pin();
        } else if (action.equals(NFW.ACTION_PIN_OR_RESET)) {
            pinOrReset();
        } else if (action.equals(NFW.ACTION_RESET)) {
            resetState();
        }
    }

    private void toggle() {
        if (isFlying()) {
            disableFlying();
            goHome(mSettings.animation);
        } else {
            if (staysHome()) {
                moveToInitialPosition(false);
                hideSoftInputMethod();
            }
            enableFlying();
        }
    }

    private void pin() {
        if (staysHome()) {
            moveToInitialPosition(true);
            hideSoftInputMethod();
            disableFlying();
        } else if (isFlying()) {
            disableFlying();
        } else {
            enableFlying();
            hideSoftInputMethod();
        }
    }

    private void pinOrReset() {
        if (staysHome()) {
            moveToInitialPosition(true);
            hideSoftInputMethod();
        } else {
            goHome(mSettings.animation);
        }
        disableFlying();
    }

    private boolean moveToInitialPosition(boolean pin) {
        final InitialPosition pos = new InitialPosition(mSettings.initialXp, mSettings.initialYp);
        if (pin && pos.getXp() == 0 && pos.getYp() == 0) {
            // default position for pin
            pos.setXp(0);
            pos.setYp(50);
        }
        final int x = pos.getX(getAttachedView());
        final int y = pos.getY(getAttachedView());
        boolean moved = false;
        if (x != 0 || y != 0) {
            moved = true;
            moveWithoutSpeed(x, y, mSettings.animation);
        }
        return moved;
    }

    public void resetState() {
        if (isFlying() || !staysHome()) {
            disableFlying();
            // goHome must be placed after pin() for "Reset when collapsed"
            // option.
            goHome(mSettings.animation);
        }
    }

    private void hideSoftInputMethod() {
        mInputMethodManager.hideSoftInputFromWindow(getAttachedView().getWindowToken(), 0);
    }

    @SuppressLint("NewApi")
    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final boolean forceLeftGravity = false;
        final ViewGroup view = getAttachedView();
        final Rect boundaryRect = getBoundaryRect();

        final int count = view.getChildCount();

        int parentLeft, parentRight, parentTop, parentBottom;
        try {
            parentLeft = (Integer) mGetPaddingLeftWithForeground.invoke(view);
            parentRight = right - left - (Integer) mGetPaddingRightWithForeground.invoke(view);

            parentTop = (Integer) mGetPaddingTopWithForeground.invoke(view);
            parentBottom = bottom - top - (Integer) mGetPaddingBottomWithForeground.invoke(view);
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }

        XposedHelpers.setBooleanField(view, "mForegroundBoundsChanged", true);

        boundaryRect.setEmpty();
        for (int i = 0; i < count; i++) {
            final View child = view.getChildAt(i);
            if (child.getVisibility() != View.GONE) {
                final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();

                final int width = child.getMeasuredWidth();
                final int height = child.getMeasuredHeight();

                int childLeft;
                int childTop;

                int gravity = lp.gravity;
                if (gravity == -1) {
                    gravity = DEFAULT_CHILD_GRAVITY;
                }

                final int layoutDirection =
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) ?
                                view.getLayoutDirection() : 0;
                final int absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection);
                final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.CENTER_HORIZONTAL:
                        childLeft = parentLeft + (parentRight - parentLeft - width) / 2
                                + lp.leftMargin - lp.rightMargin;
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
                        childTop = parentTop + (parentBottom - parentTop - height) / 2
                                + lp.topMargin - lp.bottomMargin;
                        break;
                    case Gravity.BOTTOM:
                        childTop = parentBottom - height - lp.bottomMargin;
                        break;
                    default:
                        childTop = parentTop + lp.topMargin;
                }

                mChildRect.set(childLeft, childTop, childLeft + width, childTop + height);
                if (!getUseContainer() || i == 0) {
                    mChildRect.offset(getOffsetX(), getOffsetY());
                    boundaryRect.union(mChildRect);
                }
                child.layout(mChildRect.left, mChildRect.top, mChildRect.right, mChildRect.bottom);
            }
        }
    }

    public void draw(Canvas canvas) {
        if (mBoundaryShown) {
            mBoundaryDrawable.setBounds(getBoundaryRect());
            mBoundaryDrawable.draw(canvas);
        }
    }

}