import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { api, currentSession, logout, query } from './api.js'
import { dateTime, Heading, Loading, Options, Pager, roles, SaveForm, useData } from './ui.jsx'

export function Users() {
  const [search, setSearch] = useSearchParams()
  const users = useData('/admin/users' + query({page: search.get('page')}))
  return <section>
    <Heading title="Пользователи"><Link className="button-link primary" to="/admin/users/new">Добавить пользователя</Link></Heading>
    <Loading resource={users} />
    {users.data && <div className="panel table-wrap"><table>
      <thead><tr><th>Пользователь</th><th>Роли</th><th>Доступ</th><th>Действия</th></tr></thead>
      <tbody>{users.data.content.map(user => {
        const path = '/admin/users/' + user.id
        return <tr key={user.id}>
          <td><strong>{user.displayName}</strong><small>{user.email}</small></td>
          <td>{user.roles.map(role => <span className="badge" key={role}>{roles[role]}</span>)}</td>
          <td>{user.enabled ? 'Активен' : 'Отключён'}{user.passwordChangeRequired && <small>Требуется смена пароля</small>}</td>
          <td className="row-actions"><Link to={path + '/edit'}>Изменить</Link><Link to={path + '/reset'}>Сбросить пароль</Link></td>
        </tr>
      })}</tbody>
    </table></div>}
    <Pager data={users.data} changePage={page => setSearch({page})} />
  </section>
}

export function UserForm() {
  const {id} = useParams()
  const navigate = useNavigate()
  const resource = useData(id ? '/admin/users/' + id : null)
  const user = resource.data

  async function save(form) {
    const body = {displayName: form.get('displayName'), email: form.get('email'), roles: form.getAll('roles')}
    if (body.roles.length === 0) throw new Error('Выберите хотя бы одну роль')
    if (user) {
      body.enabled = form.has('enabled')
      body.version = user.version
    } else {
      body.temporaryPassword = form.get('temporaryPassword')
    }
    await api(id ? '/admin/users/' + id : '/admin/users', {method: id ? 'PUT' : 'POST', body})
    navigate('/admin/users')
  }

  return <section>
    <Heading title={id ? 'Изменить пользователя' : 'Новый пользователь'} />
    <Loading resource={resource} />
    {(!id || user) && <SaveForm key={id || 'new'} onSave={save} cancel="/admin/users">
      <label>Имя<input name="displayName" required maxLength={160} defaultValue={user?.displayName} /></label>
      <label>Почта<input name="email" type="email" required maxLength={254} defaultValue={user?.email} /></label>
      {!user && <label>Временный пароль<input name="temporaryPassword" type="password" required
        minLength={12} maxLength={128} autoComplete="new-password" /></label>}
      <fieldset>
        <legend>Роли</legend>
        {Object.entries(roles).map(([role, label]) => <label key={role} className="checkbox">
          <input name="roles" type="checkbox" value={role} defaultChecked={user ? user.roles.includes(role) : role === 'VIEWER'} />{label}
        </label>)}
      </fieldset>
      {user && <label className="checkbox"><input type="checkbox" name="enabled" defaultChecked={user.enabled} />Учётная запись активна</label>}
    </SaveForm>}
  </section>
}

export function ResetPassword() {
  const {id} = useParams()
  const navigate = useNavigate()
  const user = useData('/admin/users/' + id)
  return <section>
    <Heading title={user.data ? 'Сброс пароля · ' + user.data.displayName : 'Сброс пароля'} />
    <Loading resource={user} />
    {user.data && <SaveForm label="Сбросить пароль" cancel="/admin/users" onSave={async form => {
      await api('/admin/users/' + id + '/reset-password', {method: 'POST', body: {temporaryPassword: form.get('temporaryPassword')}})
      if (id === currentSession()?.user.id) await logout()
      else navigate('/admin/users')
    }}>
      <p>При следующем входе пользователь должен сменить временный пароль.</p>
      <label>Временный пароль<input name="temporaryPassword" type="password" required
        minLength={12} maxLength={128} autoComplete="new-password" /></label>
    </SaveForm>}
  </section>
}

const auditSorts = {
  'occurred,desc': 'Новые сначала', 'occurred,asc': 'Старые сначала',
  'actor,asc': 'По пользователю', 'event,asc': 'По событию'
}

export function Audit() {
  const [search, setSearch] = useSearchParams()
  const audit = useData('/admin/audit?' + search.toString())
  return <section>
    <Heading title="Журнал действий" />
    <form key={search.toString()} className="panel search-row" onSubmit={event => {
      event.preventDefault()
      const form = new FormData(event.currentTarget)
      setSearch({q: form.get('q'), sort: form.get('sort')})
    }}>
      <input name="q" defaultValue={search.get('q') || ''} aria-label="Поиск" placeholder="Пользователь, событие или код запроса" />
      <select name="sort" aria-label="Порядок" defaultValue={search.get('sort') || 'occurred,desc'}><Options items={auditSorts} /></select>
      <button className="primary">Найти</button>
    </form>
    <Loading resource={audit} />
    {audit.data && <div className="panel table-wrap"><table>
      <thead><tr><th>Время</th><th>Пользователь</th><th>Событие</th><th>Описание</th><th>Код запроса</th></tr></thead>
      <tbody>{audit.data.content.map(event => <tr key={event.id}>
        <td>{dateTime(event.occurredAt)}</td><td>{event.actorEmail || 'Система'}</td>
        <td>{event.eventType}</td><td>{event.summary}</td><td>{event.correlationId}</td>
      </tr>)}</tbody>
    </table>{audit.data.content.length === 0 && <p className="empty">События не найдены</p>}</div>}
    <Pager data={audit.data} changePage={page => {
      const params = new URLSearchParams(search)
      params.set('page', page)
      setSearch(params)
    }} />
  </section>
}

