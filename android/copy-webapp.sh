#!/bin/bash
# Build SvelteKit and copy output to Android assets for WebView serving.
# Run this before building the Android app in Android Studio.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CLIENT_DIR="$SCRIPT_DIR/../client"
ASSETS_DIR="$SCRIPT_DIR/app/src/main/assets/webapp"

echo "Building SvelteKit client..."
cd "$CLIENT_DIR"
npm run build

echo "Copying build output to Android assets..."
rm -rf "$ASSETS_DIR"
cp -r build "$ASSETS_DIR"

echo "WebApp bundled to $ASSETS_DIR"
echo "Files:"
find "$ASSETS_DIR" -type f | wc -l | xargs echo "  Total files:"
du -sh "$ASSETS_DIR" | awk '{print "  Total size:", $1}'
