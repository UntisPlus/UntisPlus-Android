#!/usr/bin/env bash
set -euo pipefail

# Fails if a runtime dependency or hand-written source reintroduces a
# proprietary Google component. Open-source Google artifacts (AndroidX,
# Material Components, protobuf, Guava, ZXing, etc.) are intentionally allowed.
readonly forbidden='com\.google\.android\.gms|com\.google\.firebase|com\.google\.mlkit|com\.google\.android\.play|play-services|googlemobileads|datatransport|com\.android\.vending'

if git grep -nIE "$forbidden" -- \
	app/src/main/AndroidManifest.xml \
	app/src/main/java \
	app/src/test \
	app/src/androidTest \
	api/src \
	shared/src \
	'*.gradle.kts' \
	gradle/libs.versions.toml; then
	echo "::error::Proprietary Google component referenced in source"
	exit 1
fi

runtime_deps="$(mktemp)"
trap 'rm -f "$runtime_deps"' EXIT

./gradlew :app:dependencies --configuration releaseRuntimeClasspath --console=plain > "$runtime_deps"
if grep -Eqi "$forbidden" "$runtime_deps"; then
	echo "::error::Proprietary Google component found in releaseRuntimeClasspath"
	grep -Ein "$forbidden" "$runtime_deps" | head -20 >&2
	exit 1
fi

echo "FOSS check passed: no proprietary Google components in source or release runtime dependencies"
