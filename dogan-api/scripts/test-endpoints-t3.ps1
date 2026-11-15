# Test every Dogan API endpoint on the T3 server (internal 127.0.0.1:6080
# and public https://dogan-api.xaigrok.ir). Read-only / non-destructive:
# mutating calls use device id dogan-test-device only; web settings are restored.
# Requires the SSH alias "t3" (BatchMode). Does not commit, redeploy, or print secrets.
#
# Usage (from anywhere, or from the repo):
#   powershell -File dogan-api/scripts/test-endpoints-t3.ps1
# Optional:
#   $env:DOGAN_SSH_HOST = 't3'
#   $env:DOGAN_REMOTE_OUT = '/tmp/dogan-endpoint-tests'

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$SshHost = if ($env:DOGAN_SSH_HOST) { $env:DOGAN_SSH_HOST } else { 't3' }
$RemoteOut = if ($env:DOGAN_REMOTE_OUT) { $env:DOGAN_REMOTE_OUT } else { '/tmp/dogan-endpoint-tests' }

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BashScript = Join-Path $ScriptDir 'test-endpoints-t3.sh'
$Helpers = Join-Path $ScriptDir 'helpers.py'

if (-not (Test-Path $BashScript)) { throw "Missing $BashScript" }
if (-not (Test-Path $Helpers)) { throw "Missing $Helpers" }

Write-Host "Uploading test scripts to ${SshHost}:${RemoteOut}" -ForegroundColor Cyan
ssh -o BatchMode=yes -o ConnectTimeout=15 $SshHost "mkdir -p $RemoteOut"
# Use scp in a way that works with the t3 SSH alias
scp -o BatchMode=yes -o ConnectTimeout=15 $BashScript $Helpers "${SshHost}:${RemoteOut}/"

Write-Host "Running endpoint tests on $SshHost ..." -ForegroundColor Cyan
ssh -o BatchMode=yes -o ConnectTimeout=15 $SshHost "chmod +x $RemoteOut/test-endpoints-t3.sh && OUT_DIR=$RemoteOut bash $RemoteOut/test-endpoints-t3.sh"
$sshExit = $LASTEXITCODE

$LocalOut = Join-Path $ScriptDir 't3-last-run'
New-Item -ItemType Directory -Force -Path $LocalOut | Out-Null
Write-Host "Copying report back to $LocalOut" -ForegroundColor Cyan
scp -o BatchMode=yes "${SshHost}:${RemoteOut}/report.md" (Join-Path $LocalOut 'report.md')
scp -o BatchMode=yes "${SshHost}:${RemoteOut}/results.tsv" (Join-Path $LocalOut 'results.tsv')
scp -o BatchMode=yes "${SshHost}:${RemoteOut}/CREDENTIALS.txt" (Join-Path $LocalOut 'CREDENTIALS.txt')
scp -o BatchMode=yes "${SshHost}:${RemoteOut}/dogan-api.logs" (Join-Path $LocalOut 'dogan-api.logs')

if ($sshExit -ne 0) {
    Write-Host "Remote test script exited with code $sshExit (report may still be useful)." -ForegroundColor Yellow
    exit $sshExit
}
Write-Host "Done. Report: $(Join-Path $LocalOut 'report.md')" -ForegroundColor Green
