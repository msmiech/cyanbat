package at.smiech.cyanbat.fragments;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import at.smiech.cyanbat.R;

/**
 * This fragment displays the game options based on Preferences to the user. The user can view and change preferences here.
 *
 * @author Martin Smiech
 */
public class GameOptionFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /**
         * Using xml resources to describe the layout of this fragment/PreferenceScreens.
         */
        this.addPreferencesFromResource(R.xml.gameoptions);
    }
}
