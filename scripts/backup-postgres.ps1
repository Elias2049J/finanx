param(
    [string]$BackupDirectory = ".\backups\postgres"
)

$ErrorActionPreference = "Stop"

function Invoke-Docker {
    param([string[]]$DockerArgs)

    & docker @DockerArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker $($DockerArgs -join ' ') failed with exit code $LASTEXITCODE"
    }
}

$containerId = (& docker compose ps -q db).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
    throw "PostgreSQL is not running in the current Docker Compose project"
}

New-Item -ItemType Directory -Force -Path $BackupDirectory | Out-Null
$resolvedBackupDirectory = (Resolve-Path $BackupDirectory).Path
$timestamp = [DateTime]::UtcNow.ToString("yyyyMMddTHHmmssZ")
$remoteFile = "/tmp/mismonedas-$timestamp.dump"
$localFile = Join-Path $resolvedBackupDirectory "mismonedas-$timestamp.dump"

try {
    Invoke-Docker @(
        "exec", "-T", "db",
        "pg_dump", "-U", "postgres", "-d", "db_mismonedas",
        "-Fc", "-f", $remoteFile
    )
    Invoke-Docker @("cp", "$containerId`:$remoteFile", $localFile)
} finally {
    & docker exec $containerId rm -f $remoteFile | Out-Null
}

$backup = Get-Item -LiteralPath $localFile
if ($backup.Length -le 0) {
    throw "PostgreSQL backup is empty: $localFile"
}

Write-Host "PostgreSQL backup written to $localFile ($($backup.Length) bytes)"
Write-Host "Restore with the pg_restore commands in docs/production-runbook.md"
