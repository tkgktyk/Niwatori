package jp.tkgktyk.xposed.niwatori;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.base.Strings;

import java.io.Serializable;
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
    public static final String ACTION_TOGGLE = PACKAGE_NAME + ".intent.action.TOGGLE";
    public static final String ACTION_PIN = PACKAGE_NAME + ".intent.action.PIN";
    public static final String ACTION_PIN_OR_RESET = PACKAGE_NAME + ".intent.action.PIN_OR_RESET";
    public static final String ACTION_RESIZE = PACKAGE_NAME + ".intent.action.RESIZE";
    public static final String ACTION_ADJUST_LAYOUT = PACKAGE_NAME + ".intent.action.ADJUST_LAYOUT";
    public static final String ACTION_SMALL_SCREEN_LEFT = PACKAGE_NAME + ".intent.action.ACTION_SMALL_SCREEN_LEFT";
    public static final String ACTION_SMALL_SCREEN_RIGHT = PACKAGE_NAME + ".intent.action.ACTION_SMALL_SCREEN_RIGHT";
    public static final String ACTION_RESET = PACKAGE_NAME + ".intent.action.RESET";
    public static final String ACTION_SOFT_RESET = PACKAGE_NAME + ".intent.action.SOFT_RESET";

    public static final String ACTION_SETTINGS_CHANGED = PACKAGE_NAME + ".intent.action.SETTINGS_CHANGED";
    public static final String ACTION_SETTINGS_LOADED = PACKAGE_NAME + ".intent.action.SETTINGS_LOADED";

    /**
     * Static IntentFilters
     */
    public static final IntentFilter STATUS_BAR_FILTER;
    public static final IntentFilter FOCUSED_DIALOG_FILTER;
    public static final IntentFilter FOCUSED_ACTIVITY_FILTER;
    public static final IntentFilter ACTIVITY_FILTER;
    public static final IntentFilter SETTINGS_CHANGED_FILTER = new IntentFilter(ACTION_SETTINGS_CHANGED);
    public static final IntentFilter SETTINGS_LOADED_FILTER = new IntentFilter(ACTION_SETTINGS_LOADED);
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

    /**
     * IntentFilters initialization
     */
    static {
        STATUS_BAR_FILTER = new IntentFilter();
        STATUS_BAR_FILTER.addAction(NFW.ACTION_TOGGLE);
        STATUS_BAR_FILTER.addAction(NFW.ACTION_PIN);
        STATUS_BAR_FILTER.addAction(NFW.ACTION_PIN_OR_RESET);
        STATUS_BAR_FILTER.addAction(NFW.ACTION_RESIZE);
//        STATUS_BAR_FILTER.addAction(NFW.ACTION_ADJUST_LAYOUT);
        STATUS_BAR_FILTER.addAction(NFW.ACTION_SMALL_SCREEN_LEFT);
        STATUS_BAR_FILTER.addAction(NFW.ACTION_SMALL_SCREEN_RIGHT);
        STATUS_BAR_FILTER.addAction(NFW.ACTION_RESET);
        STATUS_BAR_FILTER.addAction(NFW.ACTION_SOFT_RESET);
        FOCUSED_DIALOG_FILTER = new IntentFilter(STATUS_BAR_FILTER);
        FOCUSED_ACTIVITY_FILTER = new IntentFilter(STATUS_BAR_FILTER);
        ACTIVITY_FILTER = new IntentFilter(STATUS_BAR_FILTER);
        // Priority
        STATUS_BAR_FILTER.setPriority(NFW.PRIORITY_STATUS_BAR);
        FOCUSED_DIALOG_FILTER.setPriority(NFW.PRIORITY_FOCUSED_DIALOG);
        FOCUSED_ACTIVITY_FILTER.setPriority(NFW.PRIORITY_FOCUSED_ACTIVITY);
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

    public static Context getNiwatoriContext(Context context) {
        Context niwatoriContext = null;
        try {
            niwatoriContext = context.createPackageContext(
                    NFW.PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY);
        } catch (Throwable t) {
            XposedModule.logE(t);
        }
        return niwatoriContext;
    }

    public static GradientDrawable makeBoundaryDrawable(int width, int color) {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.TRANSPARENT);
        drawable.setStroke(width, color);
        return drawable;
    }

    public static class Settings implements Serializable {
        private final XSharedPreferences mPrefs;

        public Set<String> blackSet;
        public boolean animation;
        public String actionWhenTapOutside;
        public String actionWhenLongPressOutside;
        public String actionWhenDoubleTapOutside;

        public float speed;
        public boolean drawBoundary;
        public int boundaryColor;
        public int initialXp;
        public int initialYp;

        public int layoutAdjustment;
        public float smallScreenSize;

        public boolean testFeature;
        public boolean resetAutomatically;
        public String actionWhenTapOnRecents;
        public String actionWhenLongPressOnRecents;
        public String actionWhenDoubleTapOnRecents;

        public Settings(XSharedPreferences prefs) {
            mPrefs = prefs;
            reload();
        }

        public void reload() {
            mPrefs.reload();
            blackSet = mPrefs.getStringSet("key_black_list", Collections.<String>emptySet());
            animation = mPrefs.getBoolean("key_animation", true);
            actionWhenTapOutside = mPrefs.getString("key_action_when_tap_outside", ACTION_SOFT_RESET);
            actionWhenLongPressOutside = mPrefs.getString("key_action_when_long_press_outside", ACTION_ADJUST_LAYOUT);
            actionWhenDoubleTapOutside = mPrefs.getString("key_action_when_double_tap_outside", ACTION_PIN);

            speed = Float.parseFloat(mPrefs.getString("key_speed", Float.toString(FlyingLayout.DEFAULT_SPEED)));
            drawBoundary = mPrefs.getBoolean("key_draw_boundary", true);
            boundaryColor = Color.parseColor(mPrefs.getString("key_boundary_color", "#689F38")); // default is Green
            initialXp = mPrefs.getInt("key_initial_x_percent", InitialPosition.DEFAULT_X_PERCENT);
            initialYp = mPrefs.getInt("key_initial_y_percent", InitialPosition.DEFAULT_Y_PERCENT);

            layoutAdjustment = Integer.parseInt(
                    mPrefs.getString("key_layout_adjustment", Integer.toString(FlyingLayout.DEFAULT_LAYOUT_ADJUSTMENT))
            );
            smallScreenSize = Float.parseFloat(mPrefs.getString("key_small_screen_size", "70")) / 100f;

            testFeature = mPrefs.getBoolean("key_test_feature", false);
            if (testFeature) {
                resetAutomatically = mPrefs.getBoolean("key_reset_automatically", false);
                actionWhenTapOnRecents = mPrefs.getString("key_action_when_tap_on_recents", ACTION_DEFAULT);
                actionWhenLongPressOnRecents = mPrefs.getString("key_action_when_long_press_on_recents", ACTION_DEFAULT);
                actionWhenDoubleTapOnRecents = mPrefs.getString("key_action_when_double_tap_on_recents", ACTION_DEFAULT);
            } else {
                resetAutomatically = false;
                actionWhenTapOnRecents = ACTION_DEFAULT;
                actionWhenLongPressOnRecents = ACTION_DEFAULT;
                actionWhenDoubleTapOnRecents = ACTION_DEFAULT;
            }
        }
    }
}
