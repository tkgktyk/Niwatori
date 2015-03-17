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

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        Log.d(TAG, action);
        if (action.equals(NFW.ACTION_ADJUST_LAYOUT)) {
            final SharedPreferences prefs = NFW.getSharedPreferences(context);
            final String key = context.getString(R.string.key_layout_adjustment);
            int layoutAdjustment = Integer.parseInt(
                    prefs.getString(key, Integer.toString(FlyingLayout.DEFAULT_LAYOUT_ADJUSTMENT))
            );
            switch (layoutAdjustment) {
                case FlyingLayout.LAYOUT_ADJUSTMENT_LEFT:
                    layoutAdjustment = FlyingLayout.LAYOUT_ADJUSTMENT_RIGHT;
                    break;
                case FlyingLayout.LAYOUT_ADJUSTMENT_RIGHT:
                default:
                    layoutAdjustment = FlyingLayout.LAYOUT_ADJUSTMENT_LEFT;
                    break;
            }
            prefs.edit()
                    .putString(key, Integer.toString(layoutAdjustment))
                    .commit();
            context.sendBroadcast(new Intent(NFW.ACTION_SETTINGS_CHANGED));
        }
    }
}
