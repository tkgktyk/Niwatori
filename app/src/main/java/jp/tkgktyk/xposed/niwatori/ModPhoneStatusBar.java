package jp.tkgktyk.xposed.niwatori;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by tkgktyk on 2015/02/13.
 */
public class ModPhoneStatusBar extends XposedModule {
    private static final String CLASS_PHONE_STATUS_BAR = "com.android.systemui.statusbar.phone.PhoneStatusBar";
    private static final String CLASS_PHONE_STATUS_BAR_VIEW = "com.android.systemui.statusbar.phone.PhoneStatusBarView";

    private static XSharedPreferences mPrefs;

    private static Object mPhoneStatusBar;
    private static View mPhoneStatusBarView;
    private static View mHelperHolder;
    private static final BroadcastReceiver mGlobalReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            logD("global broadcast receiver: " + intent.getAction());
            final int mState = XposedHelpers.getIntField(mPhoneStatusBarView, "mState");
            if (mState == 0) { // STATE_CLOSED = 0
                return;
            }
            // target is status bar
            final FlyingHelper helper = FlyingHelper.getFromHelperHolder(mHelperHolder);
            if (helper != null) {
                final String action = intent.getAction();
                helper.performAction(action);
                abortBroadcast();
                logD("consumed: " + action);
            } else {
                logD("FlyingHelper is not found.");
            }
        }
    };

    public static void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam,
                                         XSharedPreferences prefs) {
        mPrefs = prefs;
        if (!loadPackageParam.packageName.equals("com.android.systemui")) {
            return;
        }
        try {
            //
            // Install FlyingLayout
            //
            final ClassLoader classLoader = loadPackageParam.classLoader;
            XposedHelpers.findAndHookMethod(CLASS_PHONE_STATUS_BAR, classLoader,
                    "makeStatusBarView", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            logD("makeStatusBarView");
                            mPhoneStatusBar = param.thisObject;
                            mPhoneStatusBarView = (View) XposedHelpers.getObjectField(
                                    mPhoneStatusBar, "mStatusBarView");
                            try {
                                // install
                                // need for before Lollipop
                                installFlyingLayout();
                            } catch (Throwable t) {
                                logE(t);
                            }
                        }

                    });
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                XposedHelpers.findAndHookMethod(CLASS_PHONE_STATUS_BAR, classLoader,
                        "showKeyguard", new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                logD("showKeyguard");
                                try {
                                    uninstallFlyingLayout();
                                } catch (Throwable t) {
                                    logE(t);
                                }
                            }
                        });
                XposedHelpers.findAndHookMethod(CLASS_PHONE_STATUS_BAR, classLoader,
                        "hideKeyguard", new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                logD("hideKeyguard");
                                try {
                                    // install
                                    installFlyingLayout();
                                } catch (Throwable t) {
                                    logE(t);
                                }
                            }
                        });
            }
            //
            // Reset state when status bar collapsed
            //
            try {
                XposedHelpers.findAndHookMethod(CLASS_PHONE_STATUS_BAR_VIEW, classLoader,
                        "onAllPanelsCollapsed", new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                try {
                                    final FlyingHelper helper = FlyingHelper
                                            .getFrom((View) param.thisObject);
                                    if (helper != null) {
                                        helper.resetState();
                                    } else {
                                        logD("FlyingHelper is not found.");
                                    }
                                } catch (Throwable t) {
                                    logE(t);
                                }
                            }
                        });
            } catch (NoSuchMethodError e) {
                log("PhoneStatusBarView#onAllPanelsCollapsed is not found.");
            }
        } catch (Throwable t) {
            logE(t);
        }
    }

    private static void installFlyingLayout() throws Throwable {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // before Lollipop
            final ScrollView scrollView = (ScrollView) XposedHelpers.getObjectField(
                    mPhoneStatusBar, "mScrollView");
            final ViewGroup target = (ViewGroup) scrollView.getParent();
            FlyingHelper helper = FlyingHelper.getFrom(target);
            if (helper == null) {
                setHeightToMatchParent((View) target.getParent());
                setHeightToMatchParent(target);
                helper = new FlyingHelper(newSettings(mPrefs));
                mHelperHolder = helper.install(target);
            }
        } else {
            // Lollipop or later
            // NotificationPanel includes top menu.
            final ViewGroup notificationPanel = (ViewGroup) XposedHelpers.getObjectField(
                    mPhoneStatusBar, "mNotificationPanel");
            FlyingHelper helper = FlyingHelper.getFrom(notificationPanel);
            if (helper == null) {
                helper = new FlyingHelper(newSettings(mPrefs));
                mHelperHolder = helper.install(notificationPanel);
            }
        }
        // register broadcast for shortcut
        if (mHelperHolder == null) {
            log("could not attach to status bar");
            return;
        }
        final Context context = (Context) XposedHelpers.getObjectField(
                mPhoneStatusBar, "mContext");
        context.registerReceiver(mGlobalReceiver, NFW.STATUS_BAR_FILTER);
        logD("attached to status bar");
    }

    private static void setHeightToMatchParent(View view) {
        final ViewGroup.LayoutParams lp = view.getLayoutParams();
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        view.setLayoutParams(lp);
    }

    private static void uninstallFlyingLayout() throws Throwable {
        if (mHelperHolder == null) {
            logD("not installed");
            return;
        }
        FlyingHelper helper = null;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // before Lollipop
            final ScrollView scrollView = (ScrollView) XposedHelpers.getObjectField(
                    mPhoneStatusBar, "mScrollView");
            final ViewGroup target = (ViewGroup) scrollView.getParent();
            helper = FlyingHelper.getFrom(target);
        } else {
            // Lollipop or later
            // NotificationPanel includes top menu.
            final ViewGroup notificationPanel = (ViewGroup) XposedHelpers.getObjectField(
                    mPhoneStatusBar, "mNotificationPanel");
            helper = FlyingHelper.getFrom(notificationPanel);
        }
        if (helper == null) {
            logD("FlyingHelper is not found.");
            return;
        }
        helper.uninstall();
    }
}
