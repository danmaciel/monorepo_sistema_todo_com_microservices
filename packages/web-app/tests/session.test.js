import assert from 'node:assert/strict'
import test from 'node:test'
import { isJwtActive } from '../src/utils/session.js'

function tokenWithClaims(claims) {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url')
  const payload = Buffer.from(JSON.stringify(claims)).toString('base64url')
  return `${header}.${payload}.test-signature`
}

const now = Date.UTC(2026, 10, 5, 12, 0, 0)

test('accepts unexpired token for frontend display state', () => {
  assert.equal(isJwtActive(tokenWithClaims({ sub: 'user', exp: now / 1000 + 3600 }), now), true)
})

test('rejects expired token at the exact expiration instant', () => {
  assert.equal(isJwtActive(tokenWithClaims({ exp: now / 1000 }), now), false)
  assert.equal(isJwtActive(tokenWithClaims({ exp: now / 1000 - 1 }), now), false)
})

test('rejects missing or invalid expiration claims', () => {
  assert.equal(isJwtActive(tokenWithClaims({ sub: 'user' }), now), false)
  assert.equal(isJwtActive(tokenWithClaims({ exp: 'tomorrow' }), now), false)
  assert.equal(isJwtActive(tokenWithClaims({ exp: -100 }), now), false)
})

test('rejects malformed JWT without throwing', () => {
  for (const malformed of [null, undefined, '', 'not-a-token', 'a.b', 'a.!.c', 'a.e30=.c']) {
    assert.equal(isJwtActive(malformed, now), false)
  }
})
