package jp.tkgktyk.xposed.niwatori;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by tkgktyk on 2015/02/12.
 */
public class Niwatori implements IXposedHookZygoteInit, IXposedHookLoadPackage {

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        XposedModule.initializePreferences();

        ModActivity.initZygote();
        ModInputMethod.initZygote();
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        ModPhoneStatusBar.handleLoadPackage(loadPackageParam);
//        ModFlyingLayout.handleLoadPackage(loadPackageParam,);
//        ModNavigationKey.handleLoadPackage(loadPackageParam);
    }
}
