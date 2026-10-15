#!/bin/bash
# ------------------------------------------------------------------
#  ND2.0 – pubblica una nuova versione su GitHub
#
#  Doppio clic per:
#   1. incrementare la versione (versionCode +1, versionName 1.N -> 1.N+1)
#   2. compilare l'APK release
#   3. fare commit e push del cambio versione
#   4. creare la release su GitHub con allegato ND2.0.apk
#   5. eliminare le release (e i tag) precedenti
# ------------------------------------------------------------------
set -euo pipefail

cd "$(dirname "$0")"
REPO="carellice/ND2.0"
GRADLE_FILE="app/build.gradle.kts"

bold() { printf "\n\033[1;35m%s\033[0m\n" "$1"; }
fail() { printf "\n\033[1;31m✖ %s\033[0m\n" "$1"; read -r -p "Premi Invio per chiudere…" _ || true; exit 1; }
trap 'fail "Qualcosa è andato storto (riga $LINENO)."' ERR

# JDK: usa quello di Android Studio se non ne è impostato uno.
if [ -z "${JAVA_HOME:-}" ] && [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi

command -v gh >/dev/null || fail "GitHub CLI (gh) non trovato. Installalo con: brew install gh"
gh auth status >/dev/null 2>&1 || fail "Non sei autenticato su GitHub. Esegui: gh auth login"

# ---- 1. Versione -------------------------------------------------
OLD_CODE=$(sed -nE 's/^[[:space:]]*versionCode = ([0-9]+).*/\1/p' "$GRADLE_FILE")
OLD_NAME=$(sed -nE 's/^[[:space:]]*versionName = "([^"]+)".*/\1/p' "$GRADLE_FILE")
MAJOR=${OLD_NAME%%.*}
MINOR=${OLD_NAME#*.}; MINOR=${MINOR%%.*}
NEW_CODE=$((OLD_CODE + 1))
NEW_NAME="$MAJOR.$((MINOR + 1))"
TAG="v$NEW_NAME"

bold "🌙 ND2.0 – nuova release"
echo "Versione attuale: $OLD_NAME ($OLD_CODE)"
echo "Nuova versione:   $NEW_NAME ($NEW_CODE)"
echo
read -r -p "Note di rilascio (Invio per saltare): " NOTES || NOTES=""
[ -z "$NOTES" ] && NOTES="Nuova versione di ND2.0."

sed -i '' -E "s/(versionCode = )[0-9]+/\1$NEW_CODE/; s/(versionName = \")[^\"]+/\1$NEW_NAME/" "$GRADLE_FILE"

# ---- 2. Build ----------------------------------------------------
bold "🔨 Compilo l'APK…"
./gradlew -q assembleRelease
mkdir -p build
cp app/build/outputs/apk/release/app-release.apk build/ND2.0.apk

# ---- 3. Commit e push --------------------------------------------
bold "📤 Commit e push…"
git add -A
git commit -q -m "Release $TAG"
git push -q origin HEAD

# ---- 4. Release --------------------------------------------------
bold "🚀 Creo la release $TAG…"
gh release create "$TAG" build/ND2.0.apk \
    --repo "$REPO" \
    --title "ND2.0 $NEW_NAME" \
    --notes "$NOTES" \
    --latest

# ---- 5. Pulizia release precedenti --------------------------------
bold "🧹 Elimino le release precedenti…"
gh release list --repo "$REPO" --limit 100 --json tagName -q '.[].tagName' | while read -r OLD_TAG; do
    if [ "$OLD_TAG" != "$TAG" ]; then
        echo "  - $OLD_TAG"
        gh release delete "$OLD_TAG" --repo "$REPO" --yes --cleanup-tag
    fi
done

trap - ERR
bold "✅ Fatto! ND2.0 $NEW_NAME è online:"
echo "https://github.com/$REPO/releases/tag/$TAG"
echo
read -r -p "Premi Invio per chiudere…" _ || true
