package jp.tkgktyk.xposed.niwatori.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import jp.tkgktyk.flyinglayout.FlyingLayout;
import jp.tkgktyk.xposed.niwatori.InitialPosition;
import jp.tkgktyk.xposed.niwatori.NFW;
import jp.tkgktyk.xposed.niwatori.R;

/**
 * Created by tkgktyk on 2015/02/13.
 */
public class InitialPositionActivity extends Activity {

    private FlyingLayout mFlyingLayout;
    private FrameLayout mContainer;

    private InitialPosition mInitialPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_initial_position);

        mInitialPosition = new InitialPosition(this);

        mFlyingLayout = (FlyingLayout) findViewById(R.id.flying);
        mContainer = (FrameLayout) findViewById(R.id.container);

        final SharedPreferences pref = NFW.getSharedPreferences(this);

        final int width = Math.round(getResources().getDimension(R.dimen.boundary_width));
        final int color = Color.parseColor(pref.getString(
                getString(R.string.key_boundary_color),
                getString(R.string.default_boundary_color)));
        final GradientDrawable drawable = NFW.makeBoundaryDrawable(width, color);
        mContainer.setForeground(drawable);

        final FlyingLayout.Helper helper = mFlyingLayout.getHelper();
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
