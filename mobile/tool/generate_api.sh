#!/usr/bin/env bash
# Regenerates the Dart API client in lib/api/ from contract/checkout-api.yaml.
#
# Everything in lib/api/ is generated. Never hand-edit it: change the contract and
# run this script again.
#
# Usage (from mobile/):  npm install && npm run generate-api
set -euo pipefail

cd "$(dirname "$0")/.."

SPEC=../contract/checkout-api.yaml
OUT=lib/api
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# The "dart" generator produces a whole package (pubspec, docs, tests). The app only
# needs the library sources, so generate into a temp dir and copy lib/ across.
npx openapi-generator-cli generate \
  -i "$SPEC" \
  -g dart \
  -o "$TMP" \
  --additional-properties=pubName=checkout_api

rm -rf "$OUT"
mkdir -p "$OUT"
cp -R "$TMP/lib/." "$OUT/"

echo "Generated Dart client in mobile/$OUT"
