import { useEffect, useMemo, useState } from 'react'
import { api } from '../../api/client'
import type { MetricsView, TimelineView } from '../../api/types'
import { TimeSeriesChart, type TimeMarker } from '../charts'
import { compact } from '../../api/format'
import { Empty, ErrorNote, Loading } from '../ui'

/** Métricas que quase sempre respondem "quem degradou" antes das outras. */
const PRIORITY = [
  'latency_p99',
  'error_rate',
  'requests_per_second',
  'db_pool_in_use',
  'cpu_utilization',
  'consumer_lag',
  'read_iops',
  'active_connections',
  'memory_used',
]

export function MetricsPanel({ attemptId }: { attemptId: string }) {
  const [data, setData] = useState<MetricsView | null>(null)
  const [timeline, setTimeline] = useState<TimelineView | null>(null)
  const [service, setService] = useState<string | null>(null)
  const [busy, setBusy] = useState(true)
  const [failure, setFailure] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setBusy(true)
    api
      .metrics(attemptId, service ?? undefined)
      .then((view) => {
        if (!cancelled) {
          setData(view)
          setFailure(null)
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) setFailure(error instanceof Error ? error.message : 'Falha ao carregar métricas')
      })
      .finally(() => {
        if (!cancelled) setBusy(false)
      })
    return () => {
      cancelled = true
    }
  }, [attemptId, service])

  // A linha do tempo entra aqui só para marcar deploys e alertas sobre os gráficos.
  useEffect(() => {
    let cancelled = false
    api
      .timeline(attemptId)
      .then((view) => {
        if (!cancelled) setTimeline(view)
      })
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [attemptId])

  const markers = useMemo<TimeMarker[]>(() => {
    if (!timeline) return []
    return [
      ...timeline.alerts.map((alert) => ({
        at: alert.firedAt,
        label: `alerta ${alert.name}`,
        tone: (alert.severity === 'critical' ? 'crit' : 'warn') as TimeMarker['tone'],
      })),
      ...timeline.deploys.map((deploy) => ({
        at: deploy.at,
        label: `deploy ${deploy.service} ${deploy.version}`,
        tone: 'neutral' as const,
      })),
    ]
  }, [timeline])

  const grouped = useMemo(() => {
    if (!data) return []
    const byService = new Map<string, MetricsView['series']>()
    for (const series of data.series) {
      if (series.points.length === 0) continue
      const list = byService.get(series.service) ?? []
      list.push(series)
      byService.set(series.service, list)
    }
    // Séries terminadas em _limit são o teto do recurso: viram linha de referência no gráfico correspondente.
    return [...byService.entries()].map(([name, series]) => {
      const limits = new Map<string, number>()
      for (const item of series) {
        if (item.metric.endsWith('_limit')) limits.set(item.metric.replace(/_limit$/, ''), item.points[0].value)
      }
      return {
        service: name,
        limits,
        series: series.filter((item) => !item.metric.endsWith('_limit')).sort((a, b) => rank(a.metric) - rank(b.metric)),
      }
    })
  }, [data])

  if (busy && !data) return <Loading label="lendo métricas" />
  if (failure) return <ErrorNote>{failure}</ErrorNote>
  if (!data) return <Empty title="Sem métricas para este caso" />

  return (
    <div className="metrics">
      <div className="metrics__filters">
        <button
          type="button"
          className={`chip ${service === null ? 'chip--on' : ''}`}
          onClick={() => setService(null)}
        >
          todos os serviços
        </button>
        {data.services.map((name) => (
          <button
            key={name}
            type="button"
            className={`chip mono ${service === name ? 'chip--on' : ''}`}
            onClick={() => setService(name)}
          >
            {name}
          </button>
        ))}
      </div>

      {markers.length > 0 && (
        <p className="metrics__legend mono">
          <span className="metrics__legend-mark metrics__legend-mark--crit" /> alerta
          <span className="metrics__legend-mark metrics__legend-mark--neutral" /> deploy
          <span className="metrics__legend-dash" /> linha de base
        </p>
      )}

      {grouped.length === 0 && <Empty title="Nenhuma série nesta janela" />}

      {grouped.map((group) => (
        <section key={group.service} className="metrics__group">
          <h3 className="metrics__group-title mono">{group.service}</h3>
          <div className="metrics__grid">
            {group.series.map((series) => {
              const limit = group.limits.get(series.metric)
              return (
                <TimeSeriesChart
                  key={series.metric}
                  series={series}
                  markers={markers}
                  threshold={limit === undefined ? undefined : { value: limit, label: `limite ${compact(limit)}` }}
                  tone={toneFor(series.metric)}
                />
              )
            })}
          </div>
        </section>
      ))}
    </div>
  )
}

const rank = (metric: string) => {
  const index = PRIORITY.indexOf(metric)
  return index === -1 ? PRIORITY.length + 1 : index
}

function toneFor(metric: string): 'signal' | 'warn' | 'crit' {
  if (metric.startsWith('latency') || metric.includes('lag') || metric.includes('dlq')) return 'crit'
  if (metric.includes('error') || metric.includes('pool') || metric.includes('cpu')) return 'warn'
  return 'signal'
}
