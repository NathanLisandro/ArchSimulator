import { useEffect, useMemo, useState } from 'react'
import { api } from '../../api/client'
import type { TimelineView } from '../../api/types'
import { hhmm } from '../../api/format'
import { Empty, ErrorNote, Loading } from '../ui'

interface Entry {
  at: string
  kind: 'deploy' | 'flag' | 'alert'
  title: string
  detail: string
  tone: 'crit' | 'warn' | 'neutral'
}

export function DeployTimeline({ attemptId }: { attemptId: string }) {
  const [data, setData] = useState<TimelineView | null>(null)
  const [failure, setFailure] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    api
      .timeline(attemptId)
      .then((view) => {
        if (!cancelled) setData(view)
      })
      .catch((error: unknown) => {
        if (!cancelled) setFailure(error instanceof Error ? error.message : 'Falha ao carregar a linha do tempo')
      })
    return () => {
      cancelled = true
    }
  }, [attemptId])

  const entries = useMemo<Entry[]>(() => {
    if (!data) return []
    const merged: Entry[] = [
      ...data.deploys.map((deploy) => ({
        at: deploy.at,
        kind: 'deploy' as const,
        title: `${deploy.service} ${deploy.version}`,
        detail: `${deploy.description} — ${deploy.author}`,
        tone: 'neutral' as const,
      })),
      ...data.flagChanges.map((flag) => ({
        at: flag.at,
        kind: 'flag' as const,
        title: `flag ${flag.flag}: ${flag.from} → ${flag.to}`,
        detail: `${flag.actor} · escopo ${flag.scope}`,
        tone: 'warn' as const,
      })),
      ...data.alerts.map((alert) => ({
        at: alert.firedAt,
        kind: 'alert' as const,
        title: `${alert.name} (${alert.service})`,
        detail: alert.description,
        tone: (alert.severity === 'critical' ? 'crit' : 'warn') as Entry['tone'],
      })),
    ]
    return merged.sort((a, b) => new Date(a.at).getTime() - new Date(b.at).getTime())
  }, [data])

  if (failure) return <ErrorNote>{failure}</ErrorNote>
  if (!data) return <Loading label="lendo mudanças" />

  const unhealthy = data.instances.filter((i) => i.status !== 'Running' || i.restarts > 0)

  return (
    <div className="timeline">
      {entries.length === 0 ? (
        <Empty title="Nenhuma mudança registrada nesta janela" />
      ) : (
        <ol className="timeline__list">
          {entries.map((entry, index) => (
            <li key={index} className={`timeline__entry timeline__entry--${entry.tone}`}>
              <span className="timeline__time mono">{hhmm(entry.at)}</span>
              <span className="timeline__mark" aria-hidden />
              <span className="timeline__body">
                <span className="timeline__title">{entry.title}</span>
                <span className="timeline__detail">{entry.detail}</span>
              </span>
              <span className="timeline__kind mono">{entry.kind}</span>
            </li>
          ))}
        </ol>
      )}

      {unhealthy.length > 0 && (
        <section className="timeline__instances">
          <h3 className="timeline__section-title mono">instâncias fora do normal</h3>
          <table className="data-table">
            <thead>
              <tr>
                <th>serviço</th>
                <th>instância</th>
                <th>status</th>
                <th className="num">cpu</th>
                <th className="num">memória</th>
                <th className="num">restarts</th>
              </tr>
            </thead>
            <tbody>
              {unhealthy.map((instance) => (
                <tr key={instance.instanceId}>
                  <td>{instance.service}</td>
                  <td>{instance.instanceId}</td>
                  <td className={instance.status === 'Running' ? undefined : 'timeline__bad'}>{instance.status}</td>
                  <td className="num">{Math.round(instance.cpuPercent)}%</td>
                  <td className="num">{Math.round(instance.memoryPercent)}%</td>
                  <td className="num">{instance.restarts}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </div>
  )
}

/** Mapa de infraestrutura: recursos e ligações declarados pela empresa do cenário. */
export function InfraMap({ attemptId }: { attemptId: string }) {
  const [data, setData] = useState<TimelineView | null>(null)
  const [failure, setFailure] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    api
      .timeline(attemptId)
      .then((view) => {
        if (!cancelled) setData(view)
      })
      .catch((error: unknown) => {
        if (!cancelled) setFailure(error instanceof Error ? error.message : 'Falha ao carregar a infraestrutura')
      })
    return () => {
      cancelled = true
    }
  }, [attemptId])

  if (failure) return <ErrorNote>{failure}</ErrorNote>
  if (!data) return <Loading label="lendo topologia" />
  if (data.infraResources.length === 0) return <Empty title="Sem topologia declarada para esta empresa" />

  const edgesFrom = (id: string) => data.infraEdges.filter((edge) => edge.from === id)

  return (
    <div className="infra">
      {data.infraResources.map((resource) => (
        <article key={resource.id} className="infra__node">
          <header className="infra__node-head">
            <h3 className="infra__name">{resource.name}</h3>
            <span className="infra__type mono">{resource.type}</span>
          </header>
          <dl className="infra__props">
            {Object.entries(resource.properties).map(([key, value]) => (
              <div key={key} className="infra__prop">
                <dt className="mono">{key}</dt>
                <dd className="mono">{value}</dd>
              </div>
            ))}
          </dl>
          {edgesFrom(resource.id).length > 0 && (
            <ul className="infra__edges">
              {edgesFrom(resource.id).map((edge, index) => (
                <li key={index} className="mono">
                  {edge.to}
                  {edge.label && <span className="infra__edge-label"> {edge.label}</span>}
                </li>
              ))}
            </ul>
          )}
        </article>
      ))}
    </div>
  )
}
