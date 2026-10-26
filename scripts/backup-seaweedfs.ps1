param(
    [string]$BackupDirectory = ".\backups\seaweedfs"
)

$ErrorActionPreference = "Stop"

function Invoke-Docker {
    param([string[]]$DockerArgs)

    & docker @DockerArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker $($DockerArgs -join ' ') failed with exit code $LASTEXITCODE"
    }
}

$containerId = (& docker compose ps -q seaweedfs).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
    throw "SeaweedFS is not running in the current Docker Compose project"
}

$volumeName = (& docker inspect --format '{{range .Mounts}}{{if eq .Destination "/data"}}{{.Name}}{{end}}{{end}}' $containerId).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($volumeName)) {
    throw "Could not find the persistent SeaweedFS volume mounted at /data"
}

New-Item -ItemType Directory -Force -Path $BackupDirectory | Out-Null
$resolvedBackupDirectory = (Resolve-Path $BackupDirectory).Path
$dockerBackupDirectory = $resolvedBackupDirectory.Replace("\", "/")
$fileName = "seaweedfs-$([DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')).tar.gz"
$servicesStopped = $false

try {
    # Stop API/worker writers before SeaweedFS, then archive its volume offline.
    Invoke-Docker @("compose", "stop", "mismonedas", "doc-processing", "nginx", "seaweedfs")
    $servicesStopped = $true

    Invoke-Docker @(
        "run", "--rm",
        "--mount", "type=volume,source=$volumeName,target=/data,readonly",
        "--mount", "type=bind,source=$dockerBackupDirectory,target=/backup",
        "alpine:3.20", "tar", "-czf", "/backup/$fileName", "-C", "/data", "."
    )
}
finally {
    if ($servicesStopped) {
        # Restore storage first and let Compose honor its health dependency before
        # restarting the API and OCR worker.
        Invoke-Docker @("compose", "up", "-d", "seaweedfs")

        $seaweedContainer = (& docker compose ps -q seaweedfs).Trim()
        $healthy = $false
        for ($attempt = 0; $attempt -lt 60; $attempt++) {
            $health = (& docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' $seaweedContainer).Trim()
            if ($health -eq "healthy") {
                $healthy = $true
                break
            }
            if ($health -eq "unhealthy") {
                throw "SeaweedFS became unhealthy after backup"
            }
            Start-Sleep -Seconds 2
        }
        if (-not $healthy) {
            throw "Timed out waiting for SeaweedFS to become healthy after backup"
        }

        Invoke-Docker @("compose", "up", "-d", "doc-processing", "mismonedas", "nginx")
    }
}

Write-Output (Join-Path $resolvedBackupDirectory $fileName)
