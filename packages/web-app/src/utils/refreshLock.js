// Web Locks serialize cookie refresh across same-origin browser tabs.
// Never exchange access tokens, refresh secrets or CSRF values between tabs.
export async function withRefreshLock(action, locks = globalThis.navigator?.locks) {
  if (typeof action !== 'function') throw new TypeError('Refresh action is required')
  if (!locks || typeof locks.request !== 'function') {
    // Fail closed: independent tabs must not race to consume a one-use token.
    throw new Error('Browser refresh coordination unavailable')
  }
  return locks.request('todo-browser-refresh', { mode: 'exclusive' }, action)
}
