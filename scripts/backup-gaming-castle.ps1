param(
    [Parameter(Mandatory = $true)]
    [string]$WorldPath,

    [string]$InstancePath = "C:\Users\dalto\curseforge\minecraft\Instances\All the Mods 10 - ATM10",

    [int]$Keep = 10
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $WorldPath)) {
    throw "World path does not exist: $WorldPath"
}

$world = (Resolve-Path -LiteralPath $WorldPath).Path
$instance = (Resolve-Path -LiteralPath $InstancePath).Path
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupRoot = Join-Path $instance "gaming-castle-backups"
$staging = Join-Path $backupRoot ".staging-$timestamp"
$zipPath = Join-Path $backupRoot "gaming-castle-$timestamp.zip"

New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
New-Item -ItemType Directory -Force -Path $staging | Out-Null

Write-Host "Gaming Castle backup" -ForegroundColor Magenta
Write-Host "World: $world"
Write-Host "Instance: $instance"
Write-Host "IMPORTANT: Minecraft / the dedicated server should be fully stopped for a consistent backup." -ForegroundColor Yellow

try {
    Write-Host "Copying world..."
    Copy-Item -LiteralPath $world -Destination (Join-Path $staging "world") -Recurse -Force

    $serverCoreConfig = Join-Path $instance "config\servercore"
    if (Test-Path -LiteralPath $serverCoreConfig) {
        Write-Host "Copying ServerCore data..."
        Copy-Item -LiteralPath $serverCoreConfig -Destination (Join-Path $staging "servercore-config") -Recurse -Force
    }

    $serverCoreJar = Join-Path $instance "mods\servercore-0.1.0.jar"
    if (Test-Path -LiteralPath $serverCoreJar) {
        Write-Host "Copying deployed ServerCore JAR..."
        New-Item -ItemType Directory -Force -Path (Join-Path $staging "mods") | Out-Null
        Copy-Item -LiteralPath $serverCoreJar -Destination (Join-Path $staging "mods\servercore-0.1.0.jar") -Force
    }

    $manifest = @"
Gaming Castle backup
Created: $(Get-Date -Format o)
World source: $world
ATM10 instance: $instance
Includes: world save, config/servercore when present, deployed ServerCore JAR when present
"@
    Set-Content -LiteralPath (Join-Path $staging "BACKUP-MANIFEST.txt") -Value $manifest -Encoding UTF8

    Write-Host "Creating ZIP..."
    Compress-Archive -Path (Join-Path $staging "*") -DestinationPath $zipPath -CompressionLevel Optimal -Force

    if ($Keep -gt 0) {
        Get-ChildItem -LiteralPath $backupRoot -Filter "gaming-castle-*.zip" -File |
            Sort-Object LastWriteTime -Descending |
            Select-Object -Skip $Keep |
            Remove-Item -Force
    }

    Write-Host "Backup complete: $zipPath" -ForegroundColor Green
}
finally {
    if (Test-Path -LiteralPath $staging) {
        Remove-Item -LiteralPath $staging -Recurse -Force
    }
}
