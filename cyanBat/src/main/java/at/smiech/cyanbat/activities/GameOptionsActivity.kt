package at.smiech.cyanbat.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

import at.smiech.cyanbat.fragments.GameOptionFragment

/**
 * GameOptionsActivity is a container for its GameOptionFragment.
 *
 * @see GameOptionFragment
 *
 * @author msmiech
 */
class GameOptionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //Adding our settings fragment
        supportFragmentManager.beginTransaction().replace(android.R.id.content, GameOptionFragment()).commit()
    }
}
