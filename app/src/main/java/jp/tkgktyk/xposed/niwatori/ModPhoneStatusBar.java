package jp.tkgktyk.xposed.niwatori;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.support.annotation.NonNull;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

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
    private static NFW.Settings mSettings;

    private static FlyingHelper mHelper;

    private static View mPhoneStatusBarView;
    private static final BroadcastReceiver mGlobalReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            logD("global broadcast receiver: " + intent.getAction());
            final int mState = XposedHelpers.getIntField(mPhoneStatusBarView, "mState");
            if (mState == 0) { // STATE_CLOSED = 0
                return;
            }
            // target is status bar
            final String action = intent.getAction();
            mHelper.performAction(action);
            abortBroadcast();
            logD("consumed: " + action);
        }
    };

    public static void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam,
                                         XSharedPreferences prefs) {
        mPrefs = prefs;
        mSettings = newSettings(mPrefs);
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
        logD(CLASS_PANEL_HOLDER + " is found");
        XposedBridge.hookAllConstructors(classPanelHolder, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    final ViewGroup panelHolder = (ViewGroup) param.thisObject;
                    mHelper = new FlyingHelper(panelHolder, 1, mSettings);
                    XposedHelpers.setAdditionalInstanceField(panelHolder,
                            FIELD_FLYING_HELPER, mHelper);

                    panelHolder.getContext().registerReceiver(mGlobalReceiver, NFW.STATUS_BAR_FILTER);
                    logD("attached to status bar");
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
                                mHelper.resetState();
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
