const TEHRAN_TZ = 'Asia/Tehran'

function tehranParts(date: Date): { year: string; month: string; day: string; hour: string; minute: string; second: string } {
  const formatter = new Intl.DateTimeFormat('en-GB', {
    timeZone: TEHRAN_TZ,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
  const parts = formatter.formatToParts(date)
  const lookup = (type: Intl.DateTimeFormatPartTypes): string =>
    parts.find((part) => part.type === type)?.value ?? '00'
  return {
    year: lookup('year'),
    month: lookup('month'),
    day: lookup('day'),
    hour: lookup('hour'),
    minute: lookup('minute'),
    second: lookup('second'),
  }
}

export function formatTehranDateTime(value: string | Date | undefined): string {
  if (!value) {
    return '—'
  }
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) {
    return typeof value === 'string' ? value : '—'
  }
  const parts = tehranParts(date)
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`
}

/** Calendar day in Tehran as YYYY-MM-DD. */
export function tehranDateKey(value: string | Date): string {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const parts = tehranParts(date)
  return `${parts.year}-${parts.month}-${parts.day}`
}

/** Hours since Tehran midnight, 0–24, used as the chart x axis. */
export function tehranHourOfDay(value: string | Date): number {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) {
    return 0
  }
  const parts = tehranParts(date)
  return Number(parts.hour) + Number(parts.minute) / 60 + Number(parts.second) / 3600
}

export function tehranTodayKey(): string {
  return tehranDateKey(new Date())
}

export function tehranYesterdayKey(): string {
  return tehranDateKey(new Date(Date.now() - 24 * 60 * 60 * 1000))
}

/** Short label such as "26 Nov" for a Tehran calendar day. */
export function formatTehranDayLabel(value: string): string {
  const date = new Date(`${value}T12:00:00Z`)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: TEHRAN_TZ,
    day: '2-digit',
    month: 'short',
  }).format(date)
}