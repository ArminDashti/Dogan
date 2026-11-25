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