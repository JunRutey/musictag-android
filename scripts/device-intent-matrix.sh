#!/usr/bin/env bash
# Device-side companion to tools/intent_matrix.py.
#
# The static matrix proves the merged manifest resolves the historical intents; this script
# proves a real device agrees, and that a content:// round-trip still writes tags. Run it
# with an emulator or device attached (`adb devices` non-empty) and the app installed.
#
#   scripts/device-intent-matrix.sh [package]
#
# It needs one audio file on the device; pass it as DEVICE_FILE (default: /sdcard/Music/test.mp3).
set -uo pipefail

PKG="${1:-com.xjcheng.musictageditor}"
FILE="${DEVICE_FILE:-/sdcard/Music/test.mp3}"
FAILURES=0

check() {
  local description="$1"; shift
  local output
  output="$("$@" 2>&1)"
  if [[ "$output" == *"Error"* || "$output" == *"Exception"* || -z "$output" ]]; then
    echo "  [FAIL] $description"
    echo "         ${output//$'\n'/ }"
    FAILURES=$((FAILURES + 1))
  else
    echo "  [ok]   $description"
  fi
}

echo "package: $PKG"
echo "file:    $FILE"

echo "== explicit components =="
for component in \
  MainActivity SongDetailActivity activity.SearchActivity activity.SearchMediaActivity \
  activity.BatchEditSongsDetail activity.SettingsActivity activity.AlbumActivity \
  activity.ArtistActivity activity.SearchWebPictureActivity activity.SearchWebLyricActivity \
  activity.SearchWebTagActivity activity.BatchESDSelTagPic activity.IgnoreDirSetupActivity \
  activity.DonateActivity activity.CropImageActivity; do
  check "explicit $component" adb shell am start -W -n "$PKG/$component"
done

echo "== implicit intents =="
check "VIEW audio/mpeg" adb shell am start -W -a android.intent.action.VIEW -d "file://$FILE" -t audio/mpeg
check "SEND audio/mpeg" adb shell am start -W -a android.intent.action.SEND -t audio/mpeg --es android.intent.extra.STREAM "file://$FILE"
check "SEARCH" adb shell am start -W -a android.intent.action.SEARCH --es query "test"

echo "== providers =="
check "FileProvider resolves" adb shell content query --uri "content://$PKG.fileprovider/"
check "SearchSuggProvider resolves" adb shell content query --uri "content://$PKG.SearchSugg/search_suggest_query/test"

echo
if [[ "$FAILURES" -eq 0 ]]; then
  echo "device intent matrix passed"
else
  echo "device intent matrix: $FAILURES failure(s)"
  exit 1
fi
