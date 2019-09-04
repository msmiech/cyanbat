package at.smiech.cyanbat.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.RelativeLayout

import at.smiech.cyanbat.R

/**
 * Is used for representing and animating the Splash Screen
 * Reference: Lecture
 *
 * @author kittysCode
 */
class SplashScreenActivity : AppCompatActivity(), View.OnClickListener {

    private var titleAnimation: Animation? = null
    private var titleImage: ImageView? = null
    private var layout: RelativeLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        window.setBackgroundDrawableResource(R.drawable.menu_background)

        this.layout = findViewById(R.id.splash_screen_layout)
        layout!!.setOnClickListener(this)

        titleAnimation = AnimationUtils.loadAnimation(this, R.anim.view_animation)
        titleImage = findViewById(R.id.game_title_view)
        startViewAnimation(titleImage)
    }

    /**
     * Start an animation using titleAnimation
     *
     * @param view The View that should be animated
     */
    fun startViewAnimation(view: View?) {
        titleAnimation!!.reset()
        titleImage!!.clearAnimation()
        titleImage!!.startAnimation(titleAnimation)
    }

    override fun onClick(v: View) {
        val startMainActivity = Intent(this, MainMenuActivity::class.java)
        startActivity(startMainActivity)
    }
}