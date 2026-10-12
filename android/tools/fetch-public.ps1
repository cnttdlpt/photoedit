param([Parameter(Mandatory=$true)][string]$ListPath)
$ErrorActionPreference = 'Stop'
$cache = Join-Path $PSScriptRoot 'cache'
New-Item -ItemType Directory -Path $cache -Force | Out-Null
$items = Get-Content -LiteralPath $ListPath -Raw | ConvertFrom-Json
$items | ForEach-Object -Parallel {
    $item = $_
    $url = [uri]$item.url
    if ($url.Scheme -ne 'https' -or $url.Host -ne 'fujixweekly.com') { throw 'Unexpected source domain' }
    $dest = Join-Path $using:cache ($item.id + '.html')
    if (Test-Path -LiteralPath $dest) { return }
    try {
        Invoke-WebRequest -Uri $url.AbsoluteUri -OutFile $dest -TimeoutSec 45
        Start-Sleep -Milliseconds 400
    } catch {
        Write-Output ('FAILED ' + $item.url + ' ' + $_.Exception.Message)
    }
} -ThrottleLimit 3
