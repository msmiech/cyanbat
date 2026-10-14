# Turns a release's version - the tag, as for the other platforms - into the two an iOS build
# carries, for archive.sh and build_ipa.sh to source. App Store versions are numbers only, so a
# pre-release suffix (3.5-rc1) is dropped, and the build number is derived as the root build derives
# Android's versionCode, so the two stores' numbers agree.
#
#   parse_version 3.5-rc1    # sets marketing_version=3.5.0 and build_number=30500
parse_version() {
  local version="${1#v}"
  local major minor patch
  IFS=. read -r major minor patch <<< "${version%%-*}"
  patch="${patch:-0}"
  if ! [[ "$major" =~ ^[0-9]+$ && "$minor" =~ ^[0-9]+$ && "$patch" =~ ^[0-9]+$ ]]; then
    echo "'$version' is not major.minor[.patch][-suffix]" >&2
    return 1
  fi
  marketing_version="$major.$minor.$patch"
  build_number=$((major * 10000 + minor * 100 + patch))
}
