# Releasing on F-Droid

F-Droid does not take an APK from us. It builds the app itself, from the source at a release's tag,
on its own build server, and signs it with its own key. What it needs from this repository:

- **A free license.** The whole repository is GPL-3.0-or-later, the art, music and sound included
  (`LICENSE`).
- **Nothing it cannot build from source.** The Android app's dependencies are all AndroidX, Kotlin
  and JetBrains Compose libraries from Google's Maven and Maven Central. There is no Google Play
  Services, Firebase, analytics or crash reporting, and the app has no `INTERNET` permission. The
  only binary in the repository is the Gradle wrapper's jar, which F-Droid knows.
- **No dependency metadata in the APK.** AGP would otherwise put the dependency list into the APK's
  signing block, encrypted with a key only Google Play can read; `app/build.gradle.kts` turns that
  off (`dependenciesInfo`).
- **The version in the tagged source.** F-Droid passes the build nothing, so a release's
  `cyanbat.version`, and the `cyanbat.versionCode` it comes to, are stamped in `gradle.properties`
  before it is tagged. The release workflow refuses a tag they do not match; see the README,
  Cutting a release.
- **The store listing**, in `fastlane/metadata/android`: title, short and full descriptions in
  English, German and Polish, a changelog per versionCode in each, and in `en-US/images` the icon
  (written by `tools/generate_icons.py`) and phone screenshots, which F-Droid shows for every
  language.

## Submitting it

The app gets into F-Droid through a merge request to
[fdroiddata](https://gitlab.com/fdroid/fdroiddata), which needs a GitLab account. The first build is
the first release made after all of the above is in: 3.7.

1. Tag 3.7 here, as for any release.
2. Fork fdroiddata on GitLab, and on a new branch add `metadata/at.smiech.cyanbat.yml`:

   ```yaml
   Categories:
     - Games
   License: GPL-3.0-or-later
   AuthorName: Martin Śmiech
   SourceCode: https://github.com/msmiech/cyanbat
   IssueTracker: https://github.com/msmiech/cyanbat/issues
   Changelog: https://github.com/msmiech/cyanbat/releases

   AutoName: CyanBat

   RepoType: git
   Repo: https://github.com/msmiech/cyanbat.git

   Builds:
     - versionName: '3.7'
       versionCode: 30700
       commit: '3.7'
       subdir: app
       sudo:
         - apt-get update
         - apt-get install -y openjdk-25-jdk-headless
       gradle:
         - yes
       gradleprops:
         - org.gradle.java.installations.auto-download=false

   AutoUpdateMode: Version
   UpdateCheckMode: Tags ^\d+\.\d+(\.\d+)?$
   UpdateCheckData: gradle.properties|cyanbat\.versionCode=(\d+)|.|cyanbat\.version=(\S+)
   CurrentVersion: '3.7'
   CurrentVersionCode: 30700
   ```

3. If you have `fdroidserver` (on Linux, `pip install fdroidserver`), check it from the fdroiddata
   checkout with `fdroid lint at.smiech.cyanbat` and `fdroid rewritemeta at.smiech.cyanbat`, and
   try the build with `fdroid build -v -l at.smiech.cyanbat`. Otherwise the merge request's CI runs
   the same.
4. Open the merge request with fdroiddata's "App inclusion" template, and answer its checklist.
   A reviewer may ask for changes to the recipe; the build is theirs to run.

What the recipe does, and why:

- **JDK 25.** The build compiles with a Java 25 toolchain and runs Gradle's daemon on it
  (`gradle/gradle-daemon-jvm.properties`). F-Droid's build server runs Debian 13, whose own
  `openjdk-25-jdk-headless` Gradle finds by itself. Turning off auto-download keeps Gradle from
  fetching a JDK from foojay if it did not.
- **Only the Android app.** `subdir: app` with `gradle: yes` runs `assembleRelease` in `:app`, which
  builds `:engine` and `:game` for Android and nothing for the desktop, the browser or iOS: no
  Kotlin/Native, Node.js or Binaryen download.
- **Updates by tag.** F-Droid's update check looks at the newest tags that are a bare version (not a
  pre-release), and reads each one's `gradle.properties`: it cannot work a versionCode out of a
  version, which is why `cyanbat.versionCode` is written out. A new tag then gets a new build by
  itself, within a few days.

## Signing

F-Droid signs with its own key, so the F-Droid build and the GitHub release's APK cannot update one
another: a player switching between them has to uninstall first, which loses their highscores and
unlocks. F-Droid can instead ship the APK signed with our key, if its build of a tag comes out byte
for byte the same as the GitHub release's (a reproducible build: `Binaries:` and
`AllowedAPKSigningKeys:` in the recipe). That needs the release workflow's build and F-Droid's to
agree to the byte - the JDK, the build paths, R8 - and is worth trying once the app is in.

## After it is in

- Each release is stamped, changelogs included, and tagged as the README describes; nothing more is
  needed for F-Droid.
- Add the F-Droid badge to the README, and F-Droid to the privacy policy's list of where the game
  is distributed.
- Screenshots are taken on a phone (or the emulator, with the status bar in demo mode) and live in
  `fastlane/metadata/android/en-US/images/phoneScreenshots`; replace them when the game looks
  different.
