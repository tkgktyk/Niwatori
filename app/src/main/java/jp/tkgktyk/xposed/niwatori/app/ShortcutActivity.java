package jp.tkgktyk.xposed.niwatori.app;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import jp.tkgktyk.xposed.niwatori.NFW;
import jp.tkgktyk.xposed.niwatori.R;

/**
 * Created by tkgktyk on 2015/02/13.
 */
public class ShortcutActivity extends ListActivity {

    private static final String mActionList[] = {
            NFW.ACTION_TOGGLE,
            NFW.ACTION_PIN,
            NFW.ACTION_PIN_OR_RESET,
            NFW.ACTION_SMALL_SCREEN_LEFT,
            NFW.ACTION_SMALL_SCREEN_RIGHT,
    };
    private static final int mShortcutNameIdList[] = {
            R.string.action_toggle,
            R.string.action_pin,
            R.string.action_pin_or_reset,
            R.string.action_small_screen_left,
            R.string.action_small_screen_right,
    };
    private List<String> mShortcutNameList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mShortcutNameList = new ArrayList<>(mShortcutNameIdList.length);
        for (int id : mShortcutNameIdList) {
            mShortcutNameList.add(getString(id));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, mShortcutNameList);
        setListAdapter(adapter);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // create shortcut
        Intent shortcut = new Intent(this, ActionActivity.class);
        shortcut.setAction(mActionList[position]);

        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcut);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, mShortcutNameList.get(position));
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(this, R.drawable.ic_launcher));

        setResult(RESULT_OK, intent);
        finish();
    }
}
