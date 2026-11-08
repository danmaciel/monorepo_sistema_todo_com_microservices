import assert from 'node:assert/strict'
import test from 'node:test'
import { withRefreshLock } from '../src/utils/refreshLock.js'

test('serializes refresh and retains the lock until the full async transaction finishes', async () => {
  let tail = Promise.resolve()
  const locks = {
    request(_name, options, action) {
      assert.equal(_name, 'todo-browser-refresh')
      assert.equal(options.mode, 'exclusive')
      const result = tail.then(action)
      tail = result.catch(() => {})
      return result
    },
  }
  let releaseFirst
  const barrier = new Promise((resolve) => { releaseFirst = resolve })
  const events = []
  const first = withRefreshLock(async () => {
    events.push('first-start')
    await barrier
    events.push('first-end')
  }, locks)
  const second = withRefreshLock(async () => {
    events.push('second-start')
  }, locks)
  await Promise.resolve()
  await Promise.resolve()
  assert.deepEqual(events, ['first-start'])
  releaseFirst()
  await Promise.all([first, second])
  assert.deepEqual(events, ['first-start', 'first-end', 'second-start'])
})

test('refuses refresh rather than racing if Web Locks is unavailable', async () => {
  let invoked = false
  await assert.rejects(withRefreshLock(() => { invoked = true }, null), /coordination unavailable/)
  assert.equal(invoked, false)
})
