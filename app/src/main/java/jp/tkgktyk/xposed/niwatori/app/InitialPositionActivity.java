package jp.tkgktyk.xposed.niwatori.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import jp.tkgktyk.flyinglayout.FlyingLayout;
import jp.tkgktyk.xposed.niwatori.InitialPosition;
import jp.tkgktyk.xposed.niwatori.NFW;
import jp.tkgktyk.xposed.niwatori.R;

/**
 * Created by tkgktyk on 2015/02/13.
 */
public class InitialPositionActivity extends Activity {

    private FlyingLayout mFlyingLayout;

    private InitialPosition mInitialPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_initial_position);

        mInitialPosition = new InitialPosition(this);

        mFlyingLayout = (FlyingLayout) findViewById(R.id.flying);
        final FlyingLayout.Helper helper = mFlyingLayout.getHelper();
        SharedPreferences pref = NFW.getSharedPreferences(this);
        helper.setSpeed(Float.parseFloat(pref.getString(
                getString(R.string.key_speed), Float.toString(FlyingLayout.DEFAULT_SPEED))));
        mFlyingLayout.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @SuppressWarnings("deprecation")
                    @SuppressLint("NewApi")
                    @Override
                    public void onGlobalLayout() {
                        helper.setOffsetX(mInitialPosition.getX(mFlyingLayout));
                        helper.setOffsetY(mInitialPosition.getY(mFlyingLayout));
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            mFlyingLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        } else {
                            mFlyingLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        }
                    }
                });
        helper.setOnFlyingEventListener(new FlyingLayout.SimpleOnFlyingEventListener() {
            @Override
            public void onDragFinished(ViewGroup v) {
                mInitialPosition.setXp(mFlyingLayout, helper.getOffsetX());
                mInitialPosition.setYp(mFlyingLayout, helper.getOffsetY());
                mInitialPosition.save(mFlyingLayout.getContext());
                helper.setOffsetX(mInitialPosition.getX(mFlyingLayout));
                helper.setOffsetY(mInitialPosition.getY(mFlyingLayout));
                mFlyingLayout.requestLayout();
            }
        });
    }
}
