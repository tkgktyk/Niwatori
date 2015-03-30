package jp.tkgktyk.xposed.niwatori.app;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.google.common.base.Strings;

import jp.tkgktyk.xposed.niwatori.InitialPosition;
import jp.tkgktyk.xposed.niwatori.R;

/**
 * Created by tkgktyk on 2015/02/13.
 */
public class InitialPositionSimpleActivity extends Activity {
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
        private EditText mInitXEdit;
        private EditText mInitYEdit;

        private InitialPosition mInitialPosition;

        public SimpleSettingsFragment() {
        }

        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_initial_position_simple, container, false);

            mInitXEdit = (EditText) rootView.findViewById(R.id.initXEditText);
            mInitYEdit = (EditText) rootView.findViewById(R.id.initYEditText);

            mInitialPosition = new InitialPosition(getActivity());

            mInitXEdit.setText(Integer.toString(mInitialPosition.getXp()));
            mInitYEdit.setText(Integer.toString(mInitialPosition.getYp()));

            return rootView;
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();

            mInitialPosition.setXp(parseText(mInitXEdit));
            mInitialPosition.setYp(parseText(mInitYEdit));
            mInitialPosition.save(getActivity());
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
