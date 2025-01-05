package at.smiech.cyanbat.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import at.smiech.cyanbat.screen.CreditsScreen

class CreditsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CreditsScreen()
        }
    }
}
