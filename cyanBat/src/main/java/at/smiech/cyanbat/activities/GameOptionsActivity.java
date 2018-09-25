package at.smiech.cyanbat.activities;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import at.smiech.cyanbat.fragments.GameOptionFragment;

/**
 * GameOptionsActivity is a container for its GameOptionFragment.
 *
 * @see GameOptionFragment
 * @author mart1n8891
 */
public class GameOptionsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Adding our settings fragment
        //getSupportFragmentManager()
        getFragmentManager().beginTransaction().replace(android.R.id.content, new GameOptionFragment()).commit();
    }
}
