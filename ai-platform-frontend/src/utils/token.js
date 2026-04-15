export function parseJwtPayload(token) {
  if (!token || typeof token !== 'string') {
    return null
  }

  const parts = token.split('.')
  if (parts.length < 2) {
    return null
  }

  try {
    const normalized = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized + '='.repeat((4 - (normalized.length % 4)) % 4)
    const json = decodeURIComponent(
      atob(padded)
        .split('')
        .map((ch) => `%${(`00${ch.charCodeAt(0).toString(16)}`).slice(-2)}`)
        .join('')
    )
    return JSON.parse(json)
  } catch (e) {
    return null
  }
}

export function isTokenExpired(token, skewSeconds = 30) {
  const payload = parseJwtPayload(token)
  if (!payload || !payload.exp) {
    return false
  }
  const nowSeconds = Math.floor(Date.now() / 1000)
  return payload.exp <= (nowSeconds + skewSeconds)
}
