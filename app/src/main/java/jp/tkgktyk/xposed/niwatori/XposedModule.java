package jp.tkgktyk.xposed.niwatori;

import java.lang.reflect.InvocationTargetException;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

/**
 * Created by tkgktyk on 2015/02/08.
 */
public class XposedModule {
    private static XSharedPreferences mPrefs;
    protected static NFW.Settings mSettings;

    public static void initializePreferences() {
        final XSharedPreferences prefs = new XSharedPreferences(NFW.PACKAGE_NAME);
        prefs.makeWorldReadable();
        mSettings = new NFW.Settings(prefs);
        mPrefs = prefs;
    }

    public static void reloadPreferences() {
        mSettings = new NFW.Settings(mPrefs);
    }

    private static String prefix() {
        int stack = 4;
        String method = Thread.currentThread().getStackTrace()[stack].getClassName();
        method += "#" + Thread.currentThread().getStackTrace()[stack].getMethodName();
        method = method.substring(method.lastIndexOf(".") + 1);
        return NFW.NAME + "(" + method + ")";
    }

    public static void logD() {
        if (BuildConfig.DEBUG) {
            XposedBridge.log(prefix() + " [DEBUG]");
        }
    }

    public static void logD(String text) {
        if (BuildConfig.DEBUG) {
            XposedBridge.log(prefix() + " [DEBUG]: " + text);
        }
    }

    public static void log(String text) {
        XposedBridge.log(prefix() + ": " + text);
    }

    public static void logE(Throwable t) {
        XposedBridge.log(t);
    }

    public static Object invokeOriginalMethod(XC_MethodHook.MethodHookParam methodHookParam)
            throws InvocationTargetException, IllegalAccessException {
        return XposedBridge.invokeOriginalMethod(methodHookParam.method,
                methodHookParam.thisObject, methodHookParam.args);
    }
}
