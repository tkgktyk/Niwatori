package jp.tkgktyk.xposed.niwatori;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import jp.tkgktyk.flyinglayout.FlyingLayout;

/**
 * Created by tkgktyk on 2015/02/12.
 */
public class ModFlyingLayout extends XposedModule {
    private static XSharedPreferences mPrefs;

    public static void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam,
                                         XSharedPreferences prefs) {
        mPrefs = prefs;
        try {
            // fetch local FlyingLayout
            final Class<?> classFlyingLayout = XposedHelpers.findClass(
                    FlyingLayout.class.getName(), loadPackageParam.classLoader);
            XposedBridge.hookAllConstructors(classFlyingLayout, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final FlyingLayout flying = (FlyingLayout) param.thisObject;
                    NFW.Settings settings = newSettings(mPrefs);
                    flying.setSpeed(settings.speed);
                }
            });
        } catch (XposedHelpers.ClassNotFoundError e) {
            // this package doesn't have FlyingLayout
        }
    }
}
