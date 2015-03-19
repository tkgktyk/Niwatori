package jp.tkgktyk.xposed.niwatori.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import jp.tkgktyk.flyinglayout.FlyingLayout;
import jp.tkgktyk.xposed.niwatori.NFW;
import jp.tkgktyk.xposed.niwatori.R;

/**
 * Created by tkgktyk on 2015/03/17.
 */
public class SettingsActionReceiver extends BroadcastReceiver {
    private static final String TAG = SettingsActionReceiver.class.getSimpleName();

    public static final String EXTRA_LAYOUT_ADJUSTMENT = NFW.PACKAGE_NAME + ".intent.extra.LAYOUT_ADJUSTMENT";

    public static void sendBroadcast(Context context, int layoutAdjustment) {
        Intent intent = new Intent(NFW.ACTION_ADJUST_LAYOUT);
        intent.putExtra(EXTRA_LAYOUT_ADJUSTMENT, layoutAdjustment);
        context.sendBroadcast(intent);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        Log.d(TAG, action);
        if (action.equals(NFW.ACTION_ADJUST_LAYOUT)) {
            final SharedPreferences prefs = NFW.getSharedPreferences(context);
            int layoutAdjustment = intent.getIntExtra(EXTRA_LAYOUT_ADJUSTMENT,
                    FlyingLayout.DEFAULT_LAYOUT_ADJUSTMENT);
            prefs.edit()
                    .putString(context.getString(R.string.key_layout_adjustment),
                            Integer.toString(layoutAdjustment))
                    .commit();
            context.sendBroadcast(new Intent(NFW.ACTION_SETTINGS_CHANGED));
        }
    }
}
