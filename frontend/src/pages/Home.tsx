import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import type { CompanySummary, ProgressView, SprintView } from '../api/types'
import { CASE_TYPE_LABEL, LEVEL_LABEL, clock, compact, number } from '../api/format'
import { Empty, ErrorNote, Loading, Panel, RankTrack } from '../components/ui'
import './home.css'

const CASE_TYPES = Object.keys(CASE_TYPE_LABEL)
const LEVELS = ['JUNIOR', 'PLENO', 'SENIOR', 'STAFF']

export function Home() {
  const navigate = useNavigate()
  const [companies, setCompanies] = useState<CompanySummary[] | null>(null)
  const [progress, setProgress] = useState<ProgressView | null>(null)
  const [sprint, setSprint] = useState<SprintView | null>(null)
  const [companyId, setCompanyId] = useState('')
  const [caseType, setCaseType] = useState('')
  const [level, setLevel] = useState('PLENO')
  const [starting, setStarting] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)
  const [openCompany, setOpenCompany] = useState<string | null>(null)

  useEffect(() => {
    void Promise.all([api.companies(), api.progress(), api.currentSprint().catch(() => undefined)])
      .then(([companyList, progressView, sprintView]) => {
        setCompanies(companyList)
        setProgress(progressView)
        setSprint(sprintView ?? null)
        setLevel(progressView.rank)
        if (sprintView?.caseType) setCaseType(sprintView.caseType)
      })
      .catch((error: unknown) => setFailure(error instanceof Error ? error.message : 'Falha ao carregar'))
  }, [])

  async function start() {
    setStarting(true)
    setFailure(null)
    try {
      const attempt = await api.startCase(companyId || undefined, caseType || undefined, level)
      navigate(`/caso/${attempt.id}`)
    } catch (error) {
      setFailure(error instanceof Error ? error.message : 'Não foi possível gerar o caso')
      setStarting(false)
    }
  }

  async function startSprint() {
    setStarting(true)
    setFailure(null)
    try {
      await api.startSprint(5, 75, level, caseType || undefined)
      const attempt = await api.startCase(companyId || undefined, caseType || undefined, level)
      navigate(`/caso/${attempt.id}`)
    } catch (error) {
      setFailure(error instanceof Error ? error.message : 'Não foi possível iniciar o sprint')
      setStarting(false)
    }
  }

  if (failure && !companies) return <div className="page"><ErrorNote>{failure}</ErrorNote></div>
  if (!companies || !progress) return <div className="page"><Loading label="carregando contextos" /></div>

  const suggested = progress.suggestedNextType

  return (
    <div className="page home">
      <header className="page__head">
        <h1 className="page__title">Próximo caso</h1>
        <p className="page__lede">
          Escolha o contexto e o tipo, ou deixe em branco para receber um sorteado. O nível define a dificuldade e
          se o caso dá dicas.
        </p>
      </header>

      {sprint && sprint.status === 'RUNNING' && (
        <div className="home__sprint" role="status">
          <span className="home__sprint-clock mono">{clock(sprint.remainingSeconds)}</span>
          <span className="home__sprint-text">
            Sprint em andamento: {sprint.completedCases} de {sprint.totalCases} casos.
            {sprint.caseType ? ` Só de ${CASE_TYPE_LABEL[sprint.caseType]}.` : ' Tipos misturados.'}
          </span>
        </div>
      )}

      <div className="home__grid">
        <Panel title="Gerar caso">
          <div className="home__form">
            <label className="field">
              <span className="field__label">Empresa</span>
              <select className="select" value={companyId} onChange={(event) => setCompanyId(event.target.value)}>
                <option value="">sortear</option>
                {companies.map((company) => (
                  <option key={company.id} value={company.id}>
                    {company.name}
                  </option>
                ))}
              </select>
            </label>

            <label className="field">
              <span className="field__label">Tipo de caso</span>
              <select className="select" value={caseType} onChange={(event) => setCaseType(event.target.value)}>
                <option value="">sortear</option>
                {CASE_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {CASE_TYPE_LABEL[type]}
                    {type === suggested ? ' — sugerido' : ''}
                  </option>
                ))}
              </select>
            </label>

            <fieldset className="home__levels">
              <legend className="field__label">Nível</legend>
              <div className="home__level-row">
                {LEVELS.map((option) => (
                  <button
                    key={option}
                    type="button"
                    className={`home__level ${level === option ? 'home__level--on' : ''}`}
                    onClick={() => setLevel(option)}
                  >
                    {LEVEL_LABEL[option]}
                  </button>
                ))}
              </div>
            </fieldset>

            {failure && <ErrorNote>{failure}</ErrorNote>}

            <div className="home__actions">
              <button className="btn btn--primary" type="button" disabled={starting} onClick={() => void start()}>
                {starting ? 'Gerando' : 'Começar caso'}
              </button>
              {(!sprint || sprint.status !== 'RUNNING') && (
                <button className="btn btn--quiet" type="button" disabled={starting} onClick={() => void startSprint()}>
                  Sprint de 5 casos em 75 min
                </button>
              )}
            </div>

            <p className="home__suggestion">
              Sua área mais fraca agora é <strong>{CASE_TYPE_LABEL[suggested]}</strong>. {progress.missingForNextRank}
            </p>
          </div>
        </Panel>

        <Panel title="Onde você está" meta={`${progress.solvedCount} caso(s)`}>
          <RankTrack rank={progress.rank} />
          <dl className="home__stats">
            <div>
              <dt>média</dt>
              <dd className="mono">{Math.round(progress.averageScore)}</dd>
            </div>
            <div>
              <dt>xp</dt>
              <dd className="mono">{number(progress.xp)}</dd>
            </div>
            <div>
              <dt>tipos praticados</dt>
              <dd className="mono">
                {Object.keys(progress.byType).length}/{CASE_TYPES.length}
              </dd>
            </div>
          </dl>
          {progress.recentAttempts.length === 0 ? (
            <Empty title="Nenhum caso resolvido ainda" hint="Comece pelo sugerido ao lado." />
          ) : (
            <ul className="home__recent">
              {progress.recentAttempts.slice(0, 4).map((attempt) => (
                <li key={attempt.id}>
                  <button type="button" className="home__recent-item" onClick={() => navigate(`/caso/${attempt.id}`)}>
                    <span className={`home__recent-score mono home__recent-score--${tone(attempt.score)}`}>
                      {attempt.score === null ? '—' : Math.round(attempt.score)}
                    </span>
                    <span className="home__recent-title">{attempt.caseTitle}</span>
                    <span className="home__recent-type mono">{CASE_TYPE_LABEL[attempt.caseType]}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Panel>
      </div>

      <section className="home__companies">
        <h2 className="home__section-title">Contextos disponíveis</h2>
        <div className="home__company-grid">
          {companies.map((company) => {
            const open = openCompany === company.id
            return (
              <article key={company.id} className={`company ${open ? 'company--open' : ''}`}>
                <header className="company__head">
                  <h3 className="company__name">{company.name}</h3>
                  <span className="company__domain mono">{company.domain}</span>
                </header>
                <p className="company__description">{company.description}</p>
                <dl className="company__scale">
                  <div>
                    <dt>usuários/dia</dt>
                    <dd className="mono">{compact(company.scale.dailyActiveUsers)}</dd>
                  </div>
                  <div>
                    <dt>pico</dt>
                    <dd className="mono">{number(company.scale.peakTps)} tps</dd>
                  </div>
                  <div>
                    <dt>serviços</dt>
                    <dd className="mono">{company.architecture.services.length}</dd>
                  </div>
                </dl>
                <button
                  type="button"
                  className="company__toggle"
                  aria-expanded={open}
                  onClick={() => setOpenCompany(open ? null : company.id)}
                >
                  {open ? 'Esconder dívidas técnicas' : `Ver ${company.techDebts.length} dívidas técnicas conhecidas`}
                </button>
                {open && (
                  <ul className="company__debts">
                    {company.techDebts.map((debt) => (
                      <li key={debt.title}>
                        <strong>{debt.title}.</strong> {debt.description}
                      </li>
                    ))}
                  </ul>
                )}
              </article>
            )
          })}
        </div>
      </section>
    </div>
  )
}

const tone = (score: number | null) => (score === null ? 'none' : score >= 75 ? 'ok' : score >= 50 ? 'warn' : 'crit')
