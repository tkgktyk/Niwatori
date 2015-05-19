package jp.tkgktyk.xposed.niwatori;

import android.os.Build;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by tkgktyk on 2015/02/21.
 */
public class ModNavigationKey extends XposedModule {
    private static final String CLASS_PHONE_STATUS_BAR = "com.android.systemui.statusbar.phone.PhoneStatusBar";

    private static XSharedPreferences mPrefs;
    private static NFW.Settings mSettings;

    public static void initZygote(XSharedPreferences prefs) {
        mPrefs = prefs;
    }

    public static void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        try {
            if (!loadPackageParam.packageName.equals("com.android.systemui")) {
                return;
            }
            //
            // for Software Keys
            //
            mSettings = newSettings(mPrefs);
            if (mSettings.extraActionOnRecents != NFW.NONE_ON_RECENTS) {
                final ClassLoader classLoader = loadPackageParam.classLoader;
                modifySoftwareKey(classLoader);
                log("prepared to modify software recents key");
            }
        } catch (Throwable t) {
            logE(t);
        }
    }

    private static void modifySoftwareKey(ClassLoader classLoader) {
        final Class<?> classPhoneStatusBar = XposedHelpers.findClass(CLASS_PHONE_STATUS_BAR, classLoader);
        XposedBridge.hookAllMethods(classPhoneStatusBar, "prepareNavigationBarView",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        logD("prepareNavigationBarView");
                        try {
                            Object phoneStatusBar = param.thisObject;
                            final View navigationBarView = (View) XposedHelpers.getObjectField(
                                    phoneStatusBar, "mNavigationBarView");
                            modifyRecentsKey(phoneStatusBar, navigationBarView);
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
                        }
                        final View.OnLongClickListener longClickListener = localLCL;
                        recentsButton.setLongClickable(false);
                        recentsButton.setOnLongClickListener(null);
                        recentsButton.setOnClickListener(null);
                        final GestureDetector gestureDetector = new GestureDetector(
                                navigationBarView.getContext(), new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onSingleTapConfirmed(MotionEvent e) {
                                try {
                                    if (mSettings.extraActionOnRecents != NFW.TAP_ON_RECENTS) {
                                        clickListener.onClick(recentsButton);
                                    } else {
                                        NFW.performAction(recentsButton.getContext(),
                                                mSettings.extraAction);
                                    }
                                } catch (Throwable t) {
                                    logE(t);
                                }
                                return true;
                            }

                            @Override
                            public void onLongPress(MotionEvent e) {
                                try {
                                    if (mSettings.extraActionOnRecents != NFW.LONG_PRESS_ON_RECENTS) {
                                        if (longClickListener != null) {
                                            longClickListener.onLongClick(recentsButton);
                                        } else {
                                            clickListener.onClick(recentsButton);
                                        }
                                    } else {
                                        NFW.performAction(recentsButton.getContext(),
                                                mSettings.extraAction);
                                    }
                                    recentsButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                                } catch (Throwable t) {
                                    logE(t);
                                }
                            }

                            @Override
                            public boolean onDoubleTap(MotionEvent e) {
                                try {
                                    if (mSettings.extraActionOnRecents != NFW.DOUBLE_TAP_ON_RECENTS) {
                                        if (mSettings.extraActionOnRecents == NFW.TAP_ON_RECENTS) {
                                            clickListener.onClick(recentsButton);
                                        } else {
                                            return false;
                                        }
                                    } else {
                                        NFW.performAction(recentsButton.getContext(),
                                                mSettings.extraAction);
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
