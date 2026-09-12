import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import type { AttemptSummary, ProgressView, SprintView } from '../api/types'
import { CASE_TYPE_LABEL, LEVEL_LABEL, competencyLabel, dayTime, duration, number } from '../api/format'
import { BarBreakdown, ScoreTrail } from '../components/charts'
import { Empty, ErrorNote, Loading, Panel, RankTrack } from '../components/ui'
import './progress.css'

export function ProgressPage() {
  const navigate = useNavigate()
  const [progress, setProgress] = useState<ProgressView | null>(null)
  const [history, setHistory] = useState<AttemptSummary[] | null>(null)
  const [sprint, setSprint] = useState<SprintView | null>(null)
  const [failure, setFailure] = useState<string | null>(null)

  useEffect(() => {
    void Promise.all([api.progress(), api.history(), api.currentSprint().catch(() => undefined)])
      .then(([progressView, historyList, sprintView]) => {
        setProgress(progressView)
        setHistory(historyList)
        setSprint(sprintView ?? null)
      })
      .catch((error: unknown) => setFailure(error instanceof Error ? error.message : 'Falha ao carregar'))
  }, [])

  const byType = useMemo(() => {
    if (!progress) return []
    return Object.entries(progress.byType)
      .map(([type, stats]) => ({
        label: CASE_TYPE_LABEL[type] ?? type,
        value: stats.attempts === 0 ? 0 : stats.totalScore / stats.attempts,
        meta: `${stats.attempts}×`,
      }))
      .sort((a, b) => a.value - b.value)
  }, [progress])

  const byCompetency = useMemo(() => {
    if (!progress) return []
    return Object.entries(progress.byCompetency)
      .map(([competency, stats]) => ({
        label: competencyLabel(competency),
        value: stats.attempts === 0 ? 0 : stats.totalScore / stats.attempts,
        meta: `${stats.attempts}×`,
      }))
      .sort((a, b) => a.value - b.value)
  }, [progress])

  const trail = useMemo(
    () =>
      (history ?? [])
        .filter((attempt) => attempt.score !== null)
        .slice(0, 14)
        .reverse()
        .map((attempt) => ({ score: attempt.score ?? 0, label: CASE_TYPE_LABEL[attempt.caseType] ?? attempt.caseType })),
    [history],
  )

  if (failure) return <div className="page"><ErrorNote>{failure}</ErrorNote></div>
  if (!progress || !history) return <div className="page"><Loading label="lendo seu histórico" /></div>

  const untouched = Object.keys(CASE_TYPE_LABEL).filter((type) => !progress.byType[type])

  return (
    <div className="page progress">
      <header className="page__head">
        <h1 className="page__title">Progresso</h1>
        <p className="page__lede">
          A promoção exige as três coisas ao mesmo tempo: volume de casos, média e variedade de tipos praticados.
        </p>
      </header>

      <section className="progress__rank">
        <RankTrack rank={progress.rank} />
        <div className="progress__rank-meta">
          <p className="progress__rank-now">
            Você está em <strong>{LEVEL_LABEL[progress.rank]}</strong>.
          </p>
          <p className="progress__rank-next">{progress.missingForNextRank}</p>
        </div>
        <dl className="progress__numbers">
          <div>
            <dt>casos</dt>
            <dd className="mono">{progress.solvedCount}</dd>
          </div>
          <div>
            <dt>média</dt>
            <dd className="mono">{Math.round(progress.averageScore)}</dd>
          </div>
          <div>
            <dt>xp</dt>
            <dd className="mono">{number(progress.xp)}</dd>
          </div>
        </dl>
      </section>

      {sprint && sprint.status === 'RUNNING' && (
        <p className="progress__sprint mono">
          sprint em andamento: {sprint.completedCases}/{sprint.totalCases} casos, {duration(sprint.remainingSeconds)} restantes
        </p>
      )}

      <div className="progress__grid">
        <Panel title="Por tipo de caso" meta="média, do mais fraco ao mais forte">
          <BarBreakdown items={byType} />
          {untouched.length > 0 && (
            <p className="progress__untouched">
              Nunca praticados: {untouched.map((type) => CASE_TYPE_LABEL[type]).join(', ')}.
            </p>
          )}
        </Panel>

        <Panel title="Por competência" meta="taxonomia do MBA Full Cycle">
          <BarBreakdown items={byCompetency} />
        </Panel>
      </div>

      {trail.length >= 2 && (
        <Panel title="Evolução das notas" meta="as 14 tentativas mais recentes">
          <ScoreTrail scores={trail} />
          <p className="progress__trail-note">A linha pontilhada marca 70, a média exigida para pleno.</p>
        </Panel>
      )}

      <Panel title="Histórico" meta={`${history.length} tentativa(s)`}>
        {history.length === 0 ? (
          <Empty title="Nada aqui ainda" hint="Resolva o primeiro caso para começar o histórico." />
        ) : (
          <table className="history">
            <thead>
              <tr>
                <th>caso</th>
                <th>tipo</th>
                <th>caminho</th>
                <th className="num">tempo</th>
                <th className="num">nota</th>
                <th>quando</th>
              </tr>
            </thead>
            <tbody>
              {history.map((attempt) => (
                <tr key={attempt.id} onClick={() => navigate(`/caso/${attempt.id}`)}>
                  <td className="history__title">{attempt.caseTitle}</td>
                  <td className="mono">{CASE_TYPE_LABEL[attempt.caseType]}</td>
                  <td className="history__path">{attempt.followedPath ?? '—'}</td>
                  <td className="num mono">{duration(attempt.durationSeconds)}</td>
                  <td className={`num mono history__score history__score--${tone(attempt.score)}`}>
                    {attempt.score === null ? '—' : Math.round(attempt.score)}
                  </td>
                  <td className="mono history__when">{dayTime(attempt.startedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>
    </div>
  )
}

const tone = (score: number | null) => (score === null ? 'none' : score >= 75 ? 'ok' : score >= 50 ? 'warn' : 'crit')
