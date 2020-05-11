package at.smiech.cyanbat.fragments

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

import at.smiech.cyanbat.R

/**
 * This fragment displays the game options based on Preferences to the user. The user can view and change preferences here.
 *
 * @author msmiech
 */
class GameOptionFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        /**
         * Using xml resources to describe the layout of this fragment/PreferenceScreens.
         */
        setPreferencesFromResource(R.xml.gameoptions, rootKey)
    }
}
