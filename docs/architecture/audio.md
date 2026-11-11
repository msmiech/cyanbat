# Music and sound

Part of the notes for coding agents; [AGENTS.md](../../AGENTS.md) has the rules that hold everywhere
and the list of these files.

## Layered music

**Layered music.** A stage's music is one piece cut into eight stems, one per `MusicLayer` (bed,
pulse, drive, lead, boom, roll, chop, fury), mixed live so the music builds and strips back with the
action. Past the tune the layers are a trap beat growing under the stage's own instruments: 808s
(boom), rolling hi-hats (roll) and chopped voices (chop).

- `MusicDirector` (`:game`) decides what plays. The wave raises a floor and the combo builds on it,
  compared against each layer's threshold. The combo counts in rungs of the HUD's heat ladder
  (`ComboHeat.rung`), not steps of the multiplier, every rung up to SUPERNOVA, so a new title on the
  readout and a new layer land together. Under the tune the thresholds are two rungs apart, over it
  one; fury is the boss's, or a SUPERNOVA streak's. A hit is a thud (a muffle and a dip), and the
  streak it cost winds the music down a layer a beat, top first, rather than all at once. The
  level-up dialog holds the music under a muffle instead of pausing it. The boss and each boss phase
  get a break - the kit, the 808s and fury out, the bass, hats, tune and voices driving on, the
  phase's shorter - and a slam on the downbeat; a fight never falls back to its bed, which sounded
  like the fight ending at every phase. `GameScreen` feeds it at the very top of `update`, ahead of
  every early return, because the music carries on under the level-up dialog. Pause pauses it; the
  boss's death stops it dead and hands over to the victory's fanfare, and the bat's death to the
  game over's track.
- `StemMixer` (`:engine`, common) decodes the stems as it mixes and lands every change on the
  music's grid (`MusicGrid`, `Quantum`): a layer coming in is all the way up on the beat, one going
  out plays out its beat first. It also runs the muffle, a swept low-pass.
  `DesktopLayeredMusic` and `AndroidLayeredMusic` are only threads pulling frames from it into a
  `SourceDataLine` or an `AudioTrack`. `WebLayeredMusic` is a timer on the page's one thread,
  scheduling chunks on the `AudioContext`'s clock back to back, a little ahead of it. The context
  runs at the assets' 22.05 kHz, so nothing is resampled at the chunks' joins.
- The desktop's sound outlives a change of output device, as when a DualSense connects and Windows
  makes its speaker the default. `DesktopLayeredMusic` waits on its line's room rather than in its
  blocking `write`, takes a line that has taken nothing for a second for lost, and asks for another,
  every second until one comes, carrying on where the music had got to; `DesktopSound` lets go of a
  clip that fails and opens another on a later play. Nothing a device does throws into the game's
  thread. A failure also drops the JDK's list of DirectSound devices (`SoundDevices`, which the
  launcher's `--add-opens java.desktop/com.sun.media.sound` allows): the JDK reads each device's
  formats once, and one read while a device was arriving kept none, failing every line after.
- The menu's, the game over's and the victory's music go through the same mixer: `Audio.newMusic`
  is one stem at full level (`TrackMusic`), looping or played once. A track that does not loop stops
  at its end (`StemMixer.isLooping`, `hasEnded`) and starts from the top when played again.
- Two threads touch the mixer, and there are no locks, which common code could not take anyway. The
  game thread only calls the setters, which swap in immutable orders; the audio thread owns the
  rest. Every stem is decoded every chunk, heard or not: a stem's place in the music is how far it
  has been read.
- `GameScreen` opens its stage's music the first time it plays it and disposes it with itself, so a
  run with music off never reads the stems and the next stage's run does not play over this one.
- A stage's boss can be fought to a piece of its own (`StageMusic.boss`; the lagoon's Naga): as it
  arrives the stage's music is disposed and the boss's opened, with a director of its own, which
  opens it in the entrance's break, from its first note, and slams everything in on its second bar
  (`MusicDirector`'s `bossArriving`). A second boss is fought to the same piece, disposed as the
  first falls and opened again from the top as the second's fight begins. Every layer is up for the
  whole fight, so its stems are there for the drops at its phases. `MusicStemTest` holds every piece
  to its grid. The fanfare is the same (`GameAssets.VICTORY_MUSIC`): each won run opens its own,
  because a track paused partway resumes from there, and a fanfare has to start from the top.
- The grid is declared twice, in the stage's generator and in its `StageMusic` in `GameAssets`.
  `MusicStemTest` holds them together: every stem whole bars at the declared tempo, and every stem's
  length dividing the longest, so the layers stay in step however long the run goes. It reads every
  stage's whole `playlist`, the endless stage's remixes included.
