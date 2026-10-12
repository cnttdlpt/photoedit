# iOS recipe and LUT library

`LibraryStore` and `OnlineLUTService` are actors. Create them once and call them
from a view model with `await`; UI state belongs to the main actor. The default
library directory is `Application Support/IRO/Library`. An explicit directory
can be supplied for isolated tests. Parsed LUTs use an actor-owned cache capped
at sixteen entries for reuse across recipe thumbnails.
Call `clearParsedLUTCache()` on memory pressure; persistent files are unaffected.

```swift
let library = try LibraryStore()
let online = OnlineLUTService(store: library)
let recipes = try await library.loadBundledRecipes()
let snapshot = await library.snapshot()
let lut = try await library.boundLUT(for: settings.film)
```

The resource bundle must contain `recipes.json` and `sample-recipe.json` copied
from Android. `loadBundledRecipes` returns custom recipes followed by bundled
recipes. After loading, `snapshot().recipes` returns the same merged library.

## Local documents

```swift
let imported = try await library.importLUT(from: documentURL, film: settings.film)
try await library.assignLUT(imported, to: "Classic Chrome")
try await library.removeLUTBinding(for: "Classic Chrome")
let result = try await library.importRecipe(from: jsonURL)
switch result {
case .recipes(let recipes): /* refresh library, optionally apply one */ break
case .editing(let edit): /* apply edit.settings and edit.recipe */ break
}
let json = try await library.exportRecipe(settings: settings, recipe: currentRecipe)
```

The URL import methods acquire/release security-scoped access and read with a
strict byte limit. Data-based overloads are also available. Custom recipes are
persisted when imported or passed to `saveRecipe`. Edit packages are returned to
the caller for editor session persistence. Export uses Android's `IRO_ANDROID`
v1 discriminator with an extra `platform: iOS` field, so existing Android builds
can import the resulting JSON. Both `IRO_ANDROID` and `IRO_IOS` are accepted on
import. LUT files remain separate from recipe JSON.

## Online catalog

```swift
try await library.setRepository("t0saki/lumix-original-looks")
try await library.setAutomaticUpdates(false)
let cached = await online.cachedCatalog(query: "warm")
let refreshed = try await online.refresh()
let installed = try await online.download(refreshed.items[0], assignTo: settings.film)
let downloadState = await online.state(for: refreshed.items[0])
```

Call `await online.checkOnOpen()` at app opening. Automatic checks are disabled
by default. When enabled, they wait 24 hours after the last successful discovery
and at least one hour after the last attempt. A refresh automatically downloads
at most 20 changed LUTs whose source paths were previously installed. Errors
preserve old files. A newer downloaded version never changes a film's existing
assignment; only an explicit import/use/assign action does.

Only HTTPS `api.github.com` and `raw.githubusercontent.com` are fetched, with
redirects disabled, ephemeral sessions, no cookies, bounded timeouts, a 35 MiB
LUT cap and an 8 MiB metadata cap. Repositories with truncated trees or more than
1000 eligible LUT entries are rejected. Downloads are pinned to a commit and
must match the Git blob SHA-1 and parse as a supported 3D `.cube` before storage.
Cached downloads are verified before reuse. The service has no photo input.

`OnlineLUT` exposes `repository`, `path`, `name`, `sha`, `revision`, `byteCount`,
`license`, `input`, and `sourceURL`. Show the input color space and source license
in LUT details. These are source metadata, not an endorsement of compatibility.
`snapshot().onlinePreferences.status` retains the last refresh result for display.

## Verification

`Tests/LibraryTests.swift` covers repository validation, the known Git blob hash
for `hello\n`, Android JSON round trips, malformed/oversized imports, persistence,
checksum rejection, opt-in/cooldown behavior and preservation of film assignments
when a newer online version is installed. These Foundation/CryptoKit tests can
also run with the separately licensed macOS Command Line Tools. Real GitHub
requests and iOS runtime behavior require integration testing in Xcode.
