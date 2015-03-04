package jp.tkgktyk.xposed.niwatori;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import jp.tkgktyk.flyinglayout.FlyingLayout;

/**
 * Created by tkgktyk on 2015/02/13.
 */
public class FlyingHelper extends XposedModule {
    private static final String FIELD_FLYING_HELPER = NFW.NAME + "_flyingHelper";

    private NFW.Settings mSettings;

    private ViewGroup mTargetView;
    private InputMethodManager mInputMethodManager;

    private FlyingLayout mFlyingLayout;
    private FrameLayout mContainer;

    private Drawable mBoundaryDrawable;

    public FlyingHelper(NFW.Settings settings) {
        mSettings = settings;
    }

    @Nullable
    public static View getHelperHolder(View target) {
        if (target != null) {
            return target.getRootView();
        }
        return null;
    }

    @Nullable
    public static FlyingHelper getFromHelperHolder(View holder) {
        if (holder != null) {
            return (FlyingHelper) XposedHelpers.getAdditionalInstanceField(
                    holder, FIELD_FLYING_HELPER);
        }
        return null;
    }

    @Nullable
    public static FlyingHelper getFrom(View target) {
        View holder = getHelperHolder(target);
        return getFromHelperHolder(holder);
    }

    public NFW.Settings getSettings() {
        return mSettings;
    }

//    /**
//     * Before:
//     * parent
//     * - child
//     * - ...
//     * - target
//     * - ...
//     * <p/>
//     * After:
//     * parent
//     * - child
//     * - ...
//     * - FlyingLayout
//     * -- Container
//     * --- target
//     * - ...
//     *
//     * @param parent
//     * @param targetIndex
//     * @return
//     * @throws Throwable
//     */
//    public View install(ViewGroup parent, int targetIndex) throws Throwable {
//        final View target = parent.getChildAt(targetIndex);
//        mTargetView = target;
//        parent.removeView(target);
//        mInputMethodManager = (InputMethodManager) target.getContext()
//                .getSystemService(Context.INPUT_METHOD_SERVICE);
//
//        final Context context = target.getContext();
//        prepareBoundary(context);
//        installFlyingLayout(context);
//
//        // wrap with FlyingLayout
//        mContainer.addView(target);
//        parent.addView(mFlyingLayout, targetIndex);
//
//        final View holder = getHelperHolder(target);
//        XposedHelpers.setAdditionalInstanceField(holder, FIELD_FLYING_HELPER, this);
//
//        return holder;
//    }

    /**
     * Before:
     * target
     * - contents
     * <p/>
     * After:
     * target
     * - FlyingLayout
     * -- Container
     * --- contents
     *
     * @param target
     * @return
     * @throws Throwable
     */
    public View install(ViewGroup target) throws Throwable {
        mTargetView = target;
        mInputMethodManager = (InputMethodManager) target.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);

        final Context context = target.getContext();
        prepareBoundary(context);
        installFlyingLayout(context);

        return attachTo(target);
    }

    private void prepareBoundary(Context context) {
        Context flyContext = null;
        try {
            flyContext = context.createPackageContext(
                    NFW.PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
        if (flyContext != null) {
            mBoundaryDrawable = flyContext.getResources().getDrawable(R.drawable.notify_flying);
        }
    }

    private void installFlyingLayout(Context context) throws Throwable {
        mFlyingLayout = new FlyingLayout(context);
        mContainer = new FrameLayout(context);
        // setup view hierarchy
        mFlyingLayout.addView(mContainer);

        // setup FlyingLayout
        mFlyingLayout.setSpeed(mSettings.speed);
        final Context flyContext = context.createPackageContext(
                NFW.PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY);
        final int padding = flyContext.getResources()
                .getDimensionPixelSize(R.dimen.flying_view_padding);
        mFlyingLayout.setHorizontalPadding(padding);
        mFlyingLayout.setVerticalPadding(padding);
        mFlyingLayout.setEnableTouchEventX(false);
        mFlyingLayout.setEnableTouchEventY(false);
        mFlyingLayout.setUseContainer(true);
        mFlyingLayout.setOnFlyingEventListener(new FlyingLayout.SimpleOnFlyingEventListener() {
            @Override
            public void onClickOutside(FlyingLayout v) {
                resetState();
            }

            @Override
            public void onLongPressOutside(FlyingLayout v) {
                // nothing to do
            }

            @Override
            public void onDoubleClickOutside(FlyingLayout v) {
                pin();
            }
        });
    }

    /**
     * Before:
     * target
     * - contents
     * <p/>
     * After:
     * target
     * - FlyingLayout
     * -- Container
     * --- contents
     *
     * @param target
     */
    private View attachTo(ViewGroup target) {
        final List<View> contents = new ArrayList<>();
        for (int i = 0; i < target.getChildCount(); ++i) {
            contents.add(target.getChildAt(i));
        }
        target.removeAllViews();
        for (View v : contents) {
            mContainer.addView(v, v.getLayoutParams());
        }
        target.addView(mFlyingLayout);
        final View holder = getHelperHolder(target);
        XposedHelpers.setAdditionalInstanceField(holder, FIELD_FLYING_HELPER, this);

        return holder;
    }

    public void uninstall() throws Throwable {
        if (mTargetView == null) {
            log("not installed");
            return;
        }
        final View holder = getHelperHolder(mTargetView);
        XposedHelpers.removeAdditionalInstanceField(holder, FIELD_FLYING_HELPER);

        mTargetView.removeView(mFlyingLayout);

        final List<View> contents = new ArrayList<>();
        for (int i = 0; i < mContainer.getChildCount(); ++i) {
            contents.add(mContainer.getChildAt(i));
        }
        mContainer.removeAllViews();
        for (View v : contents) {
            mTargetView.addView(v, v.getLayoutParams());
        }
    }


    public FlyingLayout getFlyingLayout() {
        return mFlyingLayout;
    }

    private void setBoundaryShown(boolean shown) {
        if (mSettings.notifyFlying) {
            if (shown) {
                mContainer.setForeground(mBoundaryDrawable);
            } else {
                mContainer.setForeground(null);
            }
        }
    }

    public boolean isFlying() {
        return mFlyingLayout.getEnableTouchEventX() || mFlyingLayout.getEnableTouchEventY();
    }

    private void disableFlying() {
        mFlyingLayout.setEnableTouchEventX(false);
        mFlyingLayout.setEnableTouchEventY(false);
        setBoundaryShown(false);
    }

    private void enableFlying() {
        mFlyingLayout.setEnableTouchEventX(true);
        mFlyingLayout.setEnableTouchEventY(true);
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
            mFlyingLayout.goHome(mSettings.animation);
        } else {
            if (mFlyingLayout.staysHome()) {
                moveToInitialPosition(false);
                hideSoftInputMethod();
            }
            enableFlying();
        }
    }

    private void pin() {
        if (mFlyingLayout.staysHome()) {
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
        if (mFlyingLayout.staysHome()) {
            moveToInitialPosition(true);
            hideSoftInputMethod();
        } else {
            mFlyingLayout.goHome(mSettings.animation);
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
        final int x = pos.getX(mFlyingLayout);
        final int y = pos.getY(mFlyingLayout);
        boolean moved = false;
        if (x != 0 || y != 0) {
            moved = true;
            mFlyingLayout.moveWithoutSpeed(x, y, mSettings.animation);
        }
        return moved;
    }

    public void resetState() {
        if (isFlying() || !mFlyingLayout.staysHome()) {
            disableFlying();
            // goHome must be placed after pin() for "Reset when collapsed"
            // option.
            mFlyingLayout.goHome(mSettings.animation);
        }
    }

    private void hideSoftInputMethod() {
        mInputMethodManager.hideSoftInputFromWindow(mTargetView.getWindowToken(), 0);
    }
}
