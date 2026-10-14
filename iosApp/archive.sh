#!/usr/bin/env bash
# Builds the App Store archive of a version and uploads it to App Store Connect, where it appears
# under TestFlight once processed. On a Mac, with Xcode 26 or later, XcodeGen (brew install
# xcodegen) and a JDK; see docs/ios-release.md.
#
#   iosApp/archive.sh 3.5            # version 3.5.0, build 30500
#   iosApp/archive.sh 3.5 30501      # the same version again, as a later build
#
# The version is the tag's, as for the other platforms. App Store versions are numbers only, so a
# pre-release suffix (3.5-rc1) is dropped from it: a release candidate goes to TestFlight as a
# build of the version it leads to, and only a build that passes there is submitted for review.
# The build number has to rise with every upload of a version, so a second upload of one needs a
# second number.
#
# Signing is Xcode's automatic signing, for the team in Configuration/Local.xcconfig. Uploading
# uses the Apple account signed in to Xcode, or an App Store Connect API key when ASC_KEY_PATH,
# ASC_KEY_ID and ASC_ISSUER_ID are set, as on a build machine.
set -euo pipefail

# shellcheck source=versions.sh
source "$(dirname "$0")/versions.sh"
parse_version "${1:?usage: iosApp/archive.sh <version> [build number]}"
build="${2:-$build_number}"

auth=()
if [ -n "${ASC_KEY_PATH:-}" ]; then
  auth=(-authenticationKeyPath "$ASC_KEY_PATH" -authenticationKeyID "$ASC_KEY_ID"
    -authenticationKeyIssuerID "$ASC_ISSUER_ID")
fi

cd "$(dirname "$0")"
xcodegen generate
rm -rf build/CyanBat.xcarchive build/export
xcodebuild archive \
  -project CyanBat.xcodeproj -scheme CyanBat -configuration Release \
  -destination 'generic/platform=iOS' -archivePath build/CyanBat.xcarchive \
  -allowProvisioningUpdates ${auth[@]+"${auth[@]}"} \
  MARKETING_VERSION="$marketing_version" CURRENT_PROJECT_VERSION="$build"
xcodebuild -exportArchive \
  -archivePath build/CyanBat.xcarchive -exportOptionsPlist ExportOptions.plist \
  -exportPath build/export -allowProvisioningUpdates ${auth[@]+"${auth[@]}"}
echo "Uploaded CyanBat $marketing_version ($build) to App Store Connect."
