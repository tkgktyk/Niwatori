package jp.tkgktyk.xposed.niwatori;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.view.HapticFeedbackConstants;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import de.robv.android.xposed.XposedHelpers;
import jp.tkgktyk.flyinglayout.FlyingLayout;

/**
 * Created by tkgktyk on 2015/02/13.
 */
public class FlyingHelper extends FlyingLayout.Helper {
    private static final String TAG = FlyingHelper.class.getSimpleName();

    private final InputMethodManager mInputMethodManager;
    private NFW.Settings mSettings;

    private final GradientDrawable mBoundaryDrawable = NFW.makeBoundaryDrawable(0, 0);
    private int mBoundaryWidth;
    private boolean mBoundaryShown = false;

    public FlyingHelper(FrameLayout view, int frameLayoutHierarchy, boolean useContainer,
                        NFW.Settings settings) throws NoSuchMethodException {
        super(view, frameLayoutHierarchy);

        mInputMethodManager = (InputMethodManager) view.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);

        initialize(useContainer, settings);
    }

    private void initialize(boolean useContainer, NFW.Settings settings) {
        final Context niwatoriContext = NFW.getNiwatoriContext(getAttachedView().getContext());
        if (niwatoriContext != null) {
            mBoundaryWidth = Math.round(niwatoriContext.getResources().getDimension(R.dimen.boundary_width));
            // flying padding
            final int padding = Math.round(niwatoriContext.getResources()
                    .getDimension(R.dimen.flying_view_padding));
            setHorizontalPadding(padding);
            setVerticalPadding(padding);
        }
        onSettingsLoaded(settings);
        setTouchEventEnabled(false);
        setUseContainer(useContainer);
        setOnFlyingEventListener(new FlyingLayout.SimpleOnFlyingEventListener() {
            @Override
            public void onClickOutside(ViewGroup v) {
                if (!NFW.isDefaultAction(getSettings().actionWhenTapOutside)) {
                    performAction(getSettings().actionWhenTapOutside);
//                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
            }

            @Override
            public void onLongPressOutside(ViewGroup v) {
                if (!NFW.isDefaultAction(getSettings().actionWhenLongPressOutside)) {
                    performAction(getSettings().actionWhenLongPressOutside);
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
            }

            @Override
            public void onDoubleClickOutside(ViewGroup v) {
                if (!NFW.isDefaultAction(getSettings().actionWhenDoubleTapOutside)) {
                    performAction(getSettings().actionWhenDoubleTapOutside);
//                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
            }
        });
    }

    public void onSettingsLoaded(NFW.Settings settings) {
        mSettings = settings;
        setSpeed(getSettings().speed);
        if (isResized()) {
            setScale(getSettings().smallScreenSize);
        }
        mBoundaryDrawable.setStroke(mBoundaryWidth, getSettings().boundaryColor);
        getAttachedView().postInvalidate();
    }

    public NFW.Settings getSettings() {
        return mSettings;
    }

    private void updateBoundaryShown(boolean shown) {
        mBoundaryShown = shown;
        getAttachedView().invalidate();
    }

    public boolean isFlying() {
        return getTouchEventEnabled();
    }

    private void disableFlying() {
        setTouchEventEnabled(false);
//        updateBoundaryShown(isResized());
        updateBoundaryShown(false);
    }

    private void enableFlying() {
        setTouchEventEnabled(true);
        updateBoundaryShown(true);
    }

    public void performAction(String action) {
        if (action.equals(NFW.ACTION_RESET)) {
            resetState(true);
        } else if (action.equals(NFW.ACTION_SOFT_RESET)) {
            resetState(false);
        } else if (action.equals(NFW.ACTION_TOGGLE)) {
            toggle();
        } else if (action.equals(NFW.ACTION_PIN)) {
            pin();
        } else if (action.equals(NFW.ACTION_PIN_OR_RESET)) {
            pinOrReset();
        } else if (action.equals(NFW.ACTION_SMALL_SCREEN_LEFT)) {
            resize(FlyingLayout.LAYOUT_ADJUSTMENT_LEFT);
        } else if (action.equals(NFW.ACTION_SMALL_SCREEN_RIGHT)) {
            resize(FlyingLayout.LAYOUT_ADJUSTMENT_RIGHT);
        }
    }

    private void toggle() {
        if (isFlying()) {
            disableFlying();
            goHome(getSettings().animation);
        } else {
            if (!isResized() && staysHome()) {
                moveToInitialPosition(false);
                hideSoftInputMethod();
            }
            enableFlying();
        }
    }

    private void pin() {
        if (!isResized() && staysHome()) {
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
            goHome(getSettings().animation);
        }
        disableFlying();
    }

    public void resize(int layoutAdjustment) {
        if (isResized()) {
            if (getLayoutAdjustment() == layoutAdjustment) {
                super.resize(FlyingLayout.DEFAULT_SCALE, getSettings().animation);
            } else {
                setLayoutAdjustment(layoutAdjustment, true);
            }
        } else {
            if (getLayoutAdjustment() != layoutAdjustment) {
                setLayoutAdjustment(layoutAdjustment, false);
            }
            super.resize(getSettings().smallScreenSize, getSettings().animation);
            hideSoftInputMethod();
        }
    }

    private boolean moveToInitialPosition(boolean pin) {
        final InitialPosition pos = new InitialPosition(getSettings().initialXp, getSettings().initialYp);
        if (pin && pos.getXp() == 0 && pos.getYp() == 0) {
            // default position for pin
            pos.setXp(0); // 0%
            pos.setYp(50); // 50%
        }
        final int x = pos.getX(getAttachedView());
        final int y = pos.getY(getAttachedView());
        boolean moved = false;
        if (x != 0 || y != 0) {
            moved = true;
            moveWithoutSpeed(x, y, getSettings().animation);
        }
        return moved;
    }

    public void resetState(boolean force) {
        boolean handled = false;
        if (isFlying() || !staysHome()) {
            disableFlying();
            // goHome must be placed after pin() for "Reset when collapsed"
            // option.
            goHome(getSettings().animation);
            handled = true;
        }
        if ((force || !handled) && isResized()) {
            super.resize(FlyingLayout.DEFAULT_SCALE, getSettings().animation);
        }
    }

    private void hideSoftInputMethod() {
        mInputMethodManager.hideSoftInputFromWindow(getAttachedView().getWindowToken(), 0);
    }

    @SuppressLint("NewApi")
    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        XposedHelpers.setBooleanField(getAttachedView(), "mForegroundBoundsChanged", true);
    }

    public void draw(Canvas canvas) {
        if (getSettings().drawBoundary && mBoundaryShown) {
            mBoundaryDrawable.setBounds(getBoundaryRect());
            mBoundaryDrawable.draw(canvas);
        }
    }
}