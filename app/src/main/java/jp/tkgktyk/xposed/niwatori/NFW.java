package jp.tkgktyk.xposed.niwatori;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.base.Strings;

import java.util.Collections;
import java.util.Set;

import de.robv.android.xposed.XSharedPreferences;
import jp.tkgktyk.flyinglayout.FlyingLayout;

/**
 * Created by tkgktyk on 2015/02/12.
 * Niwatori - Fly the Window
 */
public class NFW {
    public static final String PACKAGE_NAME = NFW.class.getPackage().getName();
    public static final String NAME = NFW.class.getSimpleName();

    public static final String ACTION_DEFAULT = "";
    public static final String ACTION_RESET = PACKAGE_NAME + ".intent.action.RESET";
    public static final String ACTION_TOGGLE = PACKAGE_NAME + ".intent.action.TOGGLE";
    public static final String ACTION_PIN = PACKAGE_NAME + ".intent.action.PIN";
    public static final String ACTION_PIN_OR_RESET = PACKAGE_NAME + ".intent.action.PIN_OR_RESET";

    /**
     * Receivers are set priority.
     * 1. Status bar
     * 2. Focused Dialog
     * 3. Focused Activity
     * 4. Activity
     * *. Unfoused Dialog <- unregistered
     */
    private static final int PRIORITY_STATUS_BAR = IntentFilter.SYSTEM_HIGH_PRIORITY;
    private static final int PRIORITY_FOCUSED_DIALOG = IntentFilter.SYSTEM_HIGH_PRIORITY / 10;
    private static final int PRIORITY_FOCUSED_ACTIVITY = IntentFilter.SYSTEM_HIGH_PRIORITY / 100;
    private static final int PRIORITY_ACTIVITY = IntentFilter.SYSTEM_HIGH_PRIORITY / 1000;

    public static final IntentFilter STATUS_BAR_FILTER = new IntentFilter();
    public static final IntentFilter FOCUSED_DIALOG_FILTER = new IntentFilter();
    public static final IntentFilter FOCUSED_ACTIVITY_FILTER = new IntentFilter();
    public static final IntentFilter ACTIVITY_FILTER = new IntentFilter();
    static {
        STATUS_BAR_FILTER.addAction(NFW.ACTION_TOGGLE);
        STATUS_BAR_FILTER.addAction(NFW.ACTION_PIN);
        STATUS_BAR_FILTER.addAction(NFW.ACTION_PIN_OR_RESET);
        STATUS_BAR_FILTER.addAction(NFW.ACTION_RESET);
        STATUS_BAR_FILTER.setPriority(NFW.PRIORITY_STATUS_BAR);
        FOCUSED_DIALOG_FILTER.addAction(NFW.ACTION_TOGGLE);
        FOCUSED_DIALOG_FILTER.addAction(NFW.ACTION_PIN);
        FOCUSED_DIALOG_FILTER.addAction(NFW.ACTION_PIN_OR_RESET);
        FOCUSED_DIALOG_FILTER.addAction(NFW.ACTION_RESET);
        FOCUSED_DIALOG_FILTER.setPriority(NFW.PRIORITY_FOCUSED_DIALOG);
        FOCUSED_ACTIVITY_FILTER.addAction(NFW.ACTION_TOGGLE);
        FOCUSED_ACTIVITY_FILTER.addAction(NFW.ACTION_PIN);
        FOCUSED_ACTIVITY_FILTER.addAction(NFW.ACTION_PIN_OR_RESET);
        FOCUSED_ACTIVITY_FILTER.addAction(NFW.ACTION_RESET);
        FOCUSED_ACTIVITY_FILTER.setPriority(NFW.PRIORITY_FOCUSED_ACTIVITY);
        ACTIVITY_FILTER.addAction(NFW.ACTION_TOGGLE);
        ACTIVITY_FILTER.addAction(NFW.ACTION_PIN);
        ACTIVITY_FILTER.addAction(NFW.ACTION_PIN_OR_RESET);
        ACTIVITY_FILTER.addAction(NFW.ACTION_RESET);
        ACTIVITY_FILTER.setPriority(NFW.PRIORITY_ACTIVITY);
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("WorldReadableFiles")
    public static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(PACKAGE_NAME + "_preferences", Context.MODE_WORLD_READABLE);
    }

    public static void performAction(@NonNull Context context, @Nullable String action) {
        if (!Strings.isNullOrEmpty(action)) {
            context.sendOrderedBroadcast(new Intent(action), null);
        }
    }

    public static boolean isDefaultAction(@Nullable String action) {
        return Strings.isNullOrEmpty(action);
    }

    public static class Settings {
        public float speed;
        public int initialXp;
        public int initialYp;
        public boolean animation;
        public boolean notifyFlying;
        public Set<String> blackSet;

        public boolean testFeature;
        public boolean resetAutomatically;
        public String actionWhenTapOnRecents;
        public String actionWhenLongPressOnRecents;
        public String actionWhenDoubleTapOnRecents;

        public Settings(XSharedPreferences pref) {
            pref.reload();
            // for flying
            speed = Float.parseFloat(pref.getString("key_speed", Float.toString(FlyingLayout.DEFAULT_SPEED)));
            initialXp = pref.getInt("key_initial_x_percent", InitialPosition.DEFAULT_X_PERCENT);
            initialYp = pref.getInt("key_initial_y_percent", InitialPosition.DEFAULT_Y_PERCENT);
            animation = pref.getBoolean("key_animation", true);
            notifyFlying = pref.getBoolean("key_notify_flying", true);
            blackSet = pref.getStringSet("key_black_list", Collections.<String>emptySet());

            testFeature = pref.getBoolean("key_test_feature", false);
            if (testFeature) {
                resetAutomatically = pref.getBoolean("key_reset_automatically", false);
                actionWhenTapOnRecents = pref.getString("key_action_when_tap_on_recents", ACTION_DEFAULT);
                actionWhenLongPressOnRecents = pref.getString("key_action_when_long_press_on_recents", ACTION_DEFAULT);
                actionWhenDoubleTapOnRecents = pref.getString("key_action_when_double_tap_on_recents", ACTION_DEFAULT);
            } else {
                resetAutomatically = false;
                actionWhenTapOnRecents = ACTION_DEFAULT;
                actionWhenLongPressOnRecents = ACTION_DEFAULT;
                actionWhenDoubleTapOnRecents = ACTION_DEFAULT;
            }
        }
    }
}
