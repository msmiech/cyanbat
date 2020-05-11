package at.smiech.cyanbat.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import androidx.preference.PreferenceManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout

import at.smiech.cyanbat.R

/**
 * MainActivity represents the menu and is used to navigate to other activities
 * It contains Buttons for the navigation
 *
 * @author msmiech, KittysCode
 */
class MainMenuActivity : AppCompatActivity(), View.OnClickListener, MediaPlayer.OnCompletionListener {

    private var sharedPrefs: SharedPreferences? = null
    private var mediaPlayer: MediaPlayer? = null
    private var musicEnabled = true
    private var helpDialog: AlertDialog? = null

    /**
     * Sets the contentView and initialises Buttons
     *
     * @param savedInstanceState State of activity - not considered at the moment
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /**
         * The following line is based on:
         * https://stackoverflow.com/questions/8929172/android-activity-background-image
         */
        window.setBackgroundDrawableResource(R.drawable.menu_background)

        initButtons()
        initPreferences()
        initHelpDialog()
    }

    /**
     * Preparation of buttons with onClickListeners.
     */
    private fun initButtons() {
        val btnStartGame = findViewById<Button>(R.id.btnStartGame)
        btnStartGame.setOnClickListener(this)

        /*Button btnHighscore = findViewById(R.id.btnHighscore);
        btnHighscore.setOnClickListener(this); */

        val btnSettings = findViewById<Button>(R.id.btnSettings)
        btnSettings.setOnClickListener(this)

        val btnHelp = findViewById<Button>(R.id.btnHelp)
        btnHelp.setOnClickListener(this)

        val btnCredits = findViewById<Button>(R.id.btnCredits)
        btnCredits.setOnClickListener(this)

        val btnExit = findViewById<Button>(R.id.btnExit)
        btnExit.setOnClickListener(this)
    }

    /**
     * Initialization and preparation of preferences and settings.
     */
    private fun initPreferences() {
        this.sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        this.musicEnabled = this.sharedPrefs!!.getBoolean("music_enabled", true)
    }

    /**
     * Initialization of music playback. MediaPlayer starts music if settings were made to do so.
     */
    private fun initMusicPlayback() {
        musicEnabled = this.sharedPrefs!!.getBoolean("music_enabled", true)
        if (musicEnabled) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0)

            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer.create(this, R.raw.menu_theme)
            }
            mediaPlayer!!.setOnCompletionListener(this)
            mediaPlayer!!.start()
            mediaPlayer!!.isLooping = true
        }
    }

    /**
     * Stops music if media player exists.
     */
    private fun stopMusic() {
        if (mediaPlayer != null) {
            mediaPlayer!!.stop()
            mediaPlayer!!.release()
            mediaPlayer = null
        }
    }


    override fun onClick(v: View) {
        when (v.id) {
            R.id.btnStartGame -> {
                val startGameActivity = Intent(this, CyanBatGameActivity::class.java)
                startActivity(startGameActivity)
            }
            /*case R.id.btnHighscore:
                //Intent startScoreActivity = new Intent(this, ScoreActivity.class);
                //startActivity(startScoreActivity);
                break;*/
            R.id.btnSettings -> {
                val gameOptionsIntent = Intent(this, GameOptionsActivity::class.java)
                startActivity(gameOptionsIntent)
            }
            R.id.btnHelp -> helpDialog!!.show()
            R.id.btnCredits -> {
                val startCreditsActivity = Intent(this, CreditsActivity::class.java)
                startActivity(startCreditsActivity)
            }
            R.id.btnExit -> this.finishAffinity()
        }

    }

    /**
     * Is used for creating and initializing the Help Dialog
     */
    private fun initHelpDialog() {
        // Use the Builder class for convenient dialog construction
        val input = EditText(this)
        val builder = AlertDialog.Builder(this)
        val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT)
        input.layoutParams = lp
        builder.setTitle(R.string.dialog_help_title)
        builder.setMessage(R.string.dialog_help_text)
        builder.setPositiveButton("OK") { dialog, id -> helpDialog!!.dismiss() }
        // Create the AlertDialog object and return it
        this.helpDialog = builder.create()
    }

    override fun onCompletion(mp: MediaPlayer) {
        if (musicEnabled) {
            mediaPlayer!!.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mediaPlayer != null) {
            mediaPlayer!!.stop()
            mediaPlayer!!.release()
            mediaPlayer = null
        }
    }

    override fun onPause() {
        stopMusic()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        initMusicPlayback()
    }

    companion object {

        val TAG = "DucklingsFlightGame"
    }
}