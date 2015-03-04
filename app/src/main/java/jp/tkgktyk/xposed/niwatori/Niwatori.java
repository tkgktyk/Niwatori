package jp.tkgktyk.xposed.niwatori;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by tkgktyk on 2015/02/12.
 */
public class Niwatori implements IXposedHookZygoteInit, IXposedHookLoadPackage {
    private static XSharedPreferences mPrefs;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        mPrefs = new XSharedPreferences(NFW.PACKAGE_NAME);
        mPrefs.makeWorldReadable();

        ModActivity.initZygote(mPrefs);
        ModInputMethod.initZygote();
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        ModPhoneStatusBar.handleLoadPackage(loadPackageParam, mPrefs);
//        ModFlyingLayout.handleLoadPackage(loadPackageParam, mPrefs);
        ModNavigationKey.handleLoadPackage(loadPackageParam, mPrefs);
    }
}
