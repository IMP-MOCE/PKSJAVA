import { useEffect, useState, useSyncExternalStore } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Link, NavLink, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router'
import { api, currentSession, login, logout, refreshSession, subscribeToSession } from './api.js'
import { ErrorMessage, roles, SaveForm } from './ui.jsx'
import { Bookings, BookingForm, DeleteBooking, Notes } from './bookings.jsx'
import { Analytics, Employees, EmployeeForm, ImportCsv } from './parking.jsx'
import { Audit, Users, UserForm, ResetPassword } from './admin.jsx'
import './styles.css'

function Login() {
  const location = useLocation()
  return <section className="login-card">
    <div className="brand-mark">P</div>
    <h1>Корпоративная парковка</h1>
    {location.state?.passwordChanged && <p className="notice" role="status">Пароль изменён. Войдите с новым паролем</p>}
    <SaveForm className="" label="Войти" onSave={form => login(form.get('email'), form.get('password'))}>
      <label>Почта<input name="email" type="email" required maxLength={254} autoComplete="username" /></label>
      <label>Пароль<input name="password" type="password" required maxLength={128} autoComplete="current-password" /></label>
    </SaveForm>
  </section>
}

function Password({user}) {
  const initial = user.passwordChangeRequired
  const navigate = useNavigate()
  async function change(form) {
    const newPassword = form.get('newPassword')
    if (newPassword !== form.get('repeatPassword')) throw new Error('Пароли не совпадают')
    const path = initial ? '/auth/change-initial-password' : '/auth/change-password'
    const body = initial ? {newPassword} : {newPassword, currentPassword: form.get('currentPassword')}
    await api(path, {method: 'POST', body})
    await logout()
    navigate('/login', {replace: true, state: {passwordChanged: true}})
  }

  return <section className="login-card">
    <h1>{initial ? 'Замените временный пароль' : 'Смена пароля'}</h1>
    <p className="muted">От 12 символов: заглавная и строчная буквы, цифра и специальный символ</p>
    <SaveForm className="" cancel={initial ? undefined : '/bookings'} label="Сохранить пароль" onSave={change}>
      {!initial && <label>Текущий пароль<input name="currentPassword" type="password" required autoComplete="current-password" /></label>}
      <label>Новый пароль<input name="newPassword" type="password" required minLength={12} maxLength={128} autoComplete="new-password" /></label>
      <label>Повторите новый пароль<input name="repeatPassword" type="password" required minLength={12} maxLength={128} autoComplete="new-password" /></label>
    </SaveForm>
    {initial && <SaveForm className="" label="Выйти" onSave={logout} />}
  </section>
}

function Forbidden() {
  return <section><h1>Недостаточно прав</h1><Link to="/bookings">К бронированиям</Link></section>
}

function Workspace({user}) {
  const admin = user.roles.includes('ADMIN')
  const editor = admin || user.roles.includes('EDITOR')
  const [error, setError] = useState(null)
  const [leaving, setLeaving] = useState(false)

  async function signOut() {
    setLeaving(true)
    try {
      await logout()
    } catch (error) {
      setError(error)
    } finally {
      setLeaving(false)
    }
  }

  async function openSwagger(event) {
    event.preventDefault()
    try {
      await refreshSession()
      window.location.assign('/swagger-ui.html')
    } catch (error) {
      setError(error)
    }
  }

  return <>
    <header className="topbar">
      <Link className="brand" to="/bookings"><span className="brand-mark">P</span><strong>Корпоративная парковка</strong></Link>
      <div className="account">
        <span>{user.displayName} · {user.roles.map(role => roles[role]).join(', ')}</span>
        <Link to="/password">Сменить пароль</Link>
        <button disabled={leaving} onClick={signOut}>Выйти</button>
      </div>
    </header>
    <div className="workspace">
      <aside><nav aria-label="Основная навигация">
        <NavLink to="/bookings">Бронирования</NavLink>
        <NavLink to="/employees">Сотрудники</NavLink>
        <NavLink to="/analytics">Обзор парковки</NavLink>
        {editor && <NavLink to="/import">Импорт CSV</NavLink>}
        {admin && <>
          <NavLink to="/admin/users">Пользователи</NavLink>
          <NavLink to="/admin/audit">Журнал действий</NavLink>
          <a href="/swagger-ui.html" onClick={openSwagger}>Swagger</a>
        </>}
      </nav></aside>
      <main>
        <ErrorMessage error={error} />
        <Routes>
          <Route path="/bookings" element={<Bookings editor={editor} admin={admin} />} />
          <Route path="/bookings/new" element={editor ? <BookingForm /> : <Forbidden />} />
          <Route path="/bookings/:id/edit" element={editor ? <BookingForm /> : <Forbidden />} />
          <Route path="/bookings/:id/notes" element={<Notes user={user} editor={editor} admin={admin} />} />
          <Route path="/bookings/:id/permanent" element={admin ? <DeleteBooking /> : <Forbidden />} />
          <Route path="/employees" element={<Employees admin={admin} />} />
          <Route path="/employees/new" element={admin ? <EmployeeForm /> : <Forbidden />} />
          <Route path="/employees/:id/edit" element={admin ? <EmployeeForm /> : <Forbidden />} />
          <Route path="/analytics" element={<Analytics />} />
          <Route path="/import" element={editor ? <ImportCsv /> : <Forbidden />} />
          <Route path="/admin/users" element={admin ? <Users /> : <Forbidden />} />
          <Route path="/admin/users/new" element={admin ? <UserForm /> : <Forbidden />} />
          <Route path="/admin/users/:id/edit" element={admin ? <UserForm /> : <Forbidden />} />
          <Route path="/admin/users/:id/reset" element={admin ? <ResetPassword /> : <Forbidden />} />
          <Route path="/admin/audit" element={admin ? <Audit /> : <Forbidden />} />
          <Route path="*" element={<Navigate to="/bookings" replace />} />
        </Routes>
      </main>
    </div>
  </>
}

function App() {
  const session = useSyncExternalStore(subscribeToSession, currentSession)
  const [ready, setReady] = useState(false)
  const location = useLocation()
  useEffect(() => {
    refreshSession().catch(() => {}).finally(() => setReady(true))
  }, [])

  if (!ready) return <p className="login-card" role="status">Загрузка…</p>
  if (!session) {
    if (location.pathname === '/login') return <Login />
    return <Navigate to="/login" replace state={{from: location.pathname + location.search}} />
  }
  const user = session.user
  if (user.passwordChangeRequired && location.pathname !== '/password') return <Navigate to="/password" replace />
  if (location.pathname === '/password') return <Password user={user} />
  if (location.pathname === '/login') return <Navigate to={location.state?.from || '/bookings'} replace />
  return <Workspace user={user} />
}

createRoot(document.getElementById('root')).render(<BrowserRouter><App /></BrowserRouter>)

