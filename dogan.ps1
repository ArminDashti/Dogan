<#
.SYNOPSIS
    Dogan CLI: rebuild the API and WebUI Docker stack from the latest code.

.EXAMPLE
    .\dogan rebuild
    Rebuild images from current code and recreate containers (data kept).

.EXAMPLE
    .\dogan rebuild remove-data
    Stop the stack, delete its Docker volumes, then rebuild and start fresh.
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Command,
    [Parameter(Position = 1)]
    [string]$Option
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$Script:ProjectName = 'dogan'
$Script:ComposeFiles = @(
    (Join-Path $PSScriptRoot 'dogan-api\docker-compose.yml'),
    (Join-Path $PSScriptRoot 'dogan-webui\docker-compose.yml'),
    (Join-Path $PSScriptRoot 'dogan-webui\docker-compose.override.yml')
)
$Script:NetworkName = 't3-net'

function Show-Usage {
    Write-Host 'Usage:' -ForegroundColor Cyan
    Write-Host '  dogan rebuild               rebuild from latest code, keep data'
    Write-Host '  dogan rebuild remove-data   rebuild and delete all stack volumes'
}

function Invoke-Docker {
    param([string[]]$Arguments)
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) { throw "docker $($Arguments -join ' ') failed (exit $LASTEXITCODE)" }
}

function Invoke-Compose {
    param([string[]]$Arguments)
    $files = foreach ($file in $Script:ComposeFiles) { '-f'; $file }
    Invoke-Docker (@('compose', '-p', $Script:ProjectName) + $files + $Arguments)
}

function Invoke-Rebuild {
    param([bool]$RemoveData)

    if (-not (docker network ls --filter "name=^$($Script:NetworkName)$" --format '{{.Name}}')) {
        Invoke-Docker @('network', 'create', $Script:NetworkName)
    }

    if ($RemoveData) {
        Write-Host 'Removing stack and its volumes...' -ForegroundColor Yellow
        Invoke-Compose @('down', '-v', '--remove-orphans')
    }

    Write-Host 'Rebuilding images and recreating containers...' -ForegroundColor Cyan
    Invoke-Compose @('up', '-d', '--build', '--force-recreate', '--remove-orphans')
    Invoke-Compose @('ps')
}

try {
    if ($Command -ne 'rebuild') {
        Show-Usage
        exit 1
    }

    if ([string]::IsNullOrEmpty($Option)) {
        Invoke-Rebuild -RemoveData $false
    }
    elseif ($Option -eq 'remove-data') {
        Invoke-Rebuild -RemoveData $true
    }
    else {
        throw "Unknown option '$Option'."
    }
}
catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    Show-Usage
    exit 1
}
