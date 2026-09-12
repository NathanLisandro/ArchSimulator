import { useState } from 'react'
import { api } from '../api/client'
import type { User } from '../api/types'
import './login.css'

export function Login({ onAuthenticated }: { onAuthenticated: (user: User) => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [username, setUsername] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setBusy(true)
    setFailure(null)
    try {
      if (mode === 'register') await api.register(username, displayName || username, password)
      onAuthenticated(await api.login(username, password))
    } catch (error) {
      setFailure(error instanceof Error ? error.message : 'Não foi possível entrar')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="entry">
      <section className="entry__pitch">
        <div className="entry__brand">
          <svg viewBox="0 0 34 20" className="entry__mark" aria-hidden>
            <path
              d="M1 15 L7 15 L10 6 L13 15 L17 15 L20 2 L24 18 L27 11 L33 11"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinejoin="round"
              strokeLinecap="round"
            />
          </svg>
          <span>ArchSim</span>
        </div>
        <p className="entry__dispatch mono">
          <span className="entry__dispatch-dot" aria-hidden />
          14:32 · alerta HighLatencyP99 · account-api
        </p>
        <h1 className="entry__title">
          Você é o arquiteto de plantão.
        </h1>
        <p className="entry__lede">
          O extrato está em 4,2 segundos no p99, o suporte já abriu quarenta tickets e ninguém sabe o que mudou.
          Você tem logs, métricas, traces, um console SQL e a linha do tempo de deploys. Investigue, decida, e
          defenda a decisão.
        </p>
        <ul className="entry__list">
          <li>
            <span className="entry__list-key">Investigar</span>
            Oito tipos de caso, de query lenta a migração de legado, com dados gerados por cenário.
          </li>
          <li>
            <span className="entry__list-key">Decidir</span>
            Vários caminhos válidos por caso. A avaliação pontua o caminho que você seguiu, não o que o autor preferiu.
          </li>
          <li>
            <span className="entry__list-key">Defender</span>
            ADR e post-mortem avaliados por rubrica, com resposta de referência para comparar.
          </li>
        </ul>
      </section>

      <section className="entry__form-side">
        <form className="entry__form" onSubmit={submit}>
          <div className="entry__tabs" role="tablist">
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'login'}
              className={`entry__tab ${mode === 'login' ? 'entry__tab--on' : ''}`}
              onClick={() => setMode('login')}
            >
              Entrar
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'register'}
              className={`entry__tab ${mode === 'register' ? 'entry__tab--on' : ''}`}
              onClick={() => setMode('register')}
            >
              Criar conta
            </button>
          </div>

          <label className="field">
            <span className="field__label">Usuário</span>
            <input
              className="input"
              value={username}
              autoComplete="username"
              autoCapitalize="none"
              required
              onChange={(event) => setUsername(event.target.value.toLowerCase())}
            />
          </label>

          {mode === 'register' && (
            <label className="field">
              <span className="field__label">Como quer ser chamado</span>
              <input
                className="input"
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                placeholder={username || 'seu nome'}
              />
            </label>
          )}

          <label className="field">
            <span className="field__label">Senha</span>
            <input
              className="input"
              type="password"
              value={password}
              autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
              required
              minLength={6}
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>

          {failure && (
            <p className="entry__error mono" role="alert">
              {failure}
            </p>
          )}

          <button className="btn btn--primary entry__submit" type="submit" disabled={busy}>
            {busy ? 'Um momento' : mode === 'register' ? 'Criar conta e entrar' : 'Entrar'}
          </button>

          <p className="entry__note">
            Conta local, guardada só neste servidor. Nada é enviado para fora.
          </p>
        </form>
      </section>
    </div>
  )
}
