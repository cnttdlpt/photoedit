param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$JavaHome = 'C:\Program Files\Android\Android Studio\jbr'
)
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$androidJar=Join-Path $Sdk 'platforms/android-35/android.jar'
if (-not (Test-Path -LiteralPath $androidJar)) { throw 'Cần Android SDK Platform 35. Truyền -Sdk nếu SDK ở nơi khác.' }
Push-Location -LiteralPath $project
try {
    New-Item -ItemType Directory -Path 'tools/build' -Force | Out-Null
    $sources=Get-ChildItem -LiteralPath 'app/src/main/java/vn/iro/editor' -Filter '*.java' | ForEach-Object { 'app/src/main/java/vn/iro/editor/' + $_.Name }
    & (Join-Path $JavaHome 'bin/javac.exe') --release 17 -encoding UTF-8 -cp $androidJar -d 'tools/build' $sources 'tools/EngineTest.java' 'tools/OnlineLutsTest.java' 'tools/ExportSizingTest.java'
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }
    & (Join-Path $JavaHome 'bin/java.exe') -cp "tools/build;$androidJar" vn.iro.editor.EngineTest
    if ($LASTEXITCODE -ne 0) { throw 'Image engine tests failed' }
    & (Join-Path $JavaHome 'bin/java.exe') -cp "tools/build;$androidJar" vn.iro.editor.OnlineLutsTest
    if ($LASTEXITCODE -ne 0) { throw 'Online LUT checks failed' }
    & (Join-Path $JavaHome 'bin/java.exe') -cp "tools/build;$androidJar" vn.iro.editor.ExportSizingTest
    if ($LASTEXITCODE -ne 0) { throw 'Export sizing tests failed' }
    if (Get-Command node -ErrorAction SilentlyContinue) {
        node --test tools/catalog.test.mjs
        if ($LASTEXITCODE -ne 0) { throw 'Catalog tests failed' }
    }
    [xml]$manifest=Get-Content -LiteralPath 'app/src/main/AndroidManifest.xml' -Raw
    [xml]$styles=Get-Content -LiteralPath 'app/src/main/res/values/styles.xml' -Raw
    Write-Output 'Source checks passed. No APK was created.'
} finally {
    Remove-Item -Path 'tools/build' -Recurse -Force -ErrorAction SilentlyContinue
    Pop-Location
}
