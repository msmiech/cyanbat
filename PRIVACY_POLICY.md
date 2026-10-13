## CyanBat privacy policy

_Last updated: October 13, 2026_

Welcome to CyanBat!

CyanBat is an open source game, playable on Android, on iPhone and iPad, on the desktop (Windows,
macOS and Linux) and in the browser. The source code is available on GitHub, and the Android and
desktop builds are distributed as [GitHub releases](https://github.com/msmiech/cyanbat/releases). It is
no longer published on the Google Play Store.

### What data the game collects

To the best of my knowledge and belief, CyanBat does not collect, transmit or share any personally
identifiable information. The game has no accounts, no advertising, no analytics, no tracking and no
crash reporting, and it never connects to the internet (the Android app does not even request the
`INTERNET` permission; the browser version only downloads the game's own files from the site it is
served from). Touch, keyboard and controller input is used only to play the game and is never
recorded.

### What data the game stores on your device

The game keeps only the following, and only on your own device:

| Data                         | Purpose                                                                                           |
|------------------------------|---------------------------------------------------------------------------------------------------|
| The highscore of each stage  | Shown on the stage select and after each run, so you have something to beat.                      |
| Which stages are open        | Keeps a stage open once you have cleared the stage before it.                                     |
| Each stage's best clear      | Shown as stars on the stage select: the hardest difficulty you have cleared the stage on.          |
| Difficulty                   | Remembers the difficulty you last chose, to offer it again.                                       |
| Music and sound effects      | Remembers whether you turned the music or the sound effects off.                                  |
| Vibration                    | Remembers whether you turned vibration off.                                                       |
| Touch steering               | Remembers whether you fly the bat by dragging it or with the stick on the screen.                 |
| Display mode                 | Remembers whether the game is stretched to fill your screen or shown with black or ambient bars.  |
| Screen (desktop only)        | Remembers whether the game is played in a window, full screen or borderless.                      |
| Menu theme                   | Remembers whether the menu follows your system's theme or is always dark or always light.         |
| Language                     | Remembers the language you chose for the game, if you chose one rather than your system's.        |

Where it is kept, and how to erase it:

- **Android:** in the app's private storage. Clear the app's data or uninstall the app to erase it.
  If you have enabled your device's own backup (for example Android backup to your Google account),
  Android may include this data in that backup; this is handled by your device, not by the game. On
  Android 13 and later, the language is your system's language setting for the app.
- **iPhone and iPad:** in the app's own settings storage (user defaults), inside the app's
  container. Deleting the app erases it. If you back up your device to iCloud or a computer, the
  backup may include it; this is handled by your device, not by the game. The language is the one
  set for CyanBat in your device's Settings app.
- **Browser:** in the site's local storage in your browser, under keys starting with `cyanbat.`.
  Clearing the site's data in your browser erases it; in a private window it is gone when the
  window closes.
- **Desktop:** in the Java user preferences under the node `at/smiech/cyanbat`, which the game does
  not remove when you delete it. To erase it, delete:
    - **Windows:** the registry key `HKEY_CURRENT_USER\Software\JavaSoft\Prefs\at\smiech\cyanbat`
    - **macOS:** the `at/smiech/cyanbat` entry in
      `~/Library/Preferences/com.apple.java.util.prefs.plist`
    - **Linux:** the folder `~/.java/.userPrefs/at/smiech/cyanbat`

### Permissions

The desktop, iPhone, iPad and browser versions request no permissions. The list of permissions
required by the Android app can be found in its `AndroidManifest.xml` file:

https://github.com/msmiech/cyanbat/blob/main/app/src/main/AndroidManifest.xml

<br/>

|           Permission           | Why it is required                                                                                                                                                                                                                          |
|:------------------------------:|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|  `android.permission.VIBRATE`  | Required to vibrate the device, or the game controller you are playing with, briefly when the bat gets hit and when the game is over. You can turn vibration off under Settings. Granted automatically by the system; you cannot revoke it. |
| `android.permission.WAKE_LOCK` | Allows the game engine to keep the device awake while you play. The engine does not currently use it. Granted automatically by the system; you cannot revoke it.                                                                            |

### Changes to this policy

If this policy changes, the updated version will be published in this file in the GitHub repository,
with a new "Last updated" date.

 <hr style="border:1px solid gray">
