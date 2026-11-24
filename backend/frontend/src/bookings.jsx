import { useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { api, query } from './api.js'
import { dateText, dateTime, ErrorMessage, Heading, Loading, Options, Pager, SaveForm, statuses, today, urgencies, useData } from './ui.jsx'

const bookingSorts = {
  'date,desc': 'Дата: новые сначала', 'date,asc': 'Дата: старые сначала',
  'spot,asc': 'Номер места', 'code,asc': 'Номер брони',
  'employee,asc': 'Сотрудник', 'status,asc': 'Статус'
}

function BookingFilters({search, employees, onSearch, onExport}) {
  const advanced = ['tag', 'spot', 'urgency', 'guest'].some(key => search.get(key)) || search.get('archived') === 'true'
  return <form className="panel filters" onSubmit={event => {
    event.preventDefault()
    onSearch(new FormData(event.currentTarget))
  }}>
    <label className="wide">Поиск<input name="q" defaultValue={search.get('q') || ''} placeholder="Номер, имя, почта или цель" /></label>
    <label>Сотрудник<select name="employeeId" defaultValue={search.get('employeeId') || ''}>
      <option value="">Все сотрудники</option>
      {employees.map(employee => <option key={employee.id} value={employee.id}>{employee.name}</option>)}
    </select></label>
    <label>Статус<select name="status" defaultValue={search.get('status') || ''}>
      <option value="">Все статусы</option><Options items={statuses} />
    </select></label>
    <label>Период с<input type="date" name="from" defaultValue={search.get('from') || ''} /></label>
    <label>По<input type="date" name="to" defaultValue={search.get('to') || ''} /></label>
    <details className="advanced" open={advanced}>
      <summary>Дополнительные фильтры</summary>
      <div className="filter-grid">
        <label>Записи<select name="archived" defaultValue={search.get('archived') || 'false'}>
          <option value="false">Текущие</option><option value="true">Архив</option>
        </select></label>
        <label>Гостевой визит<select name="guest" defaultValue={search.get('guest') || ''}>
          <option value="">Любой</option><option value="true">Да</option><option value="false">Нет</option>
        </select></label>
        <label>Срочность<select name="urgency" defaultValue={search.get('urgency') || ''}>
          <option value="">Любая</option><Options items={urgencies} />
        </select></label>
        <label>Тег<input name="tag" maxLength={50} defaultValue={search.get('tag') || ''} /></label>
        <label>Сравнение<select name="spotOp" defaultValue={search.get('spotOp') || 'eq'}>
          <Options items={{eq: 'Равно', gt: 'Больше', gte: 'Не меньше', lt: 'Меньше', lte: 'Не больше'}} />
        </select></label>
        <label>Номер места<input name="spot" type="number" min="1" max="50" defaultValue={search.get('spot') || ''} /></label>
      </div>
    </details>
    <label className="wide">Сортировка<select name="sort" defaultValue={search.get('sort') || 'date,desc'}>
      <Options items={bookingSorts} />
    </select></label>
    <label>На странице<select name="size" defaultValue={search.get('size') || '25'}>
      {[10, 25, 50, 100].map(size => <option key={size} value={size}>{size}</option>)}
    </select></label>
    <div className="filter-actions">
      <button className="primary">Найти</button>
      <Link className="button-link" to="/bookings">Сбросить</Link>
      <button type="button" onClick={onExport}>Экспорт CSV</button>
    </div>
  </form>
}

function BookingTable({items, editor, admin, onChange, onBulkResult}) {
  const [selected, setSelected] = useState([])
  const [action, setAction] = useState('ARCHIVE')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState(null)

  async function archive(booking) {
    setError(null)
    setPending(true)
    try {
      if (booking.archived) {
        await api('/bookings/' + booking.id + '/restore', {method: 'POST', body: {version: booking.version}})
      } else {
        await api('/bookings/' + booking.id + query({version: booking.version}), {method: 'DELETE'})
      }
      onChange(booking.archived ? 'Бронь восстановлена' : 'Бронь в архиве')
    } catch (error) {
      setError(error)
    } finally {
      setPending(false)
    }
  }

  async function bulk(form) {
    const body = {ids: selected, action}
    if (action === 'STATUS') body.status = form.get('status')
    if (action === 'ADD_TAG' || action === 'REMOVE_TAG') body.tag = form.get('tag')
    const result = await api('/bookings/bulk', {method: 'POST', body})
    onBulkResult(result)
  }

  if (items.length === 0) return <p className="panel empty">Бронирований нет</p>
  return <>
    {editor && <form className="panel bulk-bar" onSubmit={async event => {
      event.preventDefault()
      if (!selected.length || pending) return
      setError(null)
      setPending(true)
      try { await bulk(new FormData(event.currentTarget)) }
      catch (error) { setError(error) }
      finally { setPending(false) }
    }}>
      <label>Действие с выбранными<select value={action} onChange={event => setAction(event.target.value)}>
        <Options items={{ARCHIVE: 'В архив', RESTORE: 'Восстановить', STATUS: 'Изменить статус', ADD_TAG: 'Добавить тег', REMOVE_TAG: 'Убрать тег'}} />
      </select></label>
      {action === 'STATUS' && <label>Новый статус<select name="status" required>
        <option value="">Выберите статус</option><Options items={statuses} />
      </select></label>}
      {['ADD_TAG', 'REMOVE_TAG'].includes(action) && <label>Тег<input name="tag" maxLength={50} required /></label>}
      <span>Выбрано: {selected.length}</span>
      <button disabled={pending || selected.length === 0}>Применить</button>
    </form>}
    <ErrorMessage error={error} />
    <div className="panel table-wrap"><table>
      <thead><tr>{editor && <th>
        <input type="checkbox" aria-label="Выбрать все брони" checked={selected.length === items.length}
          onChange={event => setSelected(event.target.checked ? items.map(item => item.id) : [])} />
      </th>}<th>Бронь / сотрудник</th><th>Место</th><th>Дата</th><th>Статус</th><th>Срочность</th><th>Действия</th></tr></thead>
      <tbody>{items.map(booking => {
        const path = '/bookings/' + booking.id
        return <tr key={booking.id}>
          {editor && <td><input type="checkbox" aria-label={'Выбрать ' + booking.code} checked={selected.includes(booking.id)}
            onChange={event => setSelected(event.target.checked ? [...selected, booking.id] : selected.filter(id => id !== booking.id))} /></td>}
          <td><strong>{booking.code}</strong><small>{booking.employeeName}{booking.guest ? ' · гость' : ''}</small>
            <small>{booking.purpose}</small>{booking.tags.length > 0 && <small>{booking.tags.join(', ')}</small>}</td>
          <td>{booking.spot}</td><td>{dateText(booking.date)}</td>
          <td><span className={'badge ' + booking.status}>{statuses[booking.status]}</span></td>
          <td><span className={'badge ' + booking.urgency}>{urgencies[booking.urgency]}</span></td>
          <td className="row-actions">
            {editor && !booking.archived && ['CREATED', 'CONFIRMED'].includes(booking.status) && <Link to={path + '/edit'}>Изменить</Link>}
            <Link to={path + '/notes'}>Заметки</Link>
            {editor && <button disabled={pending} onClick={() => archive(booking)}>{booking.archived ? 'Восстановить' : 'В архив'}</button>}
            {admin && booking.archived && <Link className="danger" to={path + '/permanent'}>Удалить навсегда</Link>}
          </td>
        </tr>
      })}</tbody>
    </table></div>
  </>
}

export function Bookings({editor, admin}) {
  const [search, setSearch] = useSearchParams()
  const [message, setMessage] = useState('')
  const [bulkResult, setBulkResult] = useState(null)
  const [error, setError] = useState(null)
  const employees = useData('/employees')
  const bookings = useData('/bookings?' + search.toString())

  async function exportCsv() {
    setError(null)
    try {
      const params = new URLSearchParams(search)
      params.delete('page')
      params.delete('size')
      const blob = await api('/bookings/export?' + params, {download: true})
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = 'parking.csv'
      link.click()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (error) {
      setError(error)
    }
  }

  return <section>
    <Heading title="Бронирования">{editor && <Link className="button-link primary" to="/bookings/new">Новая бронь</Link>}</Heading>
    <Loading resource={employees} />
    {employees.data && <BookingFilters key={search.toString()} search={search} employees={employees.data} onExport={exportCsv}
      onSearch={form => {
        const filters = [...form].filter(([, value]) => value !== '')
        setMessage('')
        setBulkResult(null)
        setSearch(filters)
      }} />}
    <ErrorMessage error={error} />
    {message && <p className="notice" role="status">{message}</p>}
    {bulkResult && <div className="notice" role="status">
      Успешно: {bulkResult.successful.length}, ошибок: {bulkResult.errors.length}
      {bulkResult.errors.length > 0 && <ul>{bulkResult.errors.map(item => <li key={item.id}>{item.id}: {item.message}</li>)}</ul>}
    </div>}
    <Loading resource={bookings} />
    {bookings.data && <>
      <p className="table-toolbar">Всего записей: {bookings.data.totalElements}</p>
      <BookingTable items={bookings.data.content} editor={editor} admin={admin} onChange={message => {
        setMessage(message)
        setBulkResult(null)
        bookings.reload()
      }} onBulkResult={result => {
        setMessage('')
        setBulkResult(result)
        bookings.reload()
      }} />
      <Pager data={bookings.data} changePage={page => {
        const params = new URLSearchParams(search)
        params.set('page', page)
        setSearch(params)
      }} />
    </>}
  </section>
}

export function BookingForm() {
  const {id} = useParams()
  const navigate = useNavigate()
  const employees = useData('/employees')
  const resource = useData(id ? '/bookings/' + id : null)
  const [newCode] = useState(() => 'P-' + crypto.randomUUID().slice(0, 8).toUpperCase())
  const booking = resource.data
  const options = {
    CREATED: ['CREATED', 'CONFIRMED', 'CANCELLED'],
    CONFIRMED: ['CONFIRMED', 'COMPLETED', 'CANCELLED']
  }

  async function save(form) {
    const body = {
      code: form.get('code'), employeeId: form.get('employeeId'),
      spot: Number(form.get('spot')), date: form.get('date'), status: form.get('status'),
      purpose: form.get('purpose'), guest: form.has('guest'),
      tags: form.get('tags').split(',').map(tag => tag.trim()).filter(Boolean), version: booking?.version ?? 0
    }
    await api(id ? '/bookings/' + id : '/bookings', {method: id ? 'PUT' : 'POST', body})
    navigate('/bookings')
  }

  if (booking && (booking.archived || !options[booking.status])) {
    return <><h1>Эту бронь нельзя редактировать</h1><Link to="/bookings">К бронированиям</Link></>
  }
  return <section>
    <h1>{id ? 'Изменить бронь' : 'Новая бронь'}</h1>
    <Loading resource={employees} /><Loading resource={resource} />
    {employees.data && (!id || booking) && <SaveForm key={id || 'new'} onSave={save} cancel="/bookings">
      <div className="form-grid">
        <label>Номер брони<input name="code" required maxLength={100} defaultValue={booking?.code || newCode} /></label>
        <label>Сотрудник<select name="employeeId" required defaultValue={booking?.employeeId || ''}>
          <option value="">Выберите сотрудника</option>
          {employees.data.filter(employee => employee.active || employee.id === booking?.employeeId).map(employee =>
            <option key={employee.id} value={employee.id}>{employee.name} · {employee.email}</option>)}
        </select></label>
        <label>Место<input name="spot" type="number" min="1" max="50" required defaultValue={booking?.spot} /></label>
        <label>Дата<input name="date" type="date" required defaultValue={booking?.date || today()} /></label>
        <label>Статус<select name="status" required defaultValue={booking?.status || 'CREATED'}>
          {(options[booking?.status] || ['CREATED']).map(status => <option key={status} value={status}>{statuses[status]}</option>)}
        </select></label>
        <label>Цель<input name="purpose" required maxLength={200} defaultValue={booking?.purpose || 'Рабочий день'} /></label>
        <label className="wide">Теги через запятую<input name="tags" defaultValue={booking?.tags.join(', ')} /></label>
        <label className="checkbox"><input type="checkbox" name="guest" defaultChecked={booking?.guest} />Гостевой визит</label>
      </div>
    </SaveForm>}
  </section>
}

export function DeleteBooking() {
  const {id} = useParams()
  const navigate = useNavigate()
  const booking = useData('/bookings/' + id)
  return <section>
    <Heading title="Удалить бронь" />
    <Loading resource={booking} />
    {booking.data && <SaveForm label="Удалить навсегда" cancel="/bookings?archived=true" onSave={async () => {
      await api('/bookings/' + id + '/permanent' + query({version: booking.data.version}), {method: 'DELETE'})
      navigate('/bookings?archived=true')
    }}>
      <p>Бронь {booking.data.code} и её заметки будут удалены без возможности восстановления.</p>
    </SaveForm>}
  </section>
}

export function Notes({user, editor, admin}) {
  const {id} = useParams()
  const path = '/bookings/' + id
  const booking = useData(path)
  const notes = useData(path + '/notes')
  const [editing, setEditing] = useState(null)
  const [error, setError] = useState(null)
  const [pending, setPending] = useState(false)
  const [formNumber, setFormNumber] = useState(0)

  async function save(form) {
    const body = {body: form.get('body'), pinned: form.has('pinned'), version: editing?.version ?? 0}
    const notePath = path + '/notes' + (editing ? '/' + editing.id : '')
    await api(notePath, {method: editing ? 'PUT' : 'POST', body})
    setEditing(null)
    setFormNumber(value => value + 1)
    notes.reload()
  }

  async function remove(note) {
    if (!window.confirm('Удалить заметку?')) return
    setError(null)
    setPending(true)
    try {
      await api(path + '/notes/' + note.id, {method: 'DELETE'})
      if (editing?.id === note.id) setEditing(null)
      notes.reload()
    } catch (error) {
      setError(error)
    } finally {
      setPending(false)
    }
  }

  return <section>
    <Heading title={booking.data ? 'Заметки · ' + booking.data.code : 'Заметки'}>
      <Link className="button-link" to={booking.data?.archived ? '/bookings?archived=true' : '/bookings'}>К бронированиям</Link>
    </Heading>
    <Loading resource={booking} /><Loading resource={notes} /><ErrorMessage error={error} />
    {notes.data?.length === 0 && <p className="muted">Заметок пока нет</p>}
    {notes.data?.map(note => <article key={note.id} className={note.pinned ? 'note pinned' : 'note'}>
      <small>{note.authorName} · {dateTime(note.createdAt)}{note.pinned ? ' · закреплена' : ''}</small>
      <p>{note.body}</p>
      {editor && (admin || user.id === note.authorId) && <div className="row-actions">
        <button disabled={pending} onClick={() => setEditing(note)}>Изменить</button>
        <button disabled={pending} className="danger" onClick={() => remove(note)}>Удалить</button>
      </div>}
    </article>)}
    {editor && booking.data && <SaveForm key={editing?.id || formNumber} label="Сохранить заметку" onSave={save}>
      <h2>{editing ? 'Изменить заметку' : 'Новая заметка'}</h2>
      <label>Текст<textarea name="body" required maxLength={4000} rows={5} defaultValue={editing?.body} /></label>
      <label className="checkbox"><input type="checkbox" name="pinned" defaultChecked={editing?.pinned} />Закрепить</label>
      {editing && <button type="button" onClick={() => setEditing(null)}>Отмена</button>}
    </SaveForm>}
  </section>
}

