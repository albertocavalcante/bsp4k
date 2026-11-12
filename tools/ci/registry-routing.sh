#!/usr/bin/env bash
set -euo pipefail

: "${RUNNER_TEMP:?A private runner temp directory is required}"
fixture_root=$(mktemp -d "$RUNNER_TEMP/bsp4k-registry-route.XXXXXXXX")
trap 'rm -rf "$fixture_root"' EXIT
export BSP_ROUTE_PRIMARY="$fixture_root/primary"
export BSP_ROUTE_FALLBACK="$fixture_root/fallback"

artifact() {
	local repo=$1 module=$2 version=$3 marker=$4 directory
	directory="$repo/io/github/albertocavalcante/$module/$version"
	mkdir -p "$directory"
	printf '%s' "$marker:$module" >"$directory/$module-$version.jar"
	cat >"$directory/$module-$version.pom" <<POM
<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>io.github.albertocavalcante</groupId><artifactId>$module</artifactId><version>$version</version></project>
POM
}
for module in jsonrpc4k-core jsonrpc4k-core-jvm jsonrpc4k-core-js jsonrpc4k-core-linuxx64 jsonrpc4k-transport jsonrpc4k-transport-jvm jsonrpc4k-transport-macosarm64; do
	artifact "$BSP_ROUTE_PRIMARY" "$module" 1.0 primary
	artifact "$BSP_ROUTE_FALLBACK" "$module" 1.0 fallback
done
artifact "$BSP_ROUTE_FALLBACK" jsonrpc4k-core 2.0 fallback
artifact "$BSP_ROUTE_FALLBACK" bsp4k-route-canary 1.0 fallback
artifact "$BSP_ROUTE_FALLBACK" jsonrpc4k-coreevil 1.0 fallback

common=(--no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pgpr.user= -Pgpr.key= -Pbsp4k.useLocalJsonrpc4k=false --init-script tools/ci/registry-routing/route.init.gradle.kts)
# This confirms the default never adds the GitHub route.
./gradlew "${common[@]}" help
# Reject any explicitly provided value other than github before resolution.
if ./gradlew "${common[@]}" -Pjsonrpc4k.repository=invalid help >"$fixture_root/invalid.log" 2>&1; then
	printf 'Invalid repository selection unexpectedly succeeded\n' >&2
	exit 1
fi
if ! grep -Fq 'jsonrpc4k.repository must be github when set' "$fixture_root/invalid.log"; then
	cat "$fixture_root/invalid.log" >&2
	exit 1
fi
# Do not send caller credentials to this test-only file repository.
env -u GITHUB_TOKEN -u GITHUB_ACTOR ./gradlew "${common[@]}" \
	-Dorg.gradle.project.gpr.user=fixture-user -Dorg.gradle.project.gpr.key=fixture-token \
	-Pjsonrpc4k.repository=github verifyRegistryRoute
