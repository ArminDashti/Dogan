Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$BaseUrl = if ($env:DOGAN_BASE_URL) { $env:DOGAN_BASE_URL } else { 'http://localhost:8080/dogan/api/v1' }
$EmbeddedToken = if ($env:DOGAN_EMBEDDED_API_TOKEN) {
    $env:DOGAN_EMBEDDED_API_TOKEN
} else {
    'dg_dev_a8f3c2e1b9d74f6a0e5c3b9d2f7a1e4c8b6d0f3a7e2c9b5d1f8a4e6c0b3d7f9'
}

$username = 'admin'
$password = 'admin'

function Invoke-DoganRequest {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = @{},
        [object]$Body = $null,
        [switch]$RawResponse
    )

    $uri = "$BaseUrl$Path"
    $params = @{
        Method            = $Method
        Uri               = $uri
        Headers           = $Headers
        UseBasicParsing   = $true
        ErrorAction       = 'Stop'
    }

    if ($null -ne $Body) {
        $params['Body'] = ($Body | ConvertTo-Json -Depth 10)
        $params['ContentType'] = 'application/json'
    }

    if ($RawResponse) {
        return Invoke-WebRequest @params
    }

    if ($Method -eq 'GET' -or $Method -eq 'PUT') {
        return Invoke-RestMethod @params
    }

    return Invoke-RestMethod @params
}

Write-Host "Testing dogan-server at $BaseUrl" -ForegroundColor Cyan

Write-Host '[1/15] GET /health'
Invoke-DoganRequest -Method GET -Path '/health' | Out-Null

Write-Host '[2/15] POST /auth (admin)'
$authJson = Invoke-DoganRequest -Method POST -Path '/auth' -Body @{
    username = $username
    password = $password
}
$jwtToken = $authJson.token

$authHeaders = @{
    Authorization = "Bearer $jwtToken"
}
$deviceHeaders = @{
    Authorization = "Bearer $EmbeddedToken"
}

Write-Host '[3/15] POST /auth (device api_key)'
$deviceAuthJson = Invoke-DoganRequest -Method POST -Path '/auth' -Body @{
    api_key = 'dogan-dev-key'
}
if ($deviceAuthJson.token -ne $EmbeddedToken) {
    throw 'Expected /auth with api_key to return embedded API token'
}

Write-Host '[4/15] POST /frame'
$tinyJpegBase64 = '/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//2wBDAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQH/wAARCAABAAEDAREAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAb/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCdABmX/9k='
Invoke-DoganRequest -Method POST -Path '/frame' -Headers $deviceHeaders -Body @{
    device_id  = 'test-device'
    image_data = $tinyJpegBase64
} | Out-Null

Write-Host '[5/15] GET /last-frame'
Invoke-DoganRequest -Method GET -Path '/last-frame?device-id=test-device' -Headers $authHeaders | Out-Null

Write-Host '[6/15] GET /frame/image'
$imageResponse = Invoke-DoganRequest -Method GET -Path '/frame/image?device-id=test-device' -Headers $authHeaders -RawResponse
if ($imageResponse.Headers['Content-Type'] -notmatch 'image') {
    throw 'Expected image content type from /frame/image'
}

Write-Host '[7/15] POST /device-metrics'
Invoke-DoganRequest -Method POST -Path '/device-metrics' -Headers $deviceHeaders -Body @{
    device_id             = 'test-device'
    battery_level_percent = 87.5
    signal_strength_dbm   = -65
    network_type          = 'wifi'
    temperature_celsius   = 38.2
    latitude              = 35.6892
    longitude             = 51.3890
} | Out-Null

Write-Host '[8/15] GET /device-metrics'
Invoke-DoganRequest -Method GET -Path '/device-metrics?device-id=test-device' -Headers $authHeaders | Out-Null

Write-Host '[9/15] POST /actions'
$actionJson = Invoke-DoganRequest -Method POST -Path '/actions' -Headers $authHeaders -Body @{
    device_id   = 'test-device'
    action_type = 'ping'
    payload     = @{ message = 'hello from test script' }
}
$actionId = $actionJson.id

Write-Host '[10/15] GET /actions/pending'
Invoke-DoganRequest -Method GET -Path '/actions/pending?device-id=test-device' -Headers $deviceHeaders | Out-Null

Write-Host '[11/15] PUT /settings and GET /settings'
Invoke-DoganRequest -Method PUT -Path '/settings' -Headers $authHeaders -Body @{
    platform = 'web'
    key      = 'theme'
    value    = 'dark'
} | Out-Null
Invoke-DoganRequest -Method GET -Path '/settings?platform=web' -Headers $authHeaders | Out-Null

Write-Host '[12/15] POST /streaming/token'
Invoke-DoganRequest -Method POST -Path '/streaming/token' -Headers $authHeaders -Body @{
    device_id = 'test-device'
    role      = 'subscriber'
} | Out-Null

Write-Host '[13/15] POST /webrtc/session'
Invoke-DoganRequest -Method POST -Path '/webrtc/session' -Headers $deviceHeaders -Body @{
    device_id = 'test-device'
} | Out-Null

Write-Host '[14/15] GET /devices and GET /devices/:id/stream'
Invoke-DoganRequest -Method GET -Path '/devices' -Headers $authHeaders | Out-Null
Invoke-DoganRequest -Method GET -Path '/devices/test-device/stream' -Headers $authHeaders | Out-Null

Write-Host '[15/15] GET /webrtc/connections'
Invoke-DoganRequest -Method GET -Path '/webrtc/connections?device-id=test-device' -Headers $authHeaders | Out-Null

Write-Host "Acknowledging action $actionId"
Invoke-DoganRequest -Method PUT -Path "/actions/$actionId/ack" -Headers $deviceHeaders -Body @{
    status = 'done'
} | Out-Null

Write-Host ''
Write-Host 'All dummy-data tests passed.' -ForegroundColor Green
