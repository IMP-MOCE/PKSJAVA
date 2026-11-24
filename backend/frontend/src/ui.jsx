import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api } from './api.js'

export const statuses = {
  CREATED: 'Создана', CONFIRMED: 'Подтверждена', COMPLETED: 'Завершена', CANCELLED: 'Отменена'
}
export const urgencies = {NORMAL: 'Обычная', SOON: 'Скоро', OVERDUE: 'Просрочена'}
export const roles = {VIEWER: 'Просмотр', EDITOR: 'Редактор', ADMIN: 'Администратор'}

export function dateTime(value) {
  return value ? new Date(value).toLocaleString('ru-RU', {timeZone: 'Europe/Moscow'}) : ''
}

export function dateText(value) {
  return value.split('-').reverse().join('.')
}

export function today() {
  return new Intl.DateTimeFormat('sv-SE', {timeZone: 'Europe/Moscow'}).format(new Date())
}

export function useData(path) {
  const [result, setResult] = useState(null)
  const [version, setVersion] = useState(0)
  useEffect(() => {
    if (!path) return
    const controller = new AbortController()
    api(path, {signal: controller.signal})
      .then(value => {
        if (!controller.signal.aborted) setResult({path, version, value})
      })
      .catch(error => {
        if (!controller.signal.aborted) setResult({path, version, error})
      })
    return () => controller.abort()
  }, [path, version])
  const current = result?.path === path && result?.version === version ? result : null
  return {
    data: current?.value, error: current?.error, loading: Boolean(path && !current),
    reload: () => setVersion(value => value + 1)
  }
}

export function ErrorMessage({error}) {
  return error ? <p className="form-error" role="alert">{error.message}</p> : null
}

export function Loading({resource}) {
  if (resource.error) return <ErrorMessage error={resource.error} />
  return resource.loading ? <p role="status">Загрузка…</p> : null
}

export function SaveForm({onSave, children, label = 'Сохранить', className = 'panel editor-form', cancel}) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState(null)

  async function submit(event) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setError(null)
    setPending(true)
    try {
      await onSave(form)
    } catch (error) {
      setError(error)
    } finally {
      setPending(false)
    }
  }

  return <form className={className} onSubmit={submit}>
    {children}
    <ErrorMessage error={error} />
    <div className="dialog-actions">
      {cancel && <Link className="button-link" to={cancel}>Отмена</Link>}
      <button className="primary" disabled={pending}>{pending ? 'Подождите…' : label}</button>
    </div>
  </form>
}

export function Heading({title, children}) {
  return <div className="page-heading"><h1>{title}</h1>{children}</div>
}

export function Pager({data, changePage}) {
  if (!data || data.totalPages === 0) return null
  return <div className="pagination" aria-label="Страницы">
    <span>Страница {data.page + 1} из {data.totalPages}</span>
    <button disabled={data.first} onClick={() => changePage(data.page - 1)}>Назад</button>
    <button disabled={data.last} onClick={() => changePage(data.page + 1)}>Далее</button>
  </div>
}

export function Options({items}) {
  return Object.entries(items).map(([value, title]) => <option key={value} value={value}>{title}</option>)
}

