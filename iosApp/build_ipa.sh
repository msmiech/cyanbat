#!/usr/bin/env bash
# Builds the iPhone and iPad app for devices, unsigned, and packs it as an .ipa: what the Release
# workflow attaches to a GitHub release, beside the APK and the desktop installers. On a Mac, with
# Xcode 26 or later, XcodeGen and a JDK, as archive.sh needs.
#
#   iosApp/build_ipa.sh 3.5 build/cyanBat-release-3.5-ios-arm64.ipa
#
# Unsigned, because signing needs an Apple developer certificate and a profile naming the devices
# it may run on; an unsigned .ipa is what a sideloading tool (AltStore, SideStore, Sideloadly) takes
# and signs with the player's own Apple ID. The App Store build is archive.sh's, signed by Xcode.
#
# arm64 only, which is every iPhone and iPad that runs the app's iOS 16.
set -euo pipefail

# shellcheck source=versions.sh
source "$(dirname "$0")/versions.sh"
parse_version "${1:?usage: iosApp/build_ipa.sh <version> <output.ipa>}"
output="$(cd "$(dirname "${2:?usage: iosApp/build_ipa.sh <version> <output.ipa>}")" && pwd)/$(basename "$2")"

cd "$(dirname "$0")"
xcodegen generate
rm -rf build/DerivedData build/Payload
xcodebuild build \
  -project CyanBat.xcodeproj -scheme CyanBat -configuration Release \
  -destination 'generic/platform=iOS' -derivedDataPath build/DerivedData \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY="" \
  MARKETING_VERSION="$marketing_version" CURRENT_PROJECT_VERSION="$build_number"

# An .ipa is a zip of the app under Payload/. Symbolic links are kept as links (-y), as the app's
# own structure needs.
mkdir -p build/Payload
cp -R build/DerivedData/Build/Products/Release-iphoneos/CyanBat.app build/Payload/
rm -f "$output"
(cd build && zip -qry "$output" Payload)
echo "Built $output: CyanBat $marketing_version ($build_number), unsigned, arm64."
