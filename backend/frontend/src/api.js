let session = null
let refreshRequest = null
const listeners = new Set()

function updateSession(next) {
  session = next
  for (const listener of listeners) listener()
}

export function currentSession() {
  return session
}

export function subscribeToSession(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

async function request(path, {method = 'GET', body, signal, download = false, token} = {}) {
  const headers = new Headers()
  if (token) headers.set('Authorization', 'Bearer ' + token)
  if (body != null && !(body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
    body = JSON.stringify(body)
  }

  const response = await fetch('/api' + path, {method, headers, body, signal})
  if (!response.ok) {
    const details = await response.json().catch(() => ({}))
    const message = details.message || 'Не удалось выполнить запрос (' + response.status + ')'
    const fieldErrors = Object.values(details.validationErrors || {})
    const error = new Error([message, ...new Set(fieldErrors)].join('. '))
    error.status = response.status
    error.code = details.code
    throw error
  }
  if (response.status === 204) return null
  return download ? response.blob() : response.json()
}

export function refreshSession() {
  if (!refreshRequest) {
    const refresh = async () => {
      try {
        const next = await request('/auth/refresh', {method: 'POST'})
        updateSession(next)
        return next
      } catch (error) {
        if (error.status === 401) updateSession(null)
        throw error
      }
    }
    // одна вкладка за раз обновляет общий refresh cookie
    const locks = navigator.locks
    refreshRequest = (locks ? locks.request('parking-refresh', refresh) : refresh())
      .finally(() => { refreshRequest = null })
  }
  return refreshRequest
}

export async function login(email, password) {
  const next = await request('/auth/login', {method: 'POST', body: {email, password, clientType: 'WEB'}})
  updateSession(next)
}

export async function logout() {
  if (refreshRequest) await refreshRequest.catch(() => {})
  await request('/auth/logout', {method: 'POST'})
  updateSession(null)
}

export async function api(path, options = {}) {
  const token = session?.accessToken
  try {
    return await request(path, {...options, token})
  } catch (error) {
    if (error.code === 'UNAUTHORIZED') {
      if (!session || session.accessToken === token) await refreshSession()
      return request(path, {...options, token: session?.accessToken})
    }
    if (error.code === 'PASSWORD_CHANGE_REQUIRED' && session) {
      updateSession({...session, user: {...session.user, passwordChangeRequired: true}})
    }
    throw error
  }
}

export function query(values) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(values)) {
    if (value != null && value !== '') params.set(key, value)
  }
  return '?' + params.toString()
}

