#!/usr/bin/env bash
# Downloads the AndroidX API files that the bindings are checked against.
# COMPOSE is the Jetpack Compose release the Compose Multiplatform version is based on.
# Material 3 for Compose Multiplatform tracks an alpha, so it uses the latest API file.
set -euo pipefail

COMPOSE=1.12.0-beta01
MATERIAL3=current
BASE=https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose

cd "$(dirname "$0")/api"

fetch() {
    curl -fsSL "$BASE/$1/api/$2.txt" -o "$3.txt"
    echo "$3.txt <- $1 $2"
}

fetch runtime/runtime "$COMPOSE" runtime
fetch ui/ui "$COMPOSE" ui
fetch ui/ui-graphics "$COMPOSE" ui-graphics
fetch ui/ui-text "$COMPOSE" ui-text
fetch ui/ui-unit "$COMPOSE" ui-unit
fetch ui/ui-geometry "$COMPOSE" ui-geometry
fetch foundation/foundation "$COMPOSE" foundation
fetch foundation/foundation-layout "$COMPOSE" foundation-layout
fetch animation/animation "$COMPOSE" animation
fetch animation/animation-core "$COMPOSE" animation-core
fetch material3/material3 "$MATERIAL3" material3
