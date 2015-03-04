package jp.tkgktyk.xposed.niwatori.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.support.annotation.StringRes;
import android.util.Log;

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
        if (mSettingsFragment != null) {
            mSettingsFragment.setHasPremiumSettings(mHasPremiumSettings);
        }
    }

    private void disablePremiumSettings() {
        mHasPremiumSettings = false;
        NFW.getSharedPreferences(SettingsActivity.this)
                .edit()
                .putBoolean(getString(R.string.key_purchase_premium_settings), false)
                .apply();
        if (mSettingsFragment != null) {
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
        @SuppressWarnings("deprecation")
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesMode(PreferenceActivity.MODE_WORLD_READABLE);
        }

        protected Preference findPreference(@StringRes int id) {
            return findPreference(getString(id));
        }

        protected void showListSummary(@StringRes int id) {
            ListPreference list = (ListPreference) findPreference(id);
            list.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    setListSummary((ListPreference) preference, (String) newValue);
                    return true;
                }
            });
            setListSummary(list, list.getValue());
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
            EditTextPreference et = (EditTextPreference) findPreference(id);
            et.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    preference.setSummary(getString(R.string.current_s1, (String) newValue));
                    return true;
                }
            });
            et.getOnPreferenceChangeListener().onPreferenceChange(et,
                    et.getText());
        }

        protected void openSelectorOnClick(@StringRes int id, @StringRes final int title) {
            Preference pref = findPreference(id);
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent activity = new Intent(preference.getContext(), AppSelectActivity.class);
                    activity.putExtra(AppSelectActivity.EXTRA_PREF_KEY_STRING, preference.getKey());
                    activity.putExtra(AppSelectActivity.EXTRA_TITLE_ID, title);
                    startActivity(activity);
                    return true;
                }
            });
        }

        protected void openActivity(@StringRes int id, final Class<?> cls) {
            Preference pref = findPreference(id);
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent activity = new Intent(preference.getContext(), cls);
                    startActivity(activity);
                    return true;
                }
            });
        }
    }

    public static class SettingsFragment extends BaseFragment {
        private static final int mPremiumSettings[] = {
                R.string.key_initial_position,
                R.string.key_animation,
                R.string.key_notify_flying
        };
        private static final int mPurchasePremiumSettings = R.string.key_purchase_premium_settings;
        private static String ARG_HAS_PREMIUM_SETTINGS = NFW.PACKAGE_NAME + ".HAS_PREMIUM_SETTINGS";

        private SettingsActivity mSettingsActivity;
        private boolean mHasPremiumSettings;

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
        }

        @Override
        public void onDetach() {
            super.onDetach();
            mSettingsActivity = null;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_settings);

            setHasPremiumSettings(getArguments().getBoolean(ARG_HAS_PREMIUM_SETTINGS, false));

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
            showTextSummary(R.string.key_speed);
            openSelectorOnClick(R.string.key_black_list, R.string.black_list_activity_name);
            //
            // Premium Settings
            //
            openActivity(R.string.key_initial_position, InitialPositionActivity.class);
            // Test
            showListSummary(R.string.key_action_when_tap_on_recents);
            showListSummary(R.string.key_action_when_long_press_on_recents);
            showListSummary(R.string.key_action_when_double_tap_on_recents);
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
            for (int i : mPremiumSettings) {
                findPreference(i).setEnabled(mHasPremiumSettings);
            }
        }

        private void disablePremiumSettings() {
            setHasPremiumSettings(false);
            Preference pref = findPreference(mPurchasePremiumSettings);
            pref.setEnabled(false);
            pref.setSummary(R.string.summary_unavailable_premium_settings);
        }
    }
}
