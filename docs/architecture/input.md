# Input

Part of the notes for coding agents; [AGENTS.md](../../AGENTS.md) has the rules that hold everywhere
and the list of these files.

## Input

- `PointerTouchHandler` handles touch. On desktop, mouse motion counts as a drag.
- A finger flies the bat one of two ways, the player's `TouchSteering`: dragging it, or a stick
  (`VirtualStick`, which `PlayerInputSystem` reads as a pad's stick). The stick is not there until a
  thumb is: wherever the first finger goes down becomes its center, `GameScreen.drawStick` draws it
  in the HUD only while it is held, and lifting the thumb takes it away. Its radius, which the thumb
  travels most of for full speed, is the player's `StickSensitivity`. With the stick on, nothing
  drags the bat, and keys and pads still beat it. The stick reads its thumb's place off
  `Input.getTouchX` rather than the events, which an overlay may have read meanwhile. A run reads
  both settings every update, through `SteeringSettings` in its environment, as it reads the
  sound's.
- Where there is a touch screen (`hasTouchScreen`: Android, iOS, and a browser that reports touch
  points), Settings offers the stick as a switch, with its sensitivity under it while it is on, and
  a player who has not chosen (`touchSteering` is null) is asked in a `SteeringDialog` after the
  difficulty, before their first run. A switch rather than two segments: a row of two short words
  sits so far right that the cursor going down skips it. The sensitivity is segments rather than a
  slider, because `MenuKeys` takes the arrows ahead of every control. The dialog's pictures are
  `tools/generate_steering_previews.py`'s, which draws the stick as the game does: keep it in step
  with `StickSensitivity.DEFAULT` and `STICK_*` in `CyanBatConstants`.
- `ControlHandler` turns keyboard and gamepad into the `Controls` intent: two axes plus
  edge-triggered `GameButton`s. `ComposeKeyAdapter` (desktop) and `AndroidKeyAdapter` (keyboard and
  pads) feed it, and on the desktop `PadRouter`.
- A pad's bottom face button (A, Cross) is Confirm, its right one (B, Circle) Back, Start Pause, and
  the d-pad wins over the left stick. Confirm resumes the pause screen, and the level up has a
  cursor (`offerCursor`), moved by a lean of `moveX` and taken by Confirm; the number keys still
  pick by position.
- The desktop reads pads through SDL 3, by way of LWJGL (`DesktopGamepads`), polled on the window's
  thread every few milliseconds and heard only while the window has focus, since SDL reads a pad
  whatever window is in front. SDL 3 rather than Jamepad's SDL 2, whose Windows build has no HIDAPI
  drivers, without which a DualSense cannot rumble. The build takes LWJGL's natives for the system
  it runs on (`lwjglNatives`), as Compose's `currentOs` does Skia's, and LWJGL is told to reach
  native memory through FFM rather than `sun.misc.Unsafe`, which Java 25 warns of. Every connected
  pad counts, merged. `PadRouter` turns each poll into the run's controls, or in the menu, which
  reads nothing but keys, into the keys a keyboard would press: arrows that repeat while held, Enter
  and Escape, dispatched to the window as AWT events (`PadKeyEvent`). Whatever is held as the run
  and the menu hand over stays with the side it was pressed on. The browser does not read pads yet,
  and iOS has not been given them.
- The prompts follow what the player last played with, `Controls.inputMethod`: touch, keyboard and
  mouse, an Xbox pad or a PlayStation pad, told apart by maker or name (`InputMethod.padOf`). Every
  adapter reports what it was fed (`ControlHandler.use`; a mouse or a touch through
  `onComposePointerUse`), and it is snapshot state, so the menu's help follows it as the run's
  overlays do. Touch keeps the overlays' sentences; the others draw the buttons beside short words
  (`ButtonPrompts`): key caps and a mouse, an Xbox pad's colored letters, a PlayStation pad's
  shapes, as pixel art, since not every font has the shapes. On Android the menu hands its method to
  the run (`EXTRA_INPUT_METHOD`), so a run started with a pad prompts for it from the start.
- Android's Back arrives as `GameButton.BACK` from the back-pressed dispatcher, because gesture
  navigation raises no key event.
- iOS has no Back, so a run there draws a pause button at the top right (`TouchButton`, on where the
  host sets `CyanBatEnvironment.pauseButton`), and the pause screen a Quit button where the others
  say how to quit. `PlayerInputSystem` feeds the run's buttons first: a finger put down on one is
  the button's until it lifts, and neither drags the bat nor takes the stick. Only a finger put down
  on it presses it, so a steering finger sliding into the corner does not pause.
- The back *gesture* starts as a touch on the game, which the system cancels once it claims the
  swipe. The cancel reaches the game as a `TOUCH_UP` marked `canceled` (Compose's made-up release,
  already consumed; see `ComposePointerAdapter`), and `TapDetector` never counts one as a tap: read
  as a tap, it resumed the pause screen just before the Back meant to quit it.
- The menu is worked by keys too, the same on both platforms (`MenuKeys`), because Compose moves
  focus with the arrows only on Android. Focus is the cursor: the arrows and WASD move it, Enter,
  Space or a pad's A take what it is on, Escape, Backspace or B go back. Each screen names its first
  choice with `HomeCursor`, and each control draws the cursor with a `CursorMark`, read off its
  focus. When no control has focus - touch mode, or the focused one went away - the menu's root
  holds it, so the next key still arrives and brings the cursor back; while a control has it, the
  root cannot take focus, or Android's Back would move focus to it instead of going back.
- On the desktop the window's key handler feeds `ControlHandler` only while a run is up; in the menu
  it swallowed the arrows and Enter the menu is worked with.
- Vibration goes to whatever the player is holding, and only to that. `AndroidGameActivity`
  remembers the device of the last key, or stick past its dead zone, that the game took, and a touch
  hands it back to the phone; `AndroidHaptics` rumbles that device's motors if it has any, and the
  phone's otherwise. The player's switch is applied by `ObservedHaptics`, which the host wraps round
  the haptics it hands the game. The desktop rumbles the pad last used, and only while the player
  plays with a pad; the browser has nothing to vibrate, and its Settings leave the switch out
  (`canVibrate`).
