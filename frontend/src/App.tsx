import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { NavLink, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { api } from './api/client'
import type { User } from './api/types'
import { Login } from './pages/Login'
import { Home } from './pages/Home'
import { AttemptPage } from './pages/Attempt'
import { ProgressPage } from './pages/Progress'
import { StudyPage } from './pages/Study'
import { Loading } from './components/ui'
import './app.css'

interface Session {
  user: User | null
  setUser: (user: User | null) => void
}

const SessionContext = createContext<Session>({ user: null, setUser: () => undefined })
export const useSession = () => useContext(SessionContext)

export function App() {
  const [user, setUser] = useState<User | null>(null)
  const [checking, setChecking] = useState(true)

  useEffect(() => {
    api
      .me()
      .then(setUser)
      .catch(() => setUser(null))
      .finally(() => setChecking(false))
  }, [])

  if (checking) {
    return (
      <div className="boot">
        <Loading label="abrindo o simulador" />
      </div>
    )
  }

  return (
    <SessionContext.Provider value={{ user, setUser }}>
      {user ? <Shell /> : <Login onAuthenticated={setUser} />}
    </SessionContext.Provider>
  )
}

function Shell() {
  const location = useLocation()
  const focused = location.pathname.startsWith('/caso/')

  return (
    <div className={`shell ${focused ? 'shell--focused' : ''}`}>
      {!focused && <Chrome />}
      <main className="shell__main">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/caso/:attemptId" element={<AttemptPage />} />
          <Route path="/progresso" element={<ProgressPage />} />
          <Route path="/estudo" element={<StudyPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  )
}

function Chrome() {
  const { user, setUser } = useSession()
  return (
    <header className="chrome">
      <div className="chrome__brand">
        <Mark />
        <span className="chrome__name">ArchSim</span>
      </div>
      <nav className="chrome__nav">
        <NavItem to="/">Casos</NavItem>
        <NavItem to="/progresso">Progresso</NavItem>
        <NavItem to="/estudo">Estudo</NavItem>
      </nav>
      <div className="chrome__session">
        <span className="chrome__user">{user?.displayName}</span>
        <button
          type="button"
          className="btn btn--quiet btn--small"
          onClick={() => {
            void api.logout().finally(() => setUser(null))
          }}
        >
          Sair
        </button>
      </div>
    </header>
  )
}

function NavItem({ to, children }: { to: string; children: ReactNode }) {
  return (
    <NavLink to={to} className={({ isActive }) => `chrome__link ${isActive ? 'chrome__link--on' : ''}`} end={to === '/'}>
      {children}
    </NavLink>
  )
}

/** A marca é um traço de latência que sobe e volta: o gesto que o produto inteiro trata. */
function Mark() {
  return (
    <svg viewBox="0 0 34 20" className="chrome__mark" aria-hidden>
      <path
        d="M1 15 L7 15 L10 6 L13 15 L17 15 L20 2 L24 18 L27 11 L33 11"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinejoin="round"
        strokeLinecap="round"
      />
    </svg>
  )
}
