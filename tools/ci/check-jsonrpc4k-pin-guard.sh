#!/usr/bin/env bash
# Exercises the jsonrpc4k pin guard in settings.gradle.kts against a throwaway jsonrpc4k checkout whose HEAD
# cannot equal the pin: it must fail under CI=true, warn otherwise, and stay silent with the opt-out property.
# Run it from the bsp4k checkout root.
set -euo pipefail

: "${RUNNER_TEMP:?A private runner temp directory is required}"

work=$(mktemp -d "$RUNNER_TEMP/bsp4k-pin-guard.XXXXXXXX")
trap 'rm -rf "$work"' EXIT

fake="$work/jsonrpc4k"
mkdir "$fake"
cat >"$fake/settings.gradle.kts" <<'KTS'
rootProject.name = "jsonrpc4k"
include(":jsonrpc4k-core", ":jsonrpc4k-transport")
KTS
git -C "$fake" init -q
git -C "$fake" add settings.gradle.kts
git -C "$fake" -c user.name=ci -c user.email=ci@example.invalid commit -q -m fixture

# gradle_help LOG CI_VALUE [GRADLE_ARG...]
gradle_help() {
	local log=$1 ci=$2
	shift 2
	CI="$ci" ./gradlew help --console=plain -Pbsp4k.jsonrpc4kBuild="$fake" "$@" >"$log" 2>&1
}

expect_text() {
	if ! grep -q -- "$2" "$1"; then
		printf 'Expected "%s" in %s\n' "$2" "$1" >&2
		cat "$1" >&2
		exit 1
	fi
}

if gradle_help "$work/ci.log" true; then
	printf 'The pin guard did not fail a mismatched jsonrpc4k checkout under CI=true\n' >&2
	cat "$work/ci.log" >&2
	exit 1
fi
expect_text "$work/ci.log" 'bsp4k pins'

gradle_help "$work/local.log" false
expect_text "$work/local.log" 'WARNING: The jsonrpc4k checkout'

gradle_help "$work/optout.log" true -Pbsp4k.allowJsonrpc4kPinMismatch=true
if grep -q 'bsp4k pins' "$work/optout.log"; then
	printf 'The opt-out property did not silence the pin guard\n' >&2
	cat "$work/optout.log" >&2
	exit 1
fi
