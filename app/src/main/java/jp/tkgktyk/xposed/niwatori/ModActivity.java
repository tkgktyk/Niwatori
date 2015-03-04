package jp.tkgktyk.xposed.niwatori;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TabHost;

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
    private static final String CLASS_DECOR_VIEW2 = "com.android.internal.policy.impl.PhoneWindow.DecorView";
    private static final String CLASS_SOFT_INPUT_WINDOW = "android.inputmethodservice.SoftInputWindow";

    private static final String FIELD_INSTALLED = NFW.NAME + "_installed";
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
                final View decor = getDecorView(activity);
                FlyingHelper helper = FlyingHelper.getFrom(decor);
                if (helper == null) {
                    logD("FlyingHelper is not found.");
                    return;
                }
                helper.performAction(intent.getAction());
                abortBroadcast();
                logD(activity.toString() + ": consumed");
            } catch (Throwable t) {
                logE(t);
            }
        }
    };

    private static XSharedPreferences mPrefs;

    public static void initZygote(XSharedPreferences prefs) {
        mPrefs = prefs;
        try {
            forceSetBackground();
            installToActivity();
            installToDialog();
            log("prepared to attach to Activity and Dialog");
        } catch (Throwable t) {
            logE(t);
        }
        try {
            XposedHelpers.findClass(CLASS_DECOR_VIEW, null);
            logD(CLASS_DECOR_VIEW + " is found");
        } catch (Throwable t) {
            logE(t);
        }
        try {
            XposedHelpers.findClass(CLASS_DECOR_VIEW2, null);
            logD(CLASS_DECOR_VIEW2 + " is found");
        } catch (Throwable t) {
            logE(t);
        }
    }

    private static void forceSetBackground() {
        XposedHelpers.findAndHookMethod(View.class, "setBackgroundDrawable", Drawable.class,
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object[] args = null;
                            if (param.thisObject.getClass().getName().equals(CLASS_DECOR_VIEW)) {
                                final View v = (View) param.thisObject;
                                final FlyingHelper helper = FlyingHelper.getFrom(v);
                                if (helper != null) {
                                    final Drawable d = (Drawable) param.args[0];
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
                            }
                            if (args == null) {
                                args = param.args;
                            }
                            XposedBridge.invokeOriginalMethod(param.method, param.thisObject, args);
                        } catch (Throwable t) {
                            logE(t);
                        }
                        return null;
                    }
                });
    }

    @Nullable
    private static ViewGroup getDecorView(Activity activity) {
        return (ViewGroup) activity.getWindow().peekDecorView();
    }

    @Nullable
    private static ViewGroup getDecorView(Dialog dialog) {
        return (ViewGroup) dialog.getWindow().peekDecorView();
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
                    XposedHelpers.setAdditionalInstanceField(activity, FIELD_INSTALLED, false);
                    XposedHelpers.setAdditionalInstanceField(activity, FIELD_RECEIVER_REGISTERED, false);
                    XposedHelpers.setAdditionalInstanceField(activity, FIELD_HAS_FOCUS, false);
                } catch (Throwable t) {
                    logE(t);
                }
            }
        });
        //
        // install
        //
        XposedHelpers.findAndHookMethod(Activity.class, "onPostCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    install((Activity) param.thisObject);
                } catch (Throwable t) {
                    logE(t);
                }
            }
        });
        XposedBridge.hookAllMethods(Activity.class, "setContentView", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    final Activity activity = (Activity) param.thisObject;
                    final Boolean registered = (Boolean) XposedHelpers
                            .getAdditionalInstanceField(activity, FIELD_RECEIVER_REGISTERED);
                    if (registered) {
                        // after onResume
                        final Boolean installed = (Boolean) XposedHelpers
                                .getAdditionalInstanceField(activity, FIELD_INSTALLED);
                        if (!installed) {
                            logD("install after set content view.");
                            install(activity);
                        }
                    }
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
                            final FlyingHelper helper = FlyingHelper.getFrom(getDecorView(activity));
                            if (helper != null) {
                                final FlyingLayout flyingLayout = helper.getFlyingLayout();
                                final Configuration newConfig = (Configuration) param.args[0];
                                if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                                    flyingLayout.rotate();
                                } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
                                    flyingLayout.rotate();
                                }
                            } else {
                                logD("FlyingHelper is not found.");
                            }
                        } catch (Throwable t) {
                            logE(t);
                        }
                    }
                });
    }

    private static void install(Activity activity) {
        final ViewGroup decor = getDecorView(activity);
        if (decor == null) {
            log("decorView is null. ("
                    + activity.getLocalClassName()
                    + "@" + activity.getPackageName()
                    + ")");
            return;
        }
        if (install(activity, activity.getPackageName(), activity.getCurrentFocus(), decor)) {
//            if (isTabContent(activity)) {
//                return;
//            }
//            final FlyingHelper helper = FlyingHelper.getFrom(decor);
//            Drawable drawable = decor.getBackground();
//            if (drawable == null) {
//                final TypedValue a = new TypedValue();
//                logD(activity.toString() + ": " + a.toString());
//                if (activity.getTheme().resolveAttribute(android.R.attr.windowBackground, a, true)) {
//                    if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT && a.type <= TypedValue.TYPE_LAST_COLOR_INT) {
//                        // color
//                        final int color = a.data;
//                        logD("background color: " + String.format("#%08X", (0xFFFFFFFF & color)));
//                        if (Color.alpha(color) == 0xFF) {
//                            // opaque
//                            logD("set opaque background color");
//                            helper.getFlyingLayout().setBackgroundColor(color);
//                        }
//                    } else {
//                        Drawable d = activity.getResources().getDrawable(a.resourceId);
//                        logD("background drawable opacity: " + d.getOpacity());
//                        if (d.getOpacity() == PixelFormat.OPAQUE) {
//                            // opaque
//                            logD("set opaque background drawable");
//                            helper.getFlyingLayout().setBackground(d);
//                        }
//                    }
//                }
//            } else if (drawable.getOpacity() == PixelFormat.OPAQUE) {
//                logD("decorView has opaque background drawable");
//                helper.getFlyingLayout().setBackground(drawable);
//                // API 15
////                helper.getFlyingLayout().setBackgroundDrawable(d);
//            }
        }
    }

    private static boolean install(Object parent, String packageName, View focus, ViewGroup decor) {
        final FlyingHelper installed = FlyingHelper.getFrom(decor);
        if (installed != null) {
            // already installed
            return false;
        }
        NFW.Settings settings = newSettings(mPrefs);
        if (!settings.blackSet.contains(packageName)) {
            final FlyingHelper helper = new FlyingHelper(settings);
            try {
                helper.install(decor);
            } catch (Throwable t) {
                logE(t);
            }
            // restore current focus
            if (focus != null) {
                focus.requestFocus();
            }
        } else {
            logD(packageName + " is contained in blacklist.");
        }
        // set installed flag even if target is in blacklist.
        XposedHelpers.setAdditionalInstanceField(parent, FIELD_INSTALLED, true);
        return true;
    }

    private static boolean isTabContent(Activity activity) {
        final View decor = getDecorView(activity);
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
        final FlyingHelper helper = FlyingHelper.getFrom(getDecorView(activity));
        if (helper != null) {
            if (helper.getSettings().resetAutomatically) {
                // When fire actions from shortcut (ActionActivity), it causes onPause and onResume events
                // because through an Activity. So shouldn't reset automatically.
                helper.resetState();
            }
        } else {
            logD("FlyingHelper is not found.");
        }
    }

    private static void installToDialog() {
        //
        // initialize addtional fields
        //
        XposedBridge.hookAllConstructors(Dialog.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    final Dialog dialog = (Dialog) param.thisObject;
                    XposedHelpers.setAdditionalInstanceField(dialog, FIELD_INSTALLED, false);
                } catch (Throwable t) {
                    logE(t);
                }
            }
        });
        //
        // install
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
                    install(dialog);
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

    private static void install(Dialog dialog) {
        final ViewGroup decor = getDecorView(dialog);
        if (decor == null) {
            log("decorView is null. ("
                    + dialog.getContext().getPackageName()
                    + ")");
            return;
        }
        install(dialog, dialog.getContext().getPackageName(), dialog.getCurrentFocus(), decor);
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
                    final View decor = getDecorView(dialog);
                    FlyingHelper helper = FlyingHelper.getFrom(decor);
                    if (helper == null) {
                        logD("FlyingHelper is not found.");
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
}
