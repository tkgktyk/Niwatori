package jp.tkgktyk.xposed.niwatori.app;

import android.app.Activity;
import android.app.Fragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.google.common.base.Strings;

import jp.tkgktyk.xposed.niwatori.NFW;
import jp.tkgktyk.xposed.niwatori.R;

/**
 * Created by tkgktyk on 2015/03/27.
 */
public class SmallScreenSimpleActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new SimpleSettingsFragment())
                    .commit();
        }
    }

    public static class SimpleSettingsFragment extends Fragment {

        private EditText mPivotXEdit;
        private EditText mPivotYEdit;
        private EditText mSizeEdit;

        public SimpleSettingsFragment() {
        }

        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_small_screen_simple, container, false);

            mPivotXEdit = (EditText) rootView.findViewById(R.id.pivotXEditText);
            mPivotYEdit = (EditText) rootView.findViewById(R.id.pivotYEditText);
            mSizeEdit = (EditText) rootView.findViewById(R.id.sizeEditText);

            SharedPreferences prefs = NFW.getSharedPreferences(getActivity());

            int pivotX = prefs.getInt(getString(R.string.key_small_screen_pivot_x),
                    getResources().getInteger(R.integer.default_small_screen_pivot_x));
            int pivotY = prefs.getInt(getString(R.string.key_small_screen_pivot_y),
                    getResources().getInteger(R.integer.default_small_screen_pivot_y));
            int size = prefs.getInt(getString(R.string.key_small_screen_size),
                    getResources().getInteger(R.integer.default_small_screen_size));

            mPivotXEdit.setText(Integer.toString(pivotX));
            mPivotYEdit.setText(Integer.toString(pivotY));
            mSizeEdit.setText(Integer.toString(size));

            return rootView;
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();

            NFW.getSharedPreferences(getActivity()).edit()
                    .putInt(getString(R.string.key_small_screen_pivot_x), parseText(mPivotXEdit))
                    .putInt(getString(R.string.key_small_screen_pivot_y), parseText(mPivotYEdit))
                    .putInt(getString(R.string.key_small_screen_size), parseText(mSizeEdit))
                    .apply();
        }

        private int parseText(EditText editText) {
            String text = editText.getText().toString();
            if (Strings.isNullOrEmpty(text)) {
                return 0;
            }
            return Integer.parseInt(text);
        }
    }
}
