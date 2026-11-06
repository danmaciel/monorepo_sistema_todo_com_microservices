/**
 * Client-side session freshness check only.
 * The server remains responsible for JWT signature and authorization validation.
 */
export function isJwtActive(token, nowMs = Date.now()) {
  if (typeof token !== 'string' || !Number.isFinite(nowMs)) return false

  const parts = token.split('.')
  if (parts.length !== 3 || !parts[1]) return false

  try {
    const encoded = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const claims = JSON.parse(atob(encoded))
    return (
      claims !== null &&
      typeof claims === 'object' &&
      Number.isSafeInteger(claims.exp) &&
      claims.exp > 0 &&
      claims.exp * 1000 > nowMs
    )
  } catch {
    return false
  }
}
