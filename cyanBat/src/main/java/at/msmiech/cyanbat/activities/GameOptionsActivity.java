package at.msmiech.cyanbat.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import at.msmiech.cyanbat.fragments.GameOptionFragment;

/**
 * GameOptionsActivity is a container for its GameOptionFragment.
 *
 * @see GameOptionFragment
 * @author Martin Smiech
 */
public class GameOptionsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Adding our settings fragment
        getFragmentManager().beginTransaction().replace(android.R.id.content, new GameOptionFragment()).commit();
    }
}
