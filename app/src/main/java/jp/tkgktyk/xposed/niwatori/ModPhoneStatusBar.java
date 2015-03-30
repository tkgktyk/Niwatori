package jp.tkgktyk.xposed.niwatori;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.support.annotation.NonNull;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by tkgktyk on 2015/02/13.
 */
public class ModPhoneStatusBar extends XposedModule {
    private static final String CLASS_PHONE_STATUS_BAR_VIEW = "com.android.systemui.statusbar.phone.PhoneStatusBarView";
    private static final String CLASS_PANEL_HOLDER = "com.android.systemui.statusbar.phone.PanelHolder";

    private static final String FIELD_FLYING_HELPER = NFW.NAME + "_flyingHelper";

    private static XSharedPreferences mPrefs;
    // for status bar
    private static FlyingHelper mHelper;

    private static View mPhoneStatusBarView;
    private static final BroadcastReceiver mGlobalReceiver = new BroadcastReceiver() {
        private static final String STATUS_BAR_SERVICE = "statusbar";

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            logD("global broadcast receiver: " + action);
            final int mState = XposedHelpers.getIntField(mPhoneStatusBarView, "mState");
            if (action.startsWith(NFW.PREFIX_ACTION_SB)) {
                consumeMyAction(context, action);
                return;
            }
            if (mState == 0) { // STATE_CLOSED = 0
                return;
            }
            // target is status bar
            mHelper.performAction(action);
            abortBroadcast();
            logD("consumed: " + action);
        }

        @SuppressWarnings("ResourceType")
        private void consumeMyAction(Context context, String action) {
            if (action.equals(NFW.ACTION_SB_EXPAND_NOTIFICATIONS)) {
                Object statusBar = context.getSystemService(STATUS_BAR_SERVICE);
                XposedHelpers.callMethod(statusBar, "expandNotificationsPanel");
                mHelper.performExtraAction();
            } else if (action.equals(NFW.ACTION_SB_EXPAND_QUICK_SETTINGS)) {
                Object statusBar = context.getSystemService(STATUS_BAR_SERVICE);
                XposedHelpers.callMethod(statusBar, "expandSettingsPanel");
                mHelper.performExtraAction();
            }
        }
    };

    public static void initZygote(XSharedPreferences prefs) {
        mPrefs = prefs;
    }

    public static void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (!loadPackageParam.packageName.equals("com.android.systemui")) {
            return;
        }
        try {
            installToStatusBar(loadPackageParam.classLoader);
        } catch (Throwable t) {
            logE(t);
        }
    }

    private static void installToStatusBar(ClassLoader classLoader) {
        final Class<?> classPanelHolder = XposedHelpers.findClass(CLASS_PANEL_HOLDER, classLoader);
        XposedBridge.hookAllConstructors(classPanelHolder, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    final FrameLayout panelHolder = (FrameLayout) param.thisObject;
                    // need to reload on each package?
                    mHelper = new FlyingHelper(panelHolder, 1, false, newSettings(mPrefs));
                    XposedHelpers.setAdditionalInstanceField(panelHolder,
                            FIELD_FLYING_HELPER, mHelper);

                    panelHolder.getContext().registerReceiver(mGlobalReceiver, NFW.STATUS_BAR_FILTER);
                    panelHolder.getContext().registerReceiver(new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            logD("reload settings");
                            // need to reload on each package?
                            NFW.Settings settings = (NFW.Settings) intent.getSerializableExtra(NFW.EXTRA_SETTINGS);
                            mHelper.onSettingsLoaded(settings);
                        }
                    }, NFW.SETTINGS_CHANGED_FILTER);
                    log("attached to status bar");
                } catch (Throwable t) {
                    logE(t);
                }
            }
        });
        XposedHelpers.findAndHookMethod(classPanelHolder, "onTouchEvent", MotionEvent.class,
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        try {
                            final MotionEvent event = (MotionEvent) methodHookParam.args[0];
                            if (mHelper.onTouchEvent(event)) {
                                return true;
                            }
                        } catch (Throwable t) {
                            logE(t);
                        }
                        return invokeOriginalMethod(methodHookParam);
                    }
                });
        final Class<?> classFrameLayout = classPanelHolder.getSuperclass();
        XposedHelpers.findAndHookMethod(classFrameLayout, "draw", Canvas.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            final Canvas canvas = (Canvas) param.args[0];
                            final FlyingHelper helper = getHelper(param.thisObject);
                            if (helper != null) {
                                mHelper.draw(canvas);
                            }
                        } catch (Throwable t) {
                            logE(t);
                        }
                    }
                });
        XposedHelpers.findAndHookMethod(classFrameLayout, "onLayout", boolean.class,
                int.class, int.class, int.class, int.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        try {
                            final FlyingHelper helper = getHelper(methodHookParam.thisObject);
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
        final Class<?> classViewGroup = classFrameLayout.getSuperclass();
        XposedHelpers.findAndHookMethod(classViewGroup, "onInterceptTouchEvent", MotionEvent.class,
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        try {
                            final MotionEvent event = (MotionEvent) methodHookParam.args[0];
                            final FlyingHelper helper = getHelper(methodHookParam.thisObject);
                            if (helper != null && helper.onInterceptTouchEvent(event)) {
                                return true;
                            }
                        } catch (Throwable t) {
                            logE(t);
                        }
                        return invokeOriginalMethod(methodHookParam);
                    }
                });
        final Class<?> classPhoneStatusBarView = XposedHelpers.findClass(
                CLASS_PHONE_STATUS_BAR_VIEW, classLoader);
        XposedBridge.hookAllConstructors(classPhoneStatusBarView, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mPhoneStatusBarView = (View) param.thisObject;
            }
        });
        //
        // Reset state when status bar collapsed
        //
        try {
            XposedHelpers.findAndHookMethod(classPhoneStatusBarView, "onAllPanelsCollapsed",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                if (mHelper.getSettings().autoReset) {
                                    mHelper.resetState(true);
                                }
                            } catch (Throwable t) {
                                logE(t);
                            }
                        }
                    });
        } catch (NoSuchMethodError e) {
            log("PhoneStatusBarView#onAllPanelsCollapsed is not found.");
        }
    }

    private static FlyingHelper getHelper(@NonNull Object obj) {
        return (FlyingHelper) XposedHelpers.getAdditionalInstanceField(obj, FIELD_FLYING_HELPER);
    }
}
