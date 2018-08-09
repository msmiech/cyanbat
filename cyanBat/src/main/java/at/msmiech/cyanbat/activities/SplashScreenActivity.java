package at.msmiech.cyanbat.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import at.msmiech.cyanbat.R;

/**
 * Is used for representing and animating the Splash Screen
 * Reference: Lecture
 *
 * @author kittysCode
 */
public class SplashScreenActivity extends AppCompatActivity implements View.OnClickListener {

    private Animation titleAnimation;
    private ImageView titleImage;
    private RelativeLayout layout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        getWindow().setBackgroundDrawableResource(R.drawable.menu_background);

        this.layout = findViewById(R.id.splash_screen_layout);
        layout.setOnClickListener(this);

        titleAnimation = AnimationUtils.loadAnimation(this, R.anim.view_animation);
        titleImage = findViewById(R.id.game_title_view);
        startViewAnimation(titleImage);
    }

    /**
     * Start an animation using titleAnimation
     *
     * @param view The View that should be animated
     */
    public void startViewAnimation(View view) {
        titleAnimation.reset();
        titleImage.clearAnimation();
        titleImage.startAnimation(titleAnimation);
    }

    @Override
    public void onClick(View v) {
        Intent startMainActivity = new Intent(this, MainMenuActivity.class);
        startActivity(startMainActivity);
    }
}