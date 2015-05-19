package jp.tkgktyk.xposed.niwatori.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.util.Log;

import com.google.common.base.Strings;

import jp.tkgktyk.xposed.niwatori.BuildConfig;
import jp.tkgktyk.xposed.niwatori.NFW;
import jp.tkgktyk.xposed.niwatori.R;
import jp.tkgktyk.xposed.niwatori.app.util.IabHelper;
import jp.tkgktyk.xposed.niwatori.app.util.IabResult;
import jp.tkgktyk.xposed.niwatori.app.util.Inventory;
import jp.tkgktyk.xposed.niwatori.app.util.Purchase;

/**
 * Created by tkgktyk on 2015/02/13.
 */
public class SettingsActivity extends Activity {
    private static final String TAG = SettingsActivity.class.getSimpleName();
    private static final boolean PURCHASED = BuildConfig.DEBUG && true;

    // (arbitrary) request code for the purchase flow
    private static final int RC_REQUEST = 10001;
    private static final String SKU_PREMIUM_SETTINGS = "premium_settings";
    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener
            = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            // if we were disposed of in the meantime, quit.
            if (!isBillingSupported()) {
                return;
            }

            if (result.isFailure()) {
                complain("Error purchasing: " + result);
                return;
            }

            if (purchase.getSku().equals(SKU_PREMIUM_SETTINGS)) {
                // bought the premium settings!
                updatePremiumSettings(true);
            }
        }
    };
    private IabHelper mHelper;
    private boolean mHasPremiumSettings;
    private SettingsFragment mSettingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (BuildConfig.DEBUG) {
            setTitle("[DEBUG] " + getTitle());
        }

        mHasPremiumSettings =
                NFW.getSharedPreferences(SettingsActivity.this)
                        .getBoolean(getString(R.string.key_purchase_premium_settings), false);

        mSettingsFragment = SettingsFragment.newInstance(mHasPremiumSettings);
        getFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, mSettingsFragment)
                .commit();

        String base64EncodedPublicKey =
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtbZkkt7/GU/XBfnnQKPUPeub74NKsOi7TTdQLeFigeVwx2oLbTDoi094/AGF+yD3qKYn+fAtJrVKvZ7ebbESgR5EMub4VDunbhjYq+liAnVnEDr2AWWf7WiOzYaYElpA6xi02Aa1Sslw+iE62ThUA1c6454+rI/g6X7n4URFJSzVd+ZkqT6itEvnr0/apLIs05xhZBLRQTF15c6zDOTejVdqNaZdwFHVDCD2w71uuoFgRhdO3GqFmjwFWyaBkdj3V+8ZZ+dOd+6WX5GysE6uans2zBrmGR+ujUQPNymjXAj1H3o+XTHUIBo4gnlFqGeM9ncQOSSdpfX5w3aISxDwMwIDAQAB";
        mHelper = new IabHelper(this, base64EncodedPublicKey);
        mHelper.enableDebugLogging(BuildConfig.DEBUG);
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    Log.d(TAG, "In-app Billing setup failed: " + result);
                    releaseBilling();
                    disablePremiumSettings();
                } else {
                    Log.d(TAG, "In-app Billing is set up OK");
                }
                // Have we been disposed of in the meantime? If so, quit.
                if (!isBillingSupported()) {
                    return;
                }
                // Listener that's called when we finish querying the items and subscriptions we own
                mHelper.queryInventoryAsync(new IabHelper.QueryInventoryFinishedListener() {
                    public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
                        // Have we been disposed of in the meantime? If so, quit.
                        if (!isBillingSupported()) {
                            return;
                        }

                        // Is it a failure?
                        if (result.isFailure()) {
                            complain("Failed to query inventory: " + result);
                            return;
                        }

                        /*
                         * Check for items we own. Notice that for each purchase, we check
                         * the developer payload to see if it's correct! See
                         * verifyDeveloperPayload().
                         */
                        // Do we have the premium settings
                        updatePremiumSettings(inventory.hasPurchase(SKU_PREMIUM_SETTINGS));
                    }
                });
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseBilling();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!(isBillingSupported() && mHelper.handleActivityResult(requestCode, resultCode, data))) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void purchasePremiumSettings() {
        if (isBillingSupported()) {
            mHelper.launchPurchaseFlow(this, SKU_PREMIUM_SETTINGS, RC_REQUEST, mPurchaseFinishedListener);
        }
    }

    private void updatePremiumSettings(boolean purchased) {
        mHasPremiumSettings = purchased;
        NFW.getSharedPreferences(SettingsActivity.this)
                .edit()
                .putBoolean(getString(R.string.key_purchase_premium_settings), purchased)
                .apply();
        if (mSettingsFragment != null && mSettingsFragment.isVisible()) {
            mSettingsFragment.setHasPremiumSettings(mHasPremiumSettings);
        }
    }

    private void disablePremiumSettings() {
        mHasPremiumSettings = false;
        NFW.getSharedPreferences(SettingsActivity.this)
                .edit()
                .putBoolean(getString(R.string.key_purchase_premium_settings), false)
                .apply();
        if (mSettingsFragment != null && mSettingsFragment.isVisible()) {
            mSettingsFragment.disablePremiumSettings();
        }
    }

    private boolean isBillingSupported() {
        return mHelper != null;
    }

    private void releaseBilling() {
        if (isBillingSupported()) {
            mHelper.dispose();
            mHelper = null;
        }
    }

    private void complain(String message) {
        Log.e(TAG, "**** Niwatori Billing Error: " + message);
    }

    public static class BaseFragment extends PreferenceFragment {
        private final SharedPreferences.OnSharedPreferenceChangeListener mChangeListener
                = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                NFW.sendSettingsChanged(getActivity(), sharedPreferences);
            }
        };

        @SuppressWarnings("deprecation")
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesMode(PreferenceActivity.MODE_WORLD_READABLE);
            getPreferenceManager().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(mChangeListener);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            getPreferenceManager().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(mChangeListener);
        }

        protected Preference findPreference(@StringRes int id) {
            return findPreference(getString(id));
        }

        protected void showListSummary(@StringRes int id) {
            showListSummary(id, null);
        }

        protected void showListSummary(@StringRes int id,
                                       @Nullable final Preference.OnPreferenceChangeListener extraListener) {
            ListPreference list = (ListPreference) findPreference(id);
            list.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    setListSummary((ListPreference) preference, (String) newValue);
                    if (extraListener != null) {
                        return extraListener.onPreferenceChange(preference, newValue);
                    }
                    return true;
                }
            });
            // pre-perform
            list.getOnPreferenceChangeListener().onPreferenceChange(list, list.getValue());
        }

        private void setListSummary(ListPreference pref, String value) {
            int index = pref.findIndexOfValue(value);
            CharSequence entry;
            if (index != -1) {
                entry = pref.getEntries()[index];
            } else {
                entry = "default";
            }
            pref.setSummary(getString(R.string.current_s1, entry));
        }

        protected void showTextSummary(@StringRes int id) {
            showTextSummary(id, null);
        }

        protected void showTextSummary(@StringRes int id, @Nullable final String suffix) {
            EditTextPreference et = (EditTextPreference) findPreference(id);
            et.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String value = (String) newValue;
                    if (!Strings.isNullOrEmpty(suffix)) {
                        value += suffix;
                    }
                    preference.setSummary(getString(R.string.current_s1, value));
                    return true;
                }
            });
            et.getOnPreferenceChangeListener().onPreferenceChange(et,
                    et.getText());
        }

        protected void openActivity(@StringRes int id, final Class<?> cls) {
            openActivity(id, cls, null);
        }

        protected void openActivity(@StringRes int id, final Class<?> cls, final ExtendsPutter putter) {
            Preference pref = findPreference(id);
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent activity = new Intent(preference.getContext(), cls);
                    if (putter != null) {
                        putter.putExtends(activity);
                    }
                    startActivity(activity);
                    return true;
                }
            });
        }

        protected interface ExtendsPutter {
            public void putExtends(Intent activityIntent);
        }
    }

    public static class SettingsFragment extends BaseFragment
            implements ConfirmDialogFragment.OnConfirmedListener {
        private static final int REQUEST_INITIAL_POSITION = 1;
        private static final int REQUEST_SMALL_SCREEN = 2;

        private static final int mPurchasePremiumSettings = R.string.key_purchase_premium_settings;
        private static final String ARG_HAS_PREMIUM_SETTINGS = NFW.PACKAGE_NAME + ".HAS_PREMIUM_SETTINGS";

        private SettingsActivity mSettingsActivity;
        private boolean mHasPremiumSettings;

        private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updatePreferences();
            }
        };

        public static SettingsFragment newInstance(boolean hasPremiumSettings) {
            Bundle args = new Bundle();
            args.putBoolean(ARG_HAS_PREMIUM_SETTINGS, hasPremiumSettings);
            SettingsFragment settings = new SettingsFragment();
            settings.setArguments(args);
            return settings;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            mSettingsActivity = (SettingsActivity) activity;

            mSettingsActivity.registerReceiver(mReceiver, NFW.SETTINGS_CHANGED_FILTER);
        }

        @Override
        public void onDetach() {
            super.onDetach();

            mSettingsActivity.unregisterReceiver(mReceiver);

            mSettingsActivity = null;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_settings);

            setHasPremiumSettings(getArguments().getBoolean(ARG_HAS_PREMIUM_SETTINGS, false));

            updatePreferences();
        }

        private void updatePreferences() {
            if (mSettingsActivity != null && !mSettingsActivity.isBillingSupported()) {
                disablePremiumSettings();
            }

            findPreference(R.string.key_purchase_premium_settings)
                    .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            if (mSettingsActivity != null) {
                                mSettingsActivity.purchasePremiumSettings();
                            }
                            return true;
                        }
                    });

            // Settings
            openActivity(R.string.key_blacklist, AppSelectActivity.class, new ExtendsPutter() {
                @Override
                public void putExtends(Intent activityIntent) {
                    activityIntent.putExtra(AppSelectActivity.EXTRA_PREF_KEY_ID, R.string.key_blacklist);
                    activityIntent.putExtra(AppSelectActivity.EXTRA_TITLE_ID, R.string.blacklist_activity_name);
                }
            });
            showListSummary(R.string.key_extra_action);
            showListSummary(R.string.key_action_when_tap_outside);
            showListSummary(R.string.key_action_when_double_tap_outside);
            // movable screen
            showTextSummary(R.string.key_speed);
            showListSummary(R.string.key_boundary_color_ms, new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    final int width = Math.round(getResources().getDimension(R.dimen.boundary_width));
                    final int color = Color.parseColor((String) newValue);
                    final GradientDrawable drawable = NFW.makeBoundaryDrawable(width, color);
                    final int size = Math.round(getResources().getDimension(android.R.dimen.app_icon_size));
                    drawable.setSize(size, size);
                    preference.setIcon(drawable);
                    return true;
                }
            });
            findPreference(R.string.key_initial_position)
                    .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            if (mHasPremiumSettings) {
                                openActivity(InitialPositionActivity.class);
                            } else {
                                ConfirmDialogFragment.newInstance(
                                        getString(R.string.title_premium_settings),
                                        getString(R.string.note_premium_settings),
                                        null, SettingsFragment.this, REQUEST_INITIAL_POSITION)
                                        .show(getFragmentManager(), "initial_position");
                            }
                            return true;
                        }
                    });
            // small screen
            showListSummary(R.string.key_boundary_color_ss, new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    final int width = Math.round(getResources().getDimension(R.dimen.boundary_width));
                    final int color = Color.parseColor((String) newValue);
                    final GradientDrawable drawable = NFW.makeBoundaryDrawable(width, color);
                    final int size = Math.round(getResources().getDimension(android.R.dimen.app_icon_size));
                    drawable.setSize(size, size);
                    preference.setIcon(drawable);
                    return true;
                }
            });
            findPreference(R.string.key_small_screen)
                    .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            if (mHasPremiumSettings) {
                                openActivity(SmallScreenActivity.class);
                            } else {
                                ConfirmDialogFragment.newInstance(
                                        getString(R.string.title_premium_settings),
                                        getString(R.string.note_premium_settings),
                                        null, SettingsFragment.this, REQUEST_SMALL_SCREEN)
                                        .show(getFragmentManager(), "small_screen");
                            }
                            return true;
                        }
                    });
            openActivity(R.string.key_another_resize_method_targets, AppSelectActivity.class,
                    new ExtendsPutter() {
                        @Override
                        public void putExtends(Intent activityIntent) {
                            activityIntent.putExtra(AppSelectActivity.EXTRA_PREF_KEY_ID,
                                    R.string.key_another_resize_method_targets);
                            activityIntent.putExtra(AppSelectActivity.EXTRA_TITLE_ID,
                                    R.string.another_resize_method_targets_activity_name);
                        }
                    });
            // Other
            showListSummary(R.string.key_extra_action_on_recents);
            // About
            Preference about = findPreference(R.string.key_about);
            about.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent activity = new Intent(preference.getContext(), AboutActivity.class);
                    activity.putExtra(AboutActivity.EXTRA_DONATED, mHasPremiumSettings);
                    startActivity(activity);
                    return true;
                }
            });
            about.setSummary(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);
        }

        private void setHasPremiumSettings(boolean purchased) {
            mHasPremiumSettings = purchased || PURCHASED;
            findPreference(mPurchasePremiumSettings).setEnabled(!mHasPremiumSettings);
        }

        private void disablePremiumSettings() {
            setHasPremiumSettings(false);
            Preference pref = findPreference(mPurchasePremiumSettings);
            pref.setEnabled(false);
            pref.setSummary(R.string.summary_unavailable_premium_settings);
        }

        private void openActivity(Class<?> cls) {
            Intent activity = new Intent(getActivity(), cls);
            startActivity(activity);
        }

        @Override
        public void onConfirmed(int requestCode, Bundle extras) {
            switch (requestCode) {
                case REQUEST_INITIAL_POSITION:
                    openActivity(InitialPositionActivity.class);
                    break;
                case REQUEST_SMALL_SCREEN:
                    openActivity(SmallScreenActivity.class);
                    break;
            }
        }
    }
}
