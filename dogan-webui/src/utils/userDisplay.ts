import type { User } from '@/types/api'

export function splitDisplayName(displayName: string, username: string): { firstName: string; lastName: string } {
  const source = displayName.trim() || username.trim()
  const parts = source.split(/\s+/).filter(Boolean)
  if (parts.length >= 2) {
    return { firstName: parts[0], lastName: parts.slice(1).join(' ') }
  }
  return { firstName: parts[0] ?? username, lastName: '' }
}

export function profileInitials(firstName: string, lastName: string, username: string): string {
  const first = firstName.trim()
  const last = lastName.trim()
  if (first && last) {
    return `${first[0]}${last[0]}`.toUpperCase()
  }
  const words = `${first} ${last}`.trim().split(/\s+/).filter(Boolean)
  if (words.length >= 2) {
    return `${words[0][0]}${words[1][0]}`.toUpperCase()
  }
  const fallback = (username || first || '?').trim()
  return fallback.slice(0, 2).toUpperCase()
}

export function formatUserDisplayName(user: User | null | undefined): string {
  if (!user) {
    return 'Signed in'
  }
  const { firstName, lastName } = splitDisplayName(user.name, user.email)
  const full = [firstName, lastName].filter(Boolean).join(' ')
  return full || user.name || user.email
}

export function userInitials(user: User | null | undefined): string {
  if (!user) {
    return '?'
  }
  const { firstName, lastName } = splitDisplayName(user.name, user.email)
  return profileInitials(firstName, lastName, user.email)
}
