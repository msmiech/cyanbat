package at.smiech.cyanbat.fragments

import android.os.Bundle
import android.preference.PreferenceFragment

import at.smiech.cyanbat.R

/**
 * This fragment displays the game options based on Preferences to the user. The user can view and change preferences here.
 *
 * @author msmiech
 */
class GameOptionFragment : PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /**
         * Using xml resources to describe the layout of this fragment/PreferenceScreens.
         */
        this.addPreferencesFromResource(R.xml.gameoptions)
    }
}
