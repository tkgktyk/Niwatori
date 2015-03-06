package jp.tkgktyk.xposed.niwatori;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TabHost;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

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

    private static final String FIELD_FLYING_HELPER = NFW.NAME + "_flyingHelper";

    private static XSharedPreferences mPrefs;
    private static NFW.Settings mSettings;

    public static void initZygote(XSharedPreferences prefs) {
        mPrefs = prefs;
        mSettings = newSettings(mPrefs);
        try {
//            forceSetBackground();
            installToDecorView();
            installToActivity();
            installToDialog();
            log("prepared to attach to Activity and Dialog");
        } catch (Throwable t) {
            logE(t);
        }
    }

    private static void installToDecorView() {
        try {
            final Class<?> classDecorView = XposedHelpers.findClass(CLASS_DECOR_VIEW, null);
            logD(CLASS_DECOR_VIEW + " is found");
            XposedBridge.hookAllConstructors(classDecorView, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        final FrameLayout decorView = (FrameLayout) param.thisObject;
                        logD(decorView.getContext().getPackageName() + ": DecorView");
                        final FlyingHelper helper = new FlyingHelper(decorView, 1, mSettings);
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
            final Class<?> classFrameLayout = classDecorView.getSuperclass();
            XposedHelpers.findAndHookMethod(classFrameLayout, "onLayout", boolean.class,
                    int.class, int.class, int.class, int.class, new XC_MethodReplacement() {
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
                    });
        } catch (Throwable t) {
            logE(t);
        }
    }

    private static void forceSetBackground(View decorView) {
                final Drawable d = decorView.getBackground();
                final Context context = v.getContext();
                final Drawable dark = context.getResources().getDrawable(
                        android.R.drawable.screen_background_dark);
                if (d == null) {
                    helper.getFlyingLayout().setBackground(dark);
                    // API 15
//                                        helper.getFlyingLayout().setBackgroundDrawable(dark);
                } else if (d.getOpacity() == PixelFormat.OPAQUE) {
                    helper.getFlyingLayout().setBackground(d);
                    // API 15
//                                        helper.getFlyingLayout().setBackgroundDrawable(d);
                } else {
                    helper.getFlyingLayout().setBackgroundColor(Color.TRANSPARENT);
                }
//                                    args = new Object[]{dark};
    }

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
                        resetAutomatically(dialog);
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

    private static void resetAutomatically(Dialog dialog) {
        if (mSettings.resetAutomatically) {
            // When fire actions from shortcut (ActionActivity), it causes onPause and onResume events
            // because through an Activity. So shouldn't reset automatically.
            final FlyingHelper helper = getHelper(dialog);
            if (helper == null) {
                logD("DecorView is null");
                return;
            }
            helper.resetState();
        }
    }

}
