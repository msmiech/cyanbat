#!/usr/bin/env bash
# CyanBat run driver. Everything here goes through adb; run it from the repo root.
#
#   .claude/skills/run-cyanbat/driver.sh smoke
#   .claude/skills/run-cyanbat/driver.sh start && ... driver.sh hud g1
#
# Why a driver and not a list of adb commands: three things about this app are
# easy to get wrong and are baked in below.
#   1. CyanBatGameActivity is not exported, so it cannot be started with
#      `am start`. The only way in is tapping "Start Game" on the menu.
#   2. `monkey -c LAUNCHER` (the usual "bring the app back" trick) resets the
#      task and finishes the game activity, which silently ruins any
#      pause/resume test. `pause-resume` below covers it with a Settings window
#      instead, which keeps the activity alive.
#   3. `adb shell cat` mangles binary output (LF -> CRLF). Reading the DataStore
#      needs `adb exec-out`.
set -uo pipefail

PKG=at.smiech.cyanbat
MENU_ACTIVITY="$PKG/.MainActivity"
GAME_ACTIVITY_SUFFIX="activity.CyanBatGameActivity"
OUT="${CYANBAT_OUT:-.artifacts/run-cyanbat}"

# --- sdk discovery ------------------------------------------------------------
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ] && [ -n "${LOCALAPPDATA:-}" ]; then
  SDK="$(cygpath -u "$LOCALAPPDATA" 2>/dev/null || echo "$LOCALAPPDATA")/Android/Sdk"
fi
[ -z "$SDK" ] && SDK="$HOME/Android/Sdk"
export PATH="$PATH:$SDK/platform-tools:$SDK/emulator"

command -v adb >/dev/null || { echo "adb not on PATH (looked in $SDK/platform-tools)" >&2; exit 1; }
mkdir -p "$OUT"

log() { printf '\033[36m[driver]\033[0m %s\n' "$*"; }
die() { printf '\033[31m[driver] %s\033[0m\n' "$*" >&2; exit 1; }

# --- device -------------------------------------------------------------------
booted() { [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; }

cmd_boot() {
  if booted; then log "device already up: $(adb devices | sed -n 2p | cut -f1)"; return 0; fi
  local avd="${CYANBAT_AVD:-$(emulator -list-avds 2>/dev/null | head -1)}"
  [ -n "$avd" ] || die "no AVD found; create one in Android Studio or set CYANBAT_AVD"
  log "booting AVD '$avd' (cold boot)"
  emulator -avd "$avd" -no-snapshot-load -no-boot-anim >"$OUT/emulator.log" 2>&1 &
  adb wait-for-device
  until booted; do sleep 3; done
  log "boot completed"
}

cmd_install() {
  log "gradlew :app:installDebug"
  ./gradlew :app:installDebug --console=plain -q || die "install failed"
  log "installed $PKG"
}

# --- ui -----------------------------------------------------------------------
cmd_focus() {
  adb shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | sed 's/^ *//'
}

in_game() { cmd_focus | grep -q "$GAME_ACTIVITY_SUFFIX"; }

# Dump the view hierarchy and print "x y" for the centre of the node whose text
# matches $1. Compose exports its semantics to the accessibility tree, so the
# menu buttons are findable by label - never hardcode tap coordinates, the
# emulator's screen size decides them.
find_text() {
  adb exec-out uiautomator dump /dev/tty 2>/dev/null | tr '<' '\n<' \
    | grep "text=\"$1\"" \
    | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\1 \2 \3 \4/p' \
    | head -1 | awk 'NF==4 {printf "%d %d\n", ($1+$3)/2, ($2+$4)/2}'
}

cmd_tap() {
  local xy; xy="$(find_text "$1")"
  [ -n "$xy" ] || die "no UI node with text \"$1\" (current focus: $(cmd_focus))"
  log "tap \"$1\" at $xy"
  adb shell input tap $xy
}

cmd_menu() {
  adb shell am force-stop "$PKG"; sleep 2
  adb shell am start -n "$MENU_ACTIVITY" >/dev/null; sleep 4
  log "menu: $(cmd_focus)"
}

# The game activity is NOT exported - `am start -n .../.activity.CyanBatGameActivity`
# fails with SecurityException. Tapping the menu button is the only route in.
cmd_start() {
  cmd_menu
  cmd_tap "Start Game"
  sleep 3
  in_game || die "did not reach the game activity (focus: $(cmd_focus))"
  log "in game: $(cmd_focus)"
}

# --- capture ------------------------------------------------------------------
cmd_shot() {
  local name="${1:-shot}"
  adb exec-out screencap -p > "$OUT/$name.png"
  local sz; sz=$(wc -c < "$OUT/$name.png")
  [ "$sz" -gt 1000 ] || die "screenshot $name.png is $sz bytes - capture failed"
  log "$OUT/$name.png ($sz bytes)"
}

# Crop + 1:1 the score/highscore/lives overlay. The HUD is drawn into a 480x320
# framebuffer that is stretched to the full window, so the text is small and
# blurry in a full screenshot; this makes it readable.
cmd_hud() {
  local name="${1:-hud}"
  cmd_shot "$name"
  command -v powershell.exe >/dev/null || { log "no powershell.exe; skipping crop"; return 0; }
  MSYS_NO_PATHCONV=1 powershell.exe -NoProfile -Command "
    Add-Type -AssemblyName System.Drawing
    \$src = [System.Drawing.Image]::FromFile((Resolve-Path '$OUT/$name.png'))
    \$w = [int](\$src.Width * 0.26); \$h = [int](\$src.Height * 0.21)
    \$c = New-Object System.Drawing.Bitmap \$w, \$h
    \$g = [System.Drawing.Graphics]::FromImage(\$c)
    \$g.DrawImage(\$src, (New-Object System.Drawing.Rectangle 0,0,\$w,\$h), (New-Object System.Drawing.Rectangle 0,10,\$w,\$h), [System.Drawing.GraphicsUnit]::Pixel)
    \$g.Dispose(); \$src.Dispose()
    \$c.Save((Join-Path (Resolve-Path '$OUT') 'hud_$name.png'), [System.Drawing.Imaging.ImageFormat]::Png); \$c.Dispose()
  " >/dev/null 2>&1 && log "$OUT/hud_$name.png"
}

# --- interaction --------------------------------------------------------------
# Drive a full run to completion. The bat only moves while TOUCH_DRAGGED events
# arrive and takes hits standing still, so an unattended run dies within seconds
# scoring a few hundred; blind swipes roughly double that. They do NOT keep it
# alive indefinitely - once it dies, the next swipe's TOUCH_UP dismisses
# GameOverScreen and lands back on the menu. That makes this the way to complete
# a run and exercise the highscore write path; check it with `highscore` after.
cmd_play() {
  local secs="${1:-30}"
  local deadline=$(( $(date +%s) + secs ))
  log "driving the bat for ${secs}s"
  while [ "$(date +%s)" -lt "$deadline" ]; do
    adb shell input swipe 1200 300 1200 800 400 >/dev/null 2>&1
    adb shell input swipe 1200 800 1200 300 400 >/dev/null 2>&1
  done
  log "done; focus: $(cmd_focus)"
}

# A real onPause/onResume WITHOUT losing the activity.
# Do not use `monkey -p ... -c android.intent.category.LAUNCHER 1` to come back:
# that delivers a launcher intent, which resets the task to MainActivity and
# finishes the game activity. KEYCODE_HOME + recents is fiddly to tap reliably.
# Covering the game with Settings and pressing BACK keeps the same window.
cmd_pause_resume() {
  local before after
  before="$(cmd_focus)"
  in_game || die "not in the game activity; run 'start' first"
  cmd_hud "pr_before"
  adb shell am start -a android.settings.SETTINGS >/dev/null 2>&1; sleep 4
  log "covered by: $(cmd_focus)"
  adb shell input keyevent KEYCODE_BACK; sleep 3
  after="$(cmd_focus)"
  cmd_hud "pr_after"
  log "before: $before"
  log "after : $after"
  if [ "$before" = "$after" ]; then
    log "same window - genuine onPause/onResume on a surviving activity"
  else
    log "WARNING: window changed; the activity was recreated, not resumed"
  fi
}

# --- persistence --------------------------------------------------------------
# Preferences protobuf: 0a 10 0a 09 "highscore" 12 03 18 <varint>
# `adb exec-out` is required - `adb shell` turns every 0x0a into 0d 0a and the
# varint decodes to garbage (a stored 1350 reads back as 1734).
cmd_highscore() {
  local bytes
  bytes=$(adb exec-out run-as "$PKG" cat files/datastore/cyanbat.preferences_pb 2>/dev/null | od -An -tu1 | tr -s ' ' '\n' | grep -v '^$')
  [ -n "$bytes" ] || { log "no datastore file yet (play one run to game over)"; return 0; }
  echo "$bytes" | awk '
    { b[NR]=$1 }
    END {
      for (i=1; i<=NR; i++) if (b[i]==24) {           # 0x18 = field 3, varint
        v=0; s=0
        for (j=i+1; j<=NR; j++) {
          v += (b[j] % 128) * (2 ^ s); s += 7
          if (b[j] < 128) break
        }
        print "highscore=" v; exit
      }
      print "highscore key not found"
    }'
}

# --- diagnostics --------------------------------------------------------------
cmd_logs() {
  echo "--- crash buffer ---"
  adb logcat -d -b crash 2>/dev/null | tail -40
  echo "--- runtime errors + app tag ---"
  adb logcat -d AndroidRuntime:E System.err:W CyanBat:D '*:S' 2>/dev/null | tail -40
}

cmd_stop() { adb shell am force-stop "$PKG"; log "force-stopped $PKG"; }

# --- end to end ---------------------------------------------------------------
cmd_smoke() {
  cmd_boot
  cmd_install
  adb logcat -c
  cmd_start
  sleep 3
  cmd_hud "smoke_game"
  cmd_pause_resume
  cmd_highscore
  echo "--- crashes ---"
  local crashes; crashes=$(adb logcat -d -b crash 2>/dev/null | wc -l)
  if [ "$crashes" -gt 1 ]; then
    adb logcat -d -b crash | tail -30; die "crash buffer is not empty"
  fi
  log "no crashes"
  log "PASS - artifacts in $OUT/"
}

case "${1:-smoke}" in
  boot)          cmd_boot ;;
  install)       cmd_install ;;
  menu)          cmd_menu ;;
  start)         cmd_start ;;
  tap)           shift; cmd_tap "$@" ;;
  shot)          shift; cmd_shot "$@" ;;
  hud)           shift; cmd_hud "$@" ;;
  play)          shift; cmd_play "$@" ;;
  pause-resume)  cmd_pause_resume ;;
  highscore)     cmd_highscore ;;
  focus)         cmd_focus ;;
  logs)          cmd_logs ;;
  stop)          cmd_stop ;;
  smoke)         cmd_smoke ;;
  *) sed -n '2,14p' "$0"; exit 1 ;;
esac
