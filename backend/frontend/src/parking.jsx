import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api } from './api.js'
import { dateTime, Heading, Loading, SaveForm, statuses, urgencies, useData } from './ui.jsx'

export function Employees({admin}) {
  const employees = useData('/employees')
  return <section>
    <Heading title="Сотрудники">{admin && <Link className="button-link primary" to="/employees/new">Добавить сотрудника</Link>}</Heading>
    <Loading resource={employees} />
    {employees.data && <div className="panel table-wrap"><table>
      <thead><tr><th>Имя</th><th>Почта</th><th>Доступ к бронированию</th>{admin && <th>Действия</th>}</tr></thead>
      <tbody>{employees.data.map(employee => <tr key={employee.id}>
        <td>{employee.name}</td><td>{employee.email}</td><td>{employee.active ? 'Разрешено' : 'Отключено'}</td>
        {admin && <td><Link to={'/employees/' + employee.id + '/edit'}>Изменить</Link></td>}
      </tr>)}</tbody>
    </table>{employees.data.length === 0 && <p className="empty">Сотрудников пока нет</p>}</div>}
  </section>
}

export function EmployeeForm() {
  const {id} = useParams()
  const navigate = useNavigate()
  const employees = useData(id ? '/employees' : null)
  const employee = employees.data?.find(item => item.id === id)

  async function save(form) {
    const body = {
      name: form.get('name'), email: form.get('email'), active: form.has('active'), version: employee?.version ?? 0
    }
    await api(id ? '/employees/' + id : '/employees', {method: id ? 'PUT' : 'POST', body})
    navigate('/employees')
  }

  return <section>
    <Heading title={id ? 'Изменить сотрудника' : 'Новый сотрудник'} />
    <Loading resource={employees} />
    {id && employees.data && !employee && <p role="alert">Сотрудник не найден</p>}
    {(!id || employee) && <SaveForm key={id || 'new'} cancel="/employees" onSave={save}>
      <label>Имя<input name="name" required maxLength={100} defaultValue={employee?.name} /></label>
      <label>Почта<input name="email" required type="email" maxLength={100} defaultValue={employee?.email} /></label>
      <label className="checkbox"><input type="checkbox" name="active" defaultChecked={employee?.active ?? true} />Может бронировать</label>
    </SaveForm>}
  </section>
}

function Breakdown({title, data, labels = {}}) {
  return <div className="panel">
    <h2>{title}</h2>
    {Object.entries(data).map(([key, value]) => <div className="stat-row" key={key}>
      <span>{labels[key] || key}</span><strong>{value}</strong>
    </div>)}
    {Object.keys(data).length === 0 && <p className="muted">Нет данных</p>}
  </div>
}

export function Analytics() {
  const resource = useData('/analytics/summary')
  const summary = resource.data
  const metrics = summary ? {
    'Всего бронирований': summary.total, 'Текущих записей': summary.active,
    'Действующих броней': summary.open, 'Свободно сегодня': summary.freeToday,
    'Занято сегодня': summary.occupiedToday, 'В архиве': summary.archived,
    'Гостевых визитов': summary.guests,
    'Средний номер места': summary.averageSpot.toLocaleString('ru-RU', {minimumFractionDigits: 1, maximumFractionDigits: 1})
  } : {}

  return <section>
    <Heading title="Обзор парковки"><button onClick={resource.reload}>Обновить</button></Heading>
    <Loading resource={resource} />
    {summary && <>
      <div className="stat-grid">{Object.entries(metrics).map(([label, value]) =>
        <div key={label} className="stat-card"><span>{label}</span><strong>{value}</strong></div>
      )}</div>
      <div className="analytics-grid">
        <Breakdown title="По статусам" data={summary.byStatus} labels={statuses} />
        <Breakdown title="По срочности" data={summary.byUrgency} labels={urgencies} />
        <Breakdown title="По сотрудникам" data={summary.byEmployee} />
      </div>
    </>}
  </section>
}

function ImportErrors({errors}) {
  return errors.length > 0 && <ul>{errors.map(item => <li key={item.row}>Строка {item.row}: {item.message}</li>)}</ul>
}

export function ImportCsv() {
  const [preview, setPreview] = useState(null)
  const [applied, setApplied] = useState(null)

  async function check(form) {
    setPreview(null)
    setApplied(null)
    const result = await api('/bookings/import/preview', {method: 'POST', body: form})
    setPreview(result)
  }

  async function apply() {
    const result = await api('/bookings/import/apply', {method: 'POST', body: {previewId: preview.previewId}})
    setPreview(null)
    setApplied(result)
  }

  return <section>
    <Heading title="Импорт CSV" />
    <div className="panel import-panel">
      <p>CSV в UTF-8 с запятой в качестве разделителя. Теги разделяются символом |. До 1000 строк и 5 МБ.</p>
      <p className="code-example">code,employeeEmail,spot,date,status,purpose,guest,tags</p>
      <SaveForm className="" onSave={check} label="Проверить файл">
        <label>CSV-файл<input name="file" type="file" accept=".csv,text/csv" required
          onChange={() => { setPreview(null); setApplied(null) }} /></label>
        <label>Режим<select name="mode" onChange={() => { setPreview(null); setApplied(null) }}>
          <option value="NEW_ONLY">Только новые брони</option>
          <option value="UPSERT">Добавить и обновить по номеру брони</option>
        </select></label>
      </SaveForm>
      {preview && <div className="notice" role="status">
        <p>Корректных строк: {preview.validRows}, ошибок: {preview.invalidRows}</p>
        <p className="hint">Проверка действует до {dateTime(preview.expiresAt)}</p>
        <ImportErrors errors={preview.errors} />
        {preview.validRows > 0 && <SaveForm className="" onSave={apply} label="Применить корректные строки" />}
      </div>}
      {applied && <div className="notice" role="status">
        <p>Импорт завершён. Сохранено: {applied.successful}</p>
        <ImportErrors errors={applied.errors} />
      </div>}
    </div>
  </section>
}

