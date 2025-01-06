package at.smiech.cyanbat

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import at.smiech.cyanbat.activities.CreditsActivity
import at.smiech.cyanbat.activities.CyanBatGameActivity
import at.smiech.cyanbat.activities.GameSettingsActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal val PREFS_KEY_MUSIC = booleanPreferencesKey("music_enabled")
internal val Context.dataStore by preferencesDataStore(name = "cyanbat")

/**
 * MainActivity represents the menu and is used to navigate to other activities.
 * It contains buttons for the navigation.
 *
 * @author msmiech, KittysCode
 */
class MainMenuActivity : AppCompatActivity(), View.OnClickListener {

    private var mediaPlayer: MediaPlayer? = null
    private var isMediaPlayerReleased: Boolean = false
    private var helpDialog: AlertDialog? = null

    /**
     * Sets the contentView and initialises buttons.
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

        initMusicPlayback()
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
        lifecycleScope.launch {
            dataStore.data.map { it[PREFS_KEY_MUSIC] }.collectLatest {
                if (it == true) {
                    startMusic()
                } else {
                    stopMusic()
                }
            }
        }
    }

    /**
     * Initialization of music playback. MediaPlayer starts music if settings were made to do so.
     */
    private fun initMusicPlayback() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0)

        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, R.raw.menu_theme)
        }
    }

    private fun startMusic() {
        if (isMediaPlayerReleased) {
            mediaPlayer = MediaPlayer.create(this@MainMenuActivity, R.raw.menu_theme)
            isMediaPlayerReleased = false
        }
        mediaPlayer?.apply {
            start()
            isLooping = true
        }
    }

    /**
     * Stops music if media player exists.
     */
    private fun stopMusic() {
        mediaPlayer?.apply {
            stop()
            release()
            isMediaPlayerReleased = true
        }
    }


    override fun onClick(v: View) {
        when (v.id) {
            R.id.btnStartGame -> {
                stopMusic()
                val startGameActivity = Intent(this, CyanBatGameActivity::class.java)
                startActivity(startGameActivity)
            }

            R.id.btnSettings -> {
                val gameOptionsIntent = Intent(this, GameSettingsActivity::class.java)
                startActivity(gameOptionsIntent)
            }

            R.id.btnHelp -> helpDialog?.show()
            R.id.btnCredits -> {
                val startCreditsActivity = Intent(this, CreditsActivity::class.java)
                startActivity(startCreditsActivity)
            }

            R.id.btnExit -> this.finishAffinity()
        }

    }

    /**
     * Is used for creating and initializing the Help dialog.
     */
    private fun initHelpDialog() {
        // Use the Builder class for convenient dialog construction
        val input = EditText(this)
        val builder = AlertDialog.Builder(this)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        input.layoutParams = lp
        builder.setTitle(R.string.dialog_help_title)
        builder.setMessage(R.string.dialog_help_text)
        builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        // Create the AlertDialog object and return it
        this.helpDialog = builder.create()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.apply {
            stop()
            release()
        }
    }

    override fun onResume() {
        super.onResume()
        startMusic()
    }

    companion object {
        const val TAG = "CyanBatGame"
    }
}
