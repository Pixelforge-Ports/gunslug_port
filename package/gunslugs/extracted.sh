#!/bin/bash
# SPDX-License-Identifier: MIT
# First launch: convert the owner's APK with the mounted PortMaster JRE.
set -e
GAMEDIR="${1:-$(cd -- "$(dirname -- "$0")" && pwd)}"
JAVA_HOME="${2:-${JAVA_HOME:-/tmp/gunslugs-java}}"
if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
    echo "Launch Gunslugs from Ports to load its Java runtime first."
    exit 1
fi
prepare_cp="$GAMEDIR/runtime/prepare/*"
if "$JAVA_HOME/bin/java" -Xmx128m -cp "$prepare_cp" PrepareDevice --check "$GAMEDIR/gamedata"; then
    echo "Prepared GAME.JAR and assets found."
    exit 0
fi
echo "Preparing Gunslugs 3.2.4. Please wait several minutes and do not power off."
"$JAVA_HOME/bin/java" -Xmx128m -XX:+UseSerialGC \
    "-Djava.io.tmpdir=$GAMEDIR/cache" -cp "$prepare_cp" PrepareDevice "$GAMEDIR"
