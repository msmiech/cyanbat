## CyanBat privacy policy

Welcome to the CyanBat mobile game for Android!

This is an open source Android app developed by Martin Smiech. The source code is available on GitHub. The app is also available on the Google Play store.

I hereby state, to the best of my knowledge and belief, that I have not programmed this app to collect any personally identifiable information. All user data (i.e. the app preferences used for the high score) is stored on your device only, and can be simply erased by clearing the app's data or uninstalling it.

### Explanation of permissions requested in the app

The list of permissions required by the app can be found in the `AndroidManifest.xml` file:

https://github.com/msmiech/cyanbat/blob/master/cyanBat/src/main/AndroidManifest.xml

<br/>

|                Permission                 | Why it is required                                                                                                                               |
|:-----------------------------------------:|--------------------------------------------------------------------------------------------------------------------------------------------------|
|       `android.permission.VIBRATE`        | Required to vibrate the device when gameplay interactions happen. Permission automatically granted by the system; can't be revoked by user.      |
|      `android.permission.WAKE_LOCK`       | Required to prevent the screen from turning off when playing the game. Permission automatically granted by the system; can't be revoked by user. |

 <hr style="border:1px solid gray">
