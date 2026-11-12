#!/usr/bin/env bash
set -euo pipefail

: "${RUNNER_TEMP:?A private runner temp directory is required}"
fixture_root=$(mktemp -d "$RUNNER_TEMP/bsp4k-publish-check.XXXXXXXX")
trap 'rm -rf "$fixture_root"' EXIT
workflow=.github/workflows/publish.yml

extract_run() {
	local label=$1 target=$2
	awk -v label="$label" '
		$0 == "      - name: " label { step = 1; next }
		step && /^        run: \|$/ { body = 1; next }
		body && /^        env:$/ { exit }
		body { sub(/^          /, ""); print }
	' "$workflow" >"$target"
	[[ -s $target ]]
}
check_token_env() {
	local label=$1
	awk -v label="$label" '
		$0 == "      - name: " label { step = 1; next }
		step && /^      - name:/ { exit }
		step && $0 == "          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}" { found = 1 }
		END { if (!found) exit 1 }
	' "$workflow"
}
preflight='Preflight complete publication to a private file repository'
extract_run "$preflight" "$fixture_root/preflight.sh"
extract_run 'Publish' "$fixture_root/publish.sh"
check_token_env "$preflight"
check_token_env 'Publish'
cat >"$fixture_root/gradlew" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$@" > "$TRACE_FILE"
[[ ${GITHUB_TOKEN:-} == fixture-token ]]
STUB
chmod +x "$fixture_root/gradlew"

for mode in preflight publish; do
	for version in '' 7.1.2; do
		trace="$fixture_root/$mode-${version:-manual}.args"
		(
			cd "$fixture_root"
			export RUNNER_TEMP="$fixture_root" TRACE_FILE="$trace" RELEASE_VERSION="$version" GITHUB_TOKEN=fixture-token
			bash "$fixture_root/$mode.sh"
		)
		grep -Fxq -- '-Pbsp4k.useLocalJsonrpc4k=false' "$trace"
		grep -Fxq -- '-Pjsonrpc4k.repository=github' "$trace"
		if [[ -n $version ]]; then
			grep -Fxq -- "-PVERSION_NAME=$version" "$trace"
		elif grep -Fq -- '-PVERSION_NAME=' "$trace"; then
			printf 'Manual publication unexpectedly overrides version\n' >&2
			exit 1
		fi
		if [[ $mode == preflight ]]; then
			grep -Fxq -- publishAllPublicationsToPreflightRepository "$trace"
		else
			grep -Fxq -- publishAllPublicationsToGitHubPackagesRepository "$trace"
		fi
	done
done
