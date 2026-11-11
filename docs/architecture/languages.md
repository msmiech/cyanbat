# Languages

Part of the notes for coding agents; [AGENTS.md](../../AGENTS.md) has the rules that hold everywhere
and the list of these files.

## Languages

**Languages.** The game speaks English, German and Polish: the one the player picks in Settings
(`AppLanguage`), or until they pick one the system's.

- Every word the game shows, the menu's and the run's, is a Compose resource:
  `values/strings.xml`, and `values-de` and `values-pl` beside it, under
  `game/src/commonMain/composeResources`. Compose falls back on English for a string a language
  lacks, so nothing on screen shows a gap; `TranslationsTest` does, and holds every translation to
  the English's `%1$d` blanks.
- The menu reads them with `stringResource`. A run cannot, since it draws on the game loop: the host
  reads every string once into a `GameText` (`GameText.load`, blocking, as Compose's own lookups are
  on Android and the desktop) and hands it over in the `CyanBatEnvironment`. The game's logic names
  its words by `StringResource` - `PowerUp.title`, `Stage.name`, `ComboHeat.title` - and never holds
  a string, so only the drawing knows the language. `GameText.format` fills `%1$d` and `%1$s` as
  Compose does, and a bare `%` is a percent sign.
- Compose's resources read the language off the JVM's default locale, so picking one is setting
  that. On the desktop `DesktopLocale` sets it. On Android 13+ the setting is the system's per-app
  language (`AppLocale`, through `LocaleManager`), so Settings and the system's app languages are
  one setting; before 13, `AppLocale` keeps the choice in DataStore and sets the default itself.
  Android sets the default back to the system's at every configuration change, so both activities
  call
  `AppLocale.reapply()` in `onConfigurationChanged`, before anything reads a string.
- In a browser Compose reads the language off `navigator.language` and `navigator.languages`, which
  a page cannot set. `index.html` puts a getter in front of each that answers with
  `window.cyanbatLanguage` when it is set, and `WebLocale` sets it.
- On iOS Compose reads it off `NSLocale.preferredLanguages`, which puts the app's own
  `AppleLanguages` first. That is where the system's Settings app keeps an app's language, so
  `IosLocale` reads and writes it there, and the two settings are one, as on Android 13+. The app
  lists its languages in `CFBundleLocalizations` and the `*.lproj` folders in `iosApp/`; a new
  language goes there too.
- The menu redraws itself in a new language, under `key(language)` in `CyanBatMenu`: Compose's
  resources pick the language when a string is first read, and nothing reads it again by itself. A
  host puts a language into effect before `SettingsRepository.language` tells the menu of it.
  `MainActivity` takes `locale`, `layoutDirection` and `screenLayout` in place for this, and the
  game activity reads the run's text again in `onConfigurationChanged`; recreated, either lost where
  the player was.
- The run's lines have fixed sizes and fixed room, laid out for English. `GameTextLayoutTest`
  measures every language's against them in the platform's face. DejaVu Sans, on CI's Linux, is the
  widest, so a line that passes on Windows can still fail there.
- A boss's banners are whole lines rather than its name with a word put after it, because in Polish
  the word agrees with the name.
- A stage is an *Abschnitt* and an *etap*, the bat's level a *Stufe* and a *poziom*, which keeps the
  two apart as they are in English.
- Tests run in English, because the root build pins their JVMs' locale; so does the recorder, whose
  reel is the README's and whose `Montage` finds a wave's banner by its English word.
- GAME OVER is drawn into the game over artwork and stays English in every language. The line under
  it is text the screen draws (`game_over_hint`), on the baseline and at the width the artwork had
  it; `tools/generate_game_over.py` takes the artwork's own English line out of it.
- A new language is a `values-xx/strings.xml` in composeResources, which the tests find by
  themselves; an `AppLanguage` with its name in its own words for Settings, which `TranslationsTest`
  holds to the folders; and a `values-xx` in `app/src/main/res` for the game screen's label: AGP
  builds the app's language list for the per-app setting from those (`generateLocaleConfig`) and
  does not see composeResources.
