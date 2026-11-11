import http from 'node:http'

const port = 8080
const user = {
  id: 'u1',
  email: 'admin@local.local',
  name: 'Admin Operator',
}

const devices = [
  {
    id: 'dev-1',
    name: 'Lot Cam A',
    location: 'Bay 12',
    status: 'online',
  },
]

const telemetry = {
  device_id: 'dev-1',
  battery_percent: 84,
  battery_temperature_celsius: 31.2,
  noise_db: 42.5,
  jolt: 0.2,
  signal_strength: -67,
  network_type: 'lte',
  server_phone_latency_ms: 48,
  server_web_latency_ms: 22,
  recorded_at: new Date().toISOString(),
}

function readJson(req) {
  return new Promise((resolve) => {
    let body = ''
    req.on('data', (chunk) => {
      body += chunk
    })
    req.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {})
      } catch {
        resolve({})
      }
    })
  })
}

function send(res, status, payload) {
  const body = status === 204 ? '' : JSON.stringify(payload)
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Authorization, Content-Type',
    'Access-Control-Allow-Methods': 'GET,POST,PATCH,OPTIONS',
  })
  res.end(body)
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', `http://127.0.0.1:${port}`)
  const path = url.pathname.replace(/\/+$/, '') || '/'

  if (req.method === 'OPTIONS') {
    send(res, 204, {})
    return
  }

  if (req.method === 'POST' && path.endsWith('/auth/login')) {
    await readJson(req)
    send(res, 200, { token: 'mock-token', user })
    return
  }

  if (req.method === 'POST' && path.endsWith('/auth/logout')) {
    send(res, 204, {})
    return
  }

  if (req.method === 'GET' && path.endsWith('/auth/me')) {
    send(res, 200, user)
    return
  }

  if (req.method === 'GET' && path.endsWith('/devices')) {
    send(res, 200, devices)
    return
  }

  if (req.method === 'GET' && /\/devices\/[^/]+\/telemetry$/.test(path)) {
    send(res, 200, telemetry)
    return
  }

  if (req.method === 'GET' && /\/devices\/[^/]+\/metrics$/.test(path)) {
    send(res, 200, {
      device_id: 'dev-1',
      device_name: 'Lot Cam A',
      current: {
        temperature_celsius: 31.2,
        noise_db: 42.5,
        recorded_at: new Date().toISOString(),
      },
      history: Array.from({ length: 12 }, (_, i) => ({
        timestamp: new Date(Date.now() - (11 - i) * 600000).toISOString(),
        temperature_celsius: 28 + (i % 5),
        noise_db: 35 + i * 2,
      })),
    })
    return
  }

  if (req.method === 'GET' && path.endsWith('/images')) {
    send(res, 200, [])
    return
  }

  if (req.method === 'GET' && path.endsWith('/settings')) {
    send(res, 200, {
      notifications_enabled: true,
      temperature_unit: 'celsius',
      noise_alert_threshold_db: 70,
      default_device_id: 'dev-1',
    })
    return
  }

  if (req.method === 'PATCH' && path.endsWith('/settings')) {
    const body = await readJson(req)
    send(res, 200, {
      notifications_enabled: body.notifications_enabled ?? true,
      temperature_unit: body.temperature_unit ?? 'celsius',
      noise_alert_threshold_db: body.noise_alert_threshold_db ?? 70,
      default_device_id: body.default_device_id ?? 'dev-1',
    })
    return
  }

  send(res, 404, { error: `no mock for ${req.method} ${path}` })
})

server.listen(port, '127.0.0.1', () => {
  console.log(`mock api on http://127.0.0.1:${port}`)
})
