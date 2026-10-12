#!/bin/bash
set -euo pipefail
test_dir="$(cd "$(dirname "$0")" && pwd)"
repo_root="$(cd "$test_dir/../.." && pwd)"
clt_root="/Library/Developer/CommandLineTools"
build_dir="$(mktemp -d /private/tmp/iro-core-checks.XXXXXX)"
"$clt_root/usr/bin/swiftc" -O -D PORTABLE_CORE_CHECKS \
  -target "$(uname -m)-apple-macosx13.0" -sdk "$clt_root/SDKs/MacOSX.sdk" \
  -module-cache-path "$build_dir/module-cache" \
  "$repo_root"/ios/IRO/Core/*.swift "$test_dir/PortableCoreChecks.swift" \
  -o "$build_dir/core-checks"
"$build_dir/core-checks" "$repo_root/ios/IRO/Resources/recipes.json"
