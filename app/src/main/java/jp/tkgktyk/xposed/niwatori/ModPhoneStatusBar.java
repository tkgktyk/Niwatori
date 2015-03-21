package jp.tkgktyk.xposed.niwatori;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.os.Build;
import android.support.annotation.NonNull;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
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
    private static final String CLASS_PHONE_STATUS_BAR = "com.android.systemui.statusbar.phone.PhoneStatusBar";
    private static final String CLASS_PHONE_STATUS_BAR_VIEW = "com.android.systemui.statusbar.phone.PhoneStatusBarView";
    private static final String CLASS_PANEL_HOLDER = "com.android.systemui.statusbar.phone.PanelHolder";

    private static final String FIELD_FLYING_HELPER = NFW.NAME + "_flyingHelper";

    private static XSharedPreferences mPrefs;
    // for status bar
    private static FlyingHelper mHelper;
    // for navigation key
    private static NFW.Settings mNavSettings;

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

    public static void initZygote(XSharedPreferences prefs) {
        mPrefs = prefs;
    }

    public static void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (!loadPackageParam.packageName.equals("com.android.systemui")) {
            return;
        }
        try {
            installToStatusBar(loadPackageParam.classLoader);
            modifySoftwareKey(loadPackageParam.classLoader);
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
                    mNavSettings = newSettings(mPrefs);
                    mHelper = new FlyingHelper(panelHolder, 1, false, mNavSettings);
                    XposedHelpers.setAdditionalInstanceField(panelHolder,
                            FIELD_FLYING_HELPER, mHelper);

                    panelHolder.getContext().registerReceiver(mGlobalReceiver, NFW.STATUS_BAR_FILTER);
                    panelHolder.getContext().registerReceiver(new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            logD("reload settings");
                            // need to reload on each package?
                            NFW.Settings settings = (NFW.Settings) intent.getSerializableExtra(NFW.EXTRA_SETTINGS);
                            mNavSettings = settings;
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
                                mHelper.resetState(true);
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

    private static void modifySoftwareKey(ClassLoader classLoader) {
        final Class<?> classPhoneStatusBar = XposedHelpers.findClass(CLASS_PHONE_STATUS_BAR, classLoader);
        XposedHelpers.findAndHookMethod(classPhoneStatusBar, "prepareNavigationBarView",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        logD("prepareNavigationBarView");
                        try {
                            if (mNavSettings.testFeature) {
                                logD("attached to Navigation Bar");
                                final Object phoneStatusBar = param.thisObject;
                                final View navigationBarView = (View) XposedHelpers.getObjectField(
                                        phoneStatusBar, "mNavigationBarView");
                                modifyRecentsKey(phoneStatusBar, navigationBarView);
                            }
                        } catch (Throwable t) {
                            logE(t);
                        }
                    }

                    private void modifyRecentsKey(final Object phoneStatusBar, View navigationBarView) {
                        final View recentsButton = (View) XposedHelpers.callMethod(
                                navigationBarView, "getRecentsButton");
                        final View.OnClickListener clickListener
                                = (View.OnClickListener) XposedHelpers.getObjectField(
                                phoneStatusBar, "mRecentsClickListener");
                        final View.OnTouchListener touchListener
                                = (View.OnTouchListener) XposedHelpers.getObjectField(
                                phoneStatusBar, "mRecentsPreloadOnTouchListener");
                        View.OnLongClickListener localLCL = null;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            localLCL = (View.OnLongClickListener) XposedHelpers.getObjectField(
                                    phoneStatusBar, "mLongPressBackRecentsListener");
                            recentsButton.setLongClickable(false);
                            recentsButton.setOnLongClickListener(null);
                        }
                        final View.OnLongClickListener longClickListener = localLCL;
                        recentsButton.setOnClickListener(null);
                        final GestureDetector gestureDetector = new GestureDetector(
                                navigationBarView.getContext(), new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onSingleTapConfirmed(MotionEvent e) {
                                try {
                                    if (NFW.isDefaultAction(mNavSettings.actionWhenTapOnRecents)) {
                                        clickListener.onClick(recentsButton);
                                    } else {
                                        NFW.performAction(recentsButton.getContext(),
                                                mNavSettings.actionWhenTapOnRecents);
                                    }
                                } catch (Throwable t) {
                                    logE(t);
                                }
                                return true;
                            }

                            @Override
                            public void onLongPress(MotionEvent e) {
                                try {
                                    if (NFW.isDefaultAction(mNavSettings.actionWhenLongPressOnRecents)) {
                                        if (longClickListener != null) {
                                            longClickListener.onLongClick(recentsButton);
                                        } else {
                                            clickListener.onClick(recentsButton);
                                        }
                                    } else {
                                        NFW.performAction(recentsButton.getContext(),
                                                mNavSettings.actionWhenLongPressOnRecents);
                                    }
                                    recentsButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                                } catch (Throwable t) {
                                    logE(t);
                                }
                            }

                            @Override
                            public boolean onDoubleTap(MotionEvent e) {
                                try {
                                    if (NFW.isDefaultAction(mNavSettings.actionWhenDoubleTapOnRecents)) {
                                        if (!NFW.isDefaultAction(mNavSettings.actionWhenTapOnRecents)) {
                                            clickListener.onClick(recentsButton);
                                        } else {
                                            return false;
                                        }
                                    } else {
                                        NFW.performAction(recentsButton.getContext(),
                                                mNavSettings.actionWhenDoubleTapOnRecents);
                                    }
                                    recentsButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                                } catch (Throwable t) {
                                    logE(t);
                                }
                                return true;
                            }
                        });
                        recentsButton.setOnTouchListener(new View.OnTouchListener() {
                            @Override
                            public boolean onTouch(View v, MotionEvent event) {
                                try {
                                    // original touchListener always return false.
                                    touchListener.onTouch(v, event);
                                } catch (Throwable t) {
                                    logE(t);
                                }
                                return gestureDetector.onTouchEvent(event);
                            }
                        });
                    }
                });
    }
}
