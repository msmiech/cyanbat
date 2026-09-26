## CyanBat privacy policy

_Last updated: September 26, 2026_

Welcome to CyanBat!

CyanBat is an open source game, playable on Android and on the desktop (Windows, macOS and Linux).
The source code is available on GitHub, and builds are distributed
as [GitHub releases](https://github.com/msmiech/cyanbat/releases). It is no longer published on the
Google Play Store.

### What data the game collects

To the best of my knowledge and belief, CyanBat does not collect, transmit or share any personally
identifiable information. The game has no accounts, no advertising, no analytics and no crash
reporting, and it never connects to the internet (the Android app does not even request the
`INTERNET` permission). Touch, keyboard and controller input is used only to play the game and is
never recorded.

### What data the game stores on your device

The game keeps only the following, and only on your own device:

| Data                        | Purpose                                                                                          |
|-----------------------------|--------------------------------------------------------------------------------------------------|
| The highscore of each stage | Shown on the stage select and after each run, so you have something to beat.                     |
| Which stages are unlocked   | Keeps a stage open once you have cleared the stage before it.                                    |
| Music on/off                | Remembers whether you turned the background music off.                                           |
| Sound effects on/off        | Remembers whether you turned the sound effects off.                                              |
| Display mode                | Remembers whether the game is stretched to fill your screen or shown with black or ambient bars. |

Where it is kept, and how to erase it:

- **Android:** in the app's private storage. Clear the app's data or uninstall the app to erase it.
  If you have enabled your device's own backup (for example Android backup to your Google account),
  Android may include this data in that backup; this is handled by your device, not by the game.
- **Desktop:** in the Java user preferences under the node `at/smiech/cyanbat`, which the game does
  not remove when you delete it. To erase it, delete:
    - **Windows:** the registry key `HKEY_CURRENT_USER\Software\JavaSoft\Prefs\at\smiech\cyanbat`
    - **macOS:** the `at/smiech/cyanbat` entry in
      `~/Library/Preferences/com.apple.java.util.prefs.plist`
    - **Linux:** the folder `~/.java/.userPrefs/at/smiech/cyanbat`

### Explanation of permissions requested in the Android app

The desktop version requests no permissions. The list of permissions required by the Android app can
be found in its `AndroidManifest.xml` file:

https://github.com/msmiech/cyanbat/blob/main/app/src/main/AndroidManifest.xml

<br/>

|           Permission           | Why it is required                                                                                                                                               |
|:------------------------------:|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|  `android.permission.VIBRATE`  | Required to vibrate the device briefly when the bat gets hit and when the game is over. Granted automatically by the system; you cannot revoke it.               |
| `android.permission.WAKE_LOCK` | Allows the game engine to keep the device awake while you play. The engine does not currently use it. Granted automatically by the system; you cannot revoke it. |

### Changes to this policy

If this policy changes, the updated version will be published in this file in the GitHub repository,
with a new "Last updated" date.

 <hr style="border:1px solid gray">
 