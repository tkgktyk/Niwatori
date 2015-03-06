package jp.tkgktyk.xposed.niwatori;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.TabHost;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import jp.tkgktyk.flyinglayout.FlyingLayout;

/**
 * Created by tkgktyk on 2015/02/12.
 */
public class ModActivity extends XposedModule {
    private static final String CLASS_DECOR_VIEW = "com.android.internal.policy.impl.PhoneWindow$DecorView";
    private static final String CLASS_SOFT_INPUT_WINDOW = "android.inputmethodservice.SoftInputWindow";

    private static final String FIELD_RECEIVER_REGISTERED = NFW.NAME + "_receiverRegistered";
    private static final String FIELD_HAS_FOCUS = NFW.NAME + "_hasFocus";

    private static final String FIELD_DIALOG_ACTION_RECEIVER = NFW.NAME + "_dialogActionReceiver";
    private static final BroadcastReceiver mActivityActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                logD("activity broadcast receiver: " + intent.getAction());
                final Activity activity = (Activity) context;
                if (isTabContent(activity)) {
                    logD(activity.getLocalClassName() + " is a tab content.");
                    return;
                }
                final FlyingHelper helper = getHelper(activity);
                helper.performAction(intent.getAction());
                abortBroadcast();
                logD(activity.toString() + ": consumed");
            } catch (Throwable t) {
                logE(t);
            }
        }
    };

    private static final String FIELD_FLYING_HELPER = NFW.NAME + "_flyingHelper2";

    private static XSharedPreferences mPrefs;
    private static NFW.Settings mSettings;

    public static void initZygote(XSharedPreferences prefs) {
        mPrefs = prefs;
        mSettings = newSettings(mPrefs);
        try {
//            forceSetBackground();
            installToActivity();
            installToDialog();
            log("prepared to attach to Activity and Dialog");
        } catch (Throwable t) {
            logE(t);
        }
        try {
            final Class<?> classDecorView = XposedHelpers.findClass(CLASS_DECOR_VIEW, null);
            logD(CLASS_DECOR_VIEW + " is found");
            XposedBridge.hookAllConstructors(classDecorView, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        final FrameLayout decorView = (FrameLayout) param.thisObject;
                        logD(decorView.getContext().getPackageName() + ": DecorView");
                        final FlyingHelper helper = new FlyingHelper(decorView);
                        XposedHelpers.setAdditionalInstanceField(decorView,
                                FIELD_FLYING_HELPER, helper);
                    } catch (Throwable t) {
                        logE(t);
                    }
                }
            });
            XposedHelpers.findAndHookMethod(classDecorView, "onInterceptTouchEvent", MotionEvent.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                            try {
                                final FrameLayout decorView = (FrameLayout) methodHookParam.thisObject;
                                final MotionEvent event = (MotionEvent) methodHookParam.args[0];
                                final FlyingHelper helper = getHelper(decorView);
                                if (helper.onInterceptTouchEvent(event)) {
                                    return true;
                                }
                            } catch (Throwable t) {
                                logE(t);
                            }
                            return invokeOriginalMethod(methodHookParam);
                        }
                    });
            XposedHelpers.findAndHookMethod(classDecorView, "onTouchEvent", MotionEvent.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                            try {
                                final FrameLayout decorView = (FrameLayout) methodHookParam.thisObject;
                                final MotionEvent event = (MotionEvent) methodHookParam.args[0];
                                final FlyingHelper helper = getHelper(decorView);
                                if (helper.onTouchEvent(event)) {
                                    return true;
                                }
                            } catch (Throwable t) {
                                logE(t);
                            }
                            return invokeOriginalMethod(methodHookParam);
                        }
                    });
            XposedHelpers.findAndHookMethod(classDecorView, "draw", Canvas.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                final FrameLayout decorView = (FrameLayout) param.thisObject;
                                final Canvas canvas = (Canvas) param.args[0];
                                final FlyingHelper helper = getHelper(decorView);
                                helper.draw(canvas);
                            } catch (Throwable t) {
                                logE(t);
                            }
                        }
                    });
            final XC_MethodReplacement onLayout = new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    try {
                        final FrameLayout decorView = (FrameLayout) methodHookParam.thisObject;
                        final FlyingHelper helper = getHelper(decorView);
                        if (helper != null) {
                            final boolean changed = (Boolean) methodHookParam.args[0];
                            final int left = (Integer) methodHookParam.args[1];
                            final int top = (Integer) methodHookParam.args[2];
                            final int right = (Integer) methodHookParam.args[3];
                            final int bottom = (Integer) methodHookParam.args[4];
                            helper.onLayout(changed, left, top, right, bottom);
                            return null;
                        }
                    } catch (Throwable t) {
                        logE(t);
                    }
                    return invokeOriginalMethod(methodHookParam);
                }
            };
            for (Class<?> cls = classDecorView; cls != null; cls = cls.getSuperclass()) {
                try {
                    XposedHelpers.findAndHookMethod(cls, "onLayout", boolean.class,
                            int.class, int.class, int.class, int.class, onLayout);
                    break;
                } catch (NoSuchMethodError e) {
                    // continue
                }
            }
        } catch (Throwable t) {
            logE(t);
        }
    }

//    private static void forceSetBackground() {
//        XposedHelpers.findAndHookMethod(View.class, "setBackgroundDrawable", Drawable.class,
//                new XC_MethodReplacement() {
//                    @Override
//                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
//                        try {
//                            Object[] args = null;
//                            if (param.thisObject.getClass().getName().equals(CLASS_DECOR_VIEW)) {
//                                final View v = (View) param.thisObject;
//                                final FlyingHelper helper = FlyingHelper.getFrom(v);
//                                if (helper != null) {
//                                    final Drawable d = (Drawable) param.args[0];
//                                    final Context context = v.getContext();
//                                    final Drawable dark = context.getResources().getDrawable(
//                                            android.R.drawable.screen_background_dark);
//                                    if (d == null) {
//                                        helper.getFlyingLayout().setBackground(dark);
//                                        // API 15
////                                        helper.getFlyingLayout().setBackgroundDrawable(dark);
//                                    } else if (d.getOpacity() == PixelFormat.OPAQUE) {
//                                        helper.getFlyingLayout().setBackground(d);
//                                        // API 15
////                                        helper.getFlyingLayout().setBackgroundDrawable(d);
//                                    } else {
//                                        helper.getFlyingLayout().setBackgroundColor(Color.TRANSPARENT);
//                                    }
////                                    args = new Object[]{dark};
//                                }
//                            }
//                            if (args == null) {
//                                args = param.args;
//                            }
//                            XposedBridge.invokeOriginalMethod(param.method, param.thisObject, args);
//                        } catch (Throwable t) {
//                            logE(t);
//                        }
//                        return null;
//                    }
//                });
//    }

    @Nullable
    private static FrameLayout getDecorView(@NonNull Activity activity) {
        return (FrameLayout) activity.getWindow().peekDecorView();
    }

    @Nullable
    private static FrameLayout getDecorView(@NonNull Dialog dialog) {
        return (FrameLayout) dialog.getWindow().peekDecorView();
    }

    @Nullable
    private static FlyingHelper getHelper(@NonNull Activity activity) {
        final FrameLayout decorView = getDecorView(activity);
        if (decorView == null) {
            return null;
        }
        return getHelper(decorView);
    }

    @Nullable
    private static FlyingHelper getHelper(@NonNull Dialog dialog) {
        final FrameLayout decorView = getDecorView(dialog);
        if (decorView == null) {
            return null;
        }
        return getHelper(decorView);
    }

    @NonNull
    private static FlyingHelper getHelper(@NonNull FrameLayout decorView) {
        return (FlyingHelper) XposedHelpers.getAdditionalInstanceField(
                decorView, FIELD_FLYING_HELPER);
    }

    private static void installToActivity() {
        //
        // initialize addtional fields
        //
        XposedBridge.hookAllConstructors(Activity.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    final Activity activity = (Activity) param.thisObject;
                    XposedHelpers.setAdditionalInstanceField(activity, FIELD_RECEIVER_REGISTERED, false);
                    XposedHelpers.setAdditionalInstanceField(activity, FIELD_HAS_FOCUS, false);
                } catch (Throwable t) {
                    logE(t);
                }
            }
        });
        //
        // register broadcast receiver
        //
        XposedHelpers.findAndHookMethod(Activity.class, "onWindowFocusChanged", boolean.class
                , new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    final Activity activity = (Activity) param.thisObject;
                    final boolean hasFocus = (Boolean) param.args[0];
                    logD(activity + "#onWindowFocusChanged: hasFocus=" + hasFocus);
                    registerReceiver(activity, hasFocus);
                } catch (Throwable t) {
                    logE(t);
                }
            }
        });
        XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    final Activity activity = (Activity) param.thisObject;
                    final boolean hasFocus = activity.hasWindowFocus();
                    logD(activity + "#onResume: hasFocus=" + hasFocus);
                    registerReceiver(activity, hasFocus);
                } catch (Throwable t) {
                    logE(t);
                }
            }
        });
        XposedHelpers.findAndHookMethod(Activity.class, "onPause", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    final Activity activity = (Activity) param.thisObject;
                    logD(activity + "#onPause");
                    unregisterReceiver(activity);
                } catch (Throwable t) {
                    logE(t);
                }
            }
        });
        //
        // reset by back key
        // not work because cannot hook onBackPressed of extended class
        //
//        XposedHelpers.findAndHookMethod(Activity.class, "onBackPressed", new XC_MethodReplacement() {
//            @Override
//            protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
//                try {
//                    logD();
//                    final Activity activity = (Activity) methodHookParam.thisObject;
//                    // reset state
//                    FlyingHelper helper = FlyingHelper.getFrom(getDecorView(activity));
//                    if (helper == null) {
//                        logD("FlyingHelper is not found.");
//                        XposedBridge.invokeOriginalMethod(methodHookParam.method, activity, null);
//                    } else if (helper.isFlying()) {
//                        logD("reset state because activity is flying.");
//                        helper.resetState();
//                    } else {
//                        XposedBridge.invokeOriginalMethod(methodHookParam.method, activity, null);
//                    }
//                } catch (Throwable t) {
//                    logE(t);
//                }
//                return null;
//            }
//        });
        //
        // screen rotation
        //
        XposedHelpers.findAndHookMethod(Activity.class, "onConfigurationChanged", Configuration.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            final Activity activity = (Activity) param.thisObject;
                            final FlyingHelper helper = getHelper(activity);
                            if (helper == null) {
                                logD("DecorView is null");
                                return;
                            }
                            final Configuration newConfig = (Configuration) param.args[0];
                            if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                                helper.rotate();
                            } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
                                helper.rotate();
                            }
                        } catch (Throwable t) {
                            logE(t);
                        }
                    }
                });
    }

    private static boolean isTabContent(Activity activity) {
        final FrameLayout decor = getDecorView(activity);
        if (decor != null) {
            for (ViewParent v = decor.getParent(); v != null; v = v.getParent()) {
                if (v instanceof TabHost) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void registerReceiver(Activity activity, boolean focused) {
        final Boolean registered = (Boolean) XposedHelpers
                .getAdditionalInstanceField(activity, FIELD_RECEIVER_REGISTERED);
        final Boolean hasFocus = (Boolean) XposedHelpers
                .getAdditionalInstanceField(activity, FIELD_HAS_FOCUS);
        boolean register = false;
        if (focused) {
            if (!hasFocus) {
                // got focus
                if (registered) {
                    logD("register again");
                    activity.unregisterReceiver(mActivityActionReceiver);
                }
                register = true;
            } else {
                // keep focus
                if (!registered) {
                    register = true;
                }
            }
        } else if (hasFocus) {
            // lost focus
            if (registered) {
                logD("register again");
                activity.unregisterReceiver(mActivityActionReceiver);
                register = true;
                resetAutomatically(activity);
            }
        } else {
            // keep unfocus
            if (!registered) {
                register = true;
            }
        }
        if (register) {
            activity.registerReceiver(mActivityActionReceiver,
                    focused ? NFW.FOCUSED_ACTIVITY_FILTER : NFW.ACTIVITY_FILTER);
            XposedHelpers.setAdditionalInstanceField(
                    activity, FIELD_RECEIVER_REGISTERED, true);
            logD("registered");
        }
        XposedHelpers.setAdditionalInstanceField(
                activity, FIELD_HAS_FOCUS, focused);
    }

    private static void unregisterReceiver(Activity activity) {
        final Boolean registered = (Boolean) XposedHelpers
                .getAdditionalInstanceField(activity, FIELD_RECEIVER_REGISTERED);
        if (registered) {
            activity.unregisterReceiver(mActivityActionReceiver);
            XposedHelpers.setAdditionalInstanceField(
                    activity, FIELD_RECEIVER_REGISTERED, false);
            logD("unregistered");

            resetAutomatically(activity);
        } else {
            logD("not registered");
        }
    }

    private static void resetAutomatically(Activity activity) {
        if (mSettings.resetAutomatically) {
            // When fire actions from shortcut (ActionActivity), it causes onPause and onResume events
            // because through an Activity. So shouldn't reset automatically.
            final FlyingHelper helper = getHelper(activity);
            if (helper == null) {
                logD("DecorView is null");
                return;
            }
            helper.resetState();
        }
    }

    private static void installToDialog() {
        //
        // register receiver
        //
        XposedHelpers.findAndHookMethod(Dialog.class, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                logD("onAttachedToWindow");
                try {
                    final Dialog dialog = (Dialog) param.thisObject;
                    if (isInputMethod(dialog)) {
                        return;
                    }
                    registerReceiver(dialog);
                } catch (Throwable t) {
                    logE(t);
                }
            }
        });
        XposedHelpers.findAndHookMethod(Dialog.class, "onWindowFocusChanged", boolean.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    final Dialog dialog = (Dialog) param.thisObject;
                    if (isInputMethod(dialog)) {
                        return;
                    }
                    final boolean hasFocus = (Boolean) param.args[0];
                    logD(dialog + "#onWindowFocusChanged: hasFocus=" + hasFocus);
                    if (hasFocus) {
                        registerReceiver(dialog);
                    } else {
                        unregisterReceiver(dialog);
                    }
                } catch (Throwable t) {
                    logE(t);
                }
            }
        });
        XposedHelpers.findAndHookMethod(Dialog.class, "onDetachedFromWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                logD("onDetachedFromWindow");
                try {
                    final Dialog dialog = (Dialog) param.thisObject;
                    if (isInputMethod(dialog)) {
                        return;
                    }
                    unregisterReceiver(dialog);
                } catch (Throwable t) {
                    logE(t);
                }
            }
        });
    }

    private static boolean isInputMethod(Dialog dialog) {
        return dialog.getClass().getName().equals(CLASS_SOFT_INPUT_WINDOW);
    }

    private static void registerReceiver(final Dialog dialog) {
        final BroadcastReceiver receiver = (BroadcastReceiver) XposedHelpers
                .getAdditionalInstanceField(dialog, FIELD_DIALOG_ACTION_RECEIVER);
        if (receiver != null) {
            // already registered
            return;
        }
        final BroadcastReceiver actionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                logD("activity broadcast receiver: " + intent.getAction());
                try {
                    FlyingHelper helper = getHelper(dialog);
                    if (helper == null) {
                        logD("DecorView is null.");
                        return;
                    }
                    helper.performAction(intent.getAction());
                    abortBroadcast();
                    logD(dialog.toString() + ": consumed");
                } catch (Throwable t) {
                    logE(t);
                }
            }
        };
        XposedHelpers.setAdditionalInstanceField(dialog,
                FIELD_DIALOG_ACTION_RECEIVER, actionReceiver);
        dialog.getContext().registerReceiver(actionReceiver, NFW.FOCUSED_DIALOG_FILTER);

    }

    private static void unregisterReceiver(Dialog dialog) {
        final BroadcastReceiver actionReceiver = (BroadcastReceiver) XposedHelpers
                .getAdditionalInstanceField(dialog, FIELD_DIALOG_ACTION_RECEIVER);
        if (actionReceiver != null) {
            dialog.getContext().unregisterReceiver(actionReceiver);
            XposedHelpers.setAdditionalInstanceField(
                    dialog, FIELD_DIALOG_ACTION_RECEIVER, null);
        }
    }

    private static class FlyingHelper extends FlyingLayout.Helper {
        private InputMethodManager mInputMethodManager;
        private Drawable mBoundaryDrawable;
        private boolean mBoundaryShown = false;

        private Rect mChildRect = new Rect();
        private Method mGetPaddingLeftWithForeground;
        private Method mGetPaddingTopWithForeground;
        private Method mGetPaddingRightWithForeground;
        private Method mGetPaddingBottomWithForeground;

        private FlyingHelper(FrameLayout view) {
            super(view);
            try {
                mGetPaddingLeftWithForeground = view.getClass().getSuperclass().getDeclaredMethod("getPaddingLeftWithForeground");
                mGetPaddingTopWithForeground = view.getClass().getSuperclass().getDeclaredMethod("getPaddingTopWithForeground");
                mGetPaddingRightWithForeground = view.getClass().getSuperclass().getDeclaredMethod("getPaddingRightWithForeground");
                mGetPaddingBottomWithForeground = view.getClass().getSuperclass().getDeclaredMethod("getPaddingBottomWithForeground");
            } catch (NoSuchMethodException | NullPointerException e) {
                logE(e);
            }
            setAccessible(mGetPaddingLeftWithForeground);
            setAccessible(mGetPaddingTopWithForeground);
            setAccessible(mGetPaddingRightWithForeground);
            setAccessible(mGetPaddingBottomWithForeground);

            mInputMethodManager = (InputMethodManager) view.getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);

            reinitialize();
        }

        private void setAccessible(final Method method) {
            if (!method.isAccessible()) {
                AccessController.doPrivileged(new PrivilegedAction<Object>() {
                    public Object run() {
                        method.setAccessible(true);
                        return null;
                    }
                });
            }
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
}
