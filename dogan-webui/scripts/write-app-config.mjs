import { writeFileSync } from 'node:fs'

const fromEnv =
  process.env.DOGAN_API_BASE_URL?.trim() ||
  process.env.VITE_API_BASE_URL?.trim() ||
  ''

const payload = {
  apiBaseUrl: fromEnv,
}

writeFileSync('public/app-config.json', `${JSON.stringify(payload, null, 2)}\n`)
