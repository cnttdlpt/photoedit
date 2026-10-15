param(
    [string]$Message = "",
    [switch]$Push,
    [switch]$Pull,
    [switch]$Status
)

$ErrorActionPreference = "Stop"
$localWorkspace = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
$gitRepo = "C:\Users\NamNV.dpt\Documents\photoedit"
$gitAndroid = Join-Path $gitRepo "android"

if (-not (Test-Path -LiteralPath $gitRepo)) {
    Write-Host "[ERROR] Git repository not found at $gitRepo" -ForegroundColor Red
    exit 1
}

if ($Pull) {
    Write-Host "-> Pulling latest code from GitHub origin/main..." -ForegroundColor Cyan
    Push-Location -LiteralPath $gitRepo
    try {
        git pull origin main
    } finally {
        Pop-Location
    }

    Write-Host "-> Syncing from Git into Android Studio workspace..." -ForegroundColor Cyan
    $syncFolders = @("app\src", "desktop", "tools")
    $syncFiles = @("build.gradle", "settings.gradle", "gradle.properties", "README.md")

    foreach ($folder in $syncFolders) {
        $src = Join-Path $gitAndroid $folder
        $dst = Join-Path $localWorkspace $folder
        if (Test-Path -LiteralPath $src) {
            Get-ChildItem -LiteralPath $src -Force | ForEach-Object {
                Copy-Item -LiteralPath $_.FullName -Destination $dst -Recurse -Force
            }
        }
    }
    foreach ($file in $syncFiles) {
        $src = Join-Path $gitAndroid $file
        $dst = Join-Path $localWorkspace $file
        if (Test-Path -LiteralPath $src) {
            Copy-Item -LiteralPath $src -Destination $dst -Force
        }
    }
    Write-Host "Done: Synced into Android Studio workspace." -ForegroundColor Green
    exit 0
}

Write-Host "-> Syncing source from Android Studio workspace to Git repo..." -ForegroundColor Cyan

$syncItems = @(
    "app\src",
    "app\build.gradle",
    "desktop",
    "tools",
    "build.gradle",
    "settings.gradle",
    "gradle.properties",
    "README.md",
    "AGENTS.md"
)

foreach ($item in $syncItems) {
    $src = Join-Path $localWorkspace $item
    $dst = Join-Path $gitAndroid $item
    if (Test-Path -LiteralPath $src) {
        if ((Get-Item -LiteralPath $src) -is [System.IO.DirectoryInfo]) {
            if (-not (Test-Path -LiteralPath $dst)) {
                New-Item -ItemType Directory -Path $dst -Force | Out-Null
            }
            robocopy $src $dst /E /XD dist build .gradle cache .idea bin /XF *.jar *.apk *.exe *.log *.class /NFL /NDL /NJH /NJS /nc /ns /np | Out-Null
            if ($LASTEXITCODE -ge 8) {
                throw "Robocopy failed with code $LASTEXITCODE for $item"
            }
        } else {
            $parent = Split-Path -Parent $dst
            if (-not (Test-Path -LiteralPath $parent)) {
                New-Item -ItemType Directory -Path $parent -Force | Out-Null
            }
            Copy-Item -LiteralPath $src -Destination $dst -Force
        }
    }
}

Push-Location -LiteralPath $gitRepo
try {
    Write-Host "`n=== GIT STATUS (photoedit) ===" -ForegroundColor Yellow
    git status -s

    if ($Push -or ($Message -ne "")) {
        $commitMsg = if ($Message -ne "") { $Message } else { "chore: update from Android workspace" }
        git add -A
        $statusOut = [string](git status --porcelain)
        $hasChanges = -not [string]::IsNullOrWhiteSpace($statusOut)
        if ($hasChanges) {
            git commit -m $commitMsg
            Write-Host "-> Pushing to GitHub origin/main..." -ForegroundColor Cyan
            git push origin main
            Write-Host "Done: Pushed to GitHub main successfully." -ForegroundColor Green
        } else {
            Write-Host "Done: Working tree clean, nothing to commit." -ForegroundColor Green
        }
    }
} finally {
    Pop-Location
}
