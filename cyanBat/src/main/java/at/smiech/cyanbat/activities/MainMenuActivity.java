package at.smiech.cyanbat.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import at.smiech.cyanbat.R;

/**
 * MainActivity represents the menu and is used to navigate to other activities
 * It contains Buttons for the navigation
 *
 * @author Mart1n8891, KittysCode
 */
public class MainMenuActivity extends AppCompatActivity implements View.OnClickListener, MediaPlayer.OnCompletionListener {

    public static final String TAG = "DucklingsFlightGame";

    private SharedPreferences sharedPrefs;
    private MediaPlayer mediaPlayer;
    private boolean musicEnabled = true;
    private AlertDialog helpDialog;

    /**
     * Sets the contentView and initialises Buttons
     *
     * @param savedInstanceState State of activity - not considered at the moment
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /**
         * The following line is based on:
         * https://stackoverflow.com/questions/8929172/android-activity-background-image
         */
        getWindow().setBackgroundDrawableResource(R.drawable.menu_background);

        initButtons();
        initPreferences();
        initHelpDialog();
    }

    /**
     * Preparation of buttons with onClickListeners.
     */
    private void initButtons() {
        Button btnStartGame = findViewById(R.id.btnStartGame);
        btnStartGame.setOnClickListener(this);

        Button btnHighscore = findViewById(R.id.btnHighscore);
        btnHighscore.setOnClickListener(this);

        Button btnSettings = findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(this);

        Button btnHelp = findViewById(R.id.btnHelp);
        btnHelp.setOnClickListener(this);

        Button btnExit = findViewById(R.id.btnExit);
        btnExit.setOnClickListener(this);
    }

    /**
     * Initialization and preparation of preferences and settings.
     */
    private void initPreferences() {
        this.sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        this.musicEnabled = this.sharedPrefs.getBoolean("music_enabled", true);
    }

    /**
     * Initialization of music playback. MediaPlayer starts music if settings were made to do so.
     */
    private void initMusicPlayback() {
        if (musicEnabled) {
            AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (audioManager == null) {
                Log.w(TAG, "AudioManager unavailable! Hence music remains disabled");
                return;
            }
            int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0);

            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer.create(this, R.raw.main_theme);
            }
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.start();
            mediaPlayer.setLooping(true);
        }
    }

    /**
     * Stops music if media player exists.
     */
    private void stopMusic() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnStartGame:
                Intent startGameActivity = new Intent(this, CyanBatGameActivity.class);
                startActivity(startGameActivity);
                break;
            case R.id.btnHighscore:
                //Intent startScoreActivity = new Intent(this, ScoreActivity.class);
                //startActivity(startScoreActivity);
                break;
            case R.id.btnSettings:
                //Intent gameOptionsIntent = new Intent(this, GameOptionsActivity.class);
                //startActivity(gameOptionsIntent);
                break;
            case R.id.btnHelp:
                helpDialog.show();
                break;
            case R.id.btnExit:
                this.finish();
                break;
        }

    }

    /**
     * Is used for creating and initializing the Help Dialog
     */
    private void initHelpDialog() {
        // Use the Builder class for convenient dialog construction
        final EditText input = new EditText(this);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        input.setLayoutParams(lp);
        builder.setTitle(R.string.dialog_help_title);
        builder.setMessage(R.string.dialog_help_text);
        builder.setPositiveButton("OK", (dialog, id) -> helpDialog.dismiss());
        // Create the AlertDialog object and return it
        this.helpDialog = builder.create();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (musicEnabled) {
            mediaPlayer.start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    protected void onPause() {
        stopMusic();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        initMusicPlayback();
    }
}