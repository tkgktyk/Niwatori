package jp.tkgktyk.xposed.niwatori.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
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
        SharedPreferences pref = NFW.getSharedPreferences(this);
        mFlyingLayout.setSpeed(Float.parseFloat(pref.getString(
                getString(R.string.key_speed), Float.toString(FlyingLayout.DEFAULT_SPEED))));
        mFlyingLayout.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @SuppressWarnings("deprecation")
                    @SuppressLint("NewApi")
                    @Override
                    public void onGlobalLayout() {
                        mFlyingLayout.setOffsetX(mInitialPosition
                                .getX(mFlyingLayout));
                        mFlyingLayout.setOffsetY(mInitialPosition
                                .getY(mFlyingLayout));
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            mFlyingLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        } else {
                            mFlyingLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        }
                    }
                });
        mFlyingLayout.setOnFlyingEventListener(new FlyingLayout.SimpleOnFlyingEventListener() {
            @Override
            public void onDragFinished(FlyingLayout v) {
                mInitialPosition.setXp(v, v.getOffsetX());
                mInitialPosition.setYp(v, v.getOffsetY());
                mInitialPosition.save(v.getContext());
                v.setOffsetX(mInitialPosition.getX(v));
                v.setOffsetY(mInitialPosition.getY(v));
                v.requestLayout();
            }
        });
    }
}
