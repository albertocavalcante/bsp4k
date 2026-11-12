#!/usr/bin/env bash
set -euo pipefail

: "${RUNNER_TEMP:?A private runner temp directory is required}"
: "${GITHUB_RUN_ID:?A unique run ID is required}"
: "${GITHUB_RUN_ATTEMPT:?A unique run attempt is required}"
: "${JAVA_HOME_17_X64:?JDK 17 is required}"

tools/ci/check-publish-workflow.sh
tools/ci/registry-routing.sh

export BSP_FIXTURE_VERSION="0.0.0-java17-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"
fixture_root=$(mktemp -d "$RUNNER_TEMP/bsp4k-java17.XXXXXXXX")
trap 'rm -rf "$fixture_root"' EXIT
export BSP_FIXTURE_REPOSITORY="$fixture_root/repository"
root_consumer="$fixture_root/root-consumer"
jvm_consumer="$fixture_root/jvm-consumer"
mkdir -p "$BSP_FIXTURE_REPOSITORY" "$root_consumer" "$jvm_consumer"
cp -R tools/ci/java17-consumer/. "$root_consumer/"
cp -R tools/ci/java17-consumer/. "$jvm_consumer/"

# The real publication preflight must reject an empty declared-dependency repository.
export BSP_PREFLIGHT_REPOSITORY="$fixture_root/preflight"
preflight_log="$fixture_root/preflight-missing.log"
if ./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m \
	-PVERSION_NAME="$BSP_FIXTURE_VERSION" -Pbsp4k.useLocalJsonrpc4k=false \
	--init-script tools/ci/java17-consumer/publish.init.gradle.kts \
	--init-script tools/ci/publication-preflight.init.gradle.kts \
	publishAllPublicationsToPreflightRepository --refresh-dependencies >"$preflight_log" 2>&1; then
	printf 'Publication preflight unexpectedly succeeded without JSON-RPC artifacts\n' >&2
	exit 1
fi
jsonrpc_version=$(python3 -c 'import tomllib; print(tomllib.load(open("gradle/libs.versions.toml", "rb"))["versions"]["jsonrpc4k"])')
missing_coordinate="io.github.albertocavalcante:jsonrpc4k-transport:$jsonrpc_version"
if ! grep -Fq "Could not find $missing_coordinate." "$preflight_log"; then
	cat "$preflight_log" >&2
	printf 'Publication preflight failed for an unrelated reason\n' >&2
	exit 1
fi

../jsonrpc4k/gradlew -p ../jsonrpc4k --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m \
	--init-script "$PWD/tools/ci/java17-consumer/publish.init.gradle.kts" \
	:jsonrpc4k-core:publishAllPublicationsToFixtureRepository \
	:jsonrpc4k-transport:publishAllPublicationsToFixtureRepository

# The identical preflight succeeds once pinned JSON-RPC binaries are independently resolvable.
env -u GITHUB_TOKEN -u GITHUB_ACTOR ./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m \
	-PVERSION_NAME="$BSP_FIXTURE_VERSION" -Pgpr.user= -Pgpr.key= -Pbsp4k.useLocalJsonrpc4k=false -Pjsonrpc4k.repository=github \
	--init-script tools/ci/registry-routing/redirect.init.gradle.kts \
	--init-script tools/ci/publication-preflight.init.gradle.kts \
	publishAllPublicationsToPreflightRepository --refresh-dependencies

./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m \
	-PVERSION_NAME="$BSP_FIXTURE_VERSION" -Pbsp4k.useLocalJsonrpc4k=false \
	--init-script tools/ci/java17-consumer/publish.init.gradle.kts \
	:bsp4k-protocol:publishKotlinMultiplatformPublicationToFixtureRepository \
	:bsp4k-protocol:publishJvmPublicationToFixtureRepository \
	:bsp4k-client:publishKotlinMultiplatformPublicationToFixtureRepository \
	:bsp4k-client:publishJvmPublicationToFixtureRepository \
	:bsp4k-server:publishKotlinMultiplatformPublicationToFixtureRepository \
	:bsp4k-server:publishJvmPublicationToFixtureRepository

python3 -I - <<'PY'
import json
import os
from pathlib import Path
import struct
import xml.etree.ElementTree as ET
import zipfile

root = Path(os.environ["BSP_FIXTURE_REPOSITORY"]) / "io/github/albertocavalcante"
version = os.environ["BSP_FIXTURE_VERSION"]
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
expected_api = {
    "bsp4k-protocol": [("org.jetbrains.kotlinx", "kotlinx-serialization-json", "1.9.0")],
    "bsp4k-client": [("io.github.albertocavalcante", "bsp4k-protocol", version),
                     ("io.github.albertocavalcante", "jsonrpc4k-transport", "0.1.0-SNAPSHOT"),
                     ("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.10.2")],
    "bsp4k-server": [("io.github.albertocavalcante", "bsp4k-protocol", version),
                     ("io.github.albertocavalcante", "jsonrpc4k-transport", "0.1.0-SNAPSHOT"),
                     ("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.10.2")],
}
for library in expected_api:
    for artifact in (library, f"{library}-jvm"):
        directory = root / artifact / version
        prefix = directory / f"{artifact}-{version}"
        pom = ET.parse(f"{prefix}.pom").getroot()
        assert pom.findtext("m:groupId", namespaces=ns) == "io.github.albertocavalcante"
        assert pom.findtext("m:artifactId", namespaces=ns) == artifact
        assert pom.findtext("m:version", namespaces=ns) == version
        metadata = json.loads(Path(f"{prefix}.module").read_text())
        component = metadata["component"]
        assert component["group"] == "io.github.albertocavalcante"
        assert component["module"] == library and component["version"] == version
        if artifact.endswith("-jvm"):
            assert (directory / component["url"]).resolve() == (root / library / version / f"{library}-{version}.module").resolve()
        variants = [v for v in metadata["variants"] if v["attributes"].get("org.jetbrains.kotlin.platform.type") == "jvm"
                    and v["attributes"].get("org.gradle.usage") in ("java-api", "java-runtime")]
        assert {v["attributes"]["org.gradle.usage"] for v in variants} == {"java-api", "java-runtime"}, variants
        assert all(v["attributes"].get("org.gradle.jvm.version") == 17 for v in variants), variants
        if artifact == library:
            redirects = [v["available-at"] for v in variants if "available-at" in v]
            assert any(x.get("module") == f"{library}-jvm" and x.get("version") == version for x in redirects), redirects
            continue
        deps = [(d.findtext("m:groupId", namespaces=ns), d.findtext("m:artifactId", namespaces=ns),
                 d.findtext("m:version", namespaces=ns), d.findtext("m:scope", default="compile", namespaces=ns))
                for d in pom.findall("m:dependencies/m:dependency", ns)]
        api = [v for v in variants if v["attributes"]["org.gradle.usage"] == "java-api"]
        for group, module, wanted in expected_api[library]:
            assert any(g == group and m in (module, f"{module}-jvm") and v == wanted and scope == "compile"
                       for g, m, v, scope in deps), deps
            assert all(any(d["group"] == group and d["module"] in (module, f"{module}-jvm")
                           and d.get("version", {}).get("requires") == wanted
                           for d in v.get("dependencies", [])) for v in api), api
        with zipfile.ZipFile(f"{prefix}.jar") as jar:
            classes = [name for name in jar.namelist() if name.endswith(".class")]
            assert classes, f"{artifact} contains no classes"
            majors = [struct.unpack(">H", jar.read(name)[6:8])[0] for name in classes]
            assert max(majors) == 61, (artifact, max(majors))
            print(f"{artifact}: {len(classes)} classes, max major {max(majors)}; JVM metadata 17")
PY

BSP_FIXTURE_FORM=root JAVA_HOME="$JAVA_HOME_17_X64" PATH="$JAVA_HOME_17_X64/bin:$PATH" \
	./gradlew --no-daemon --max-workers=2 --info -Dorg.gradle.jvmargs=-Xmx1536m -p "$root_consumer" \
	run verifyJava16Rejected
BSP_FIXTURE_FORM=jvm JAVA_HOME="$JAVA_HOME_17_X64" PATH="$JAVA_HOME_17_X64/bin:$PATH" \
	./gradlew --no-daemon --max-workers=2 --info -Dorg.gradle.jvmargs=-Xmx1536m -p "$jvm_consumer" run
