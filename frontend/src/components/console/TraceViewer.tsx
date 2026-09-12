import { useEffect, useMemo, useState } from 'react'
import { api } from '../../api/client'
import type { Span, Trace } from '../../api/types'
import { hhmmss } from '../../api/format'
import { Empty, ErrorNote, Loading } from '../ui'

export function TraceViewer({ attemptId }: { attemptId: string }) {
  const [traces, setTraces] = useState<Trace[] | null>(null)
  const [selected, setSelected] = useState<string | null>(null)
  const [failure, setFailure] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    api
      .traces(attemptId)
      .then(({ traces: list }) => {
        if (cancelled) return
        setTraces(list)
        setSelected(list[0]?.traceId ?? null)
      })
      .catch((error: unknown) => {
        if (!cancelled) setFailure(error instanceof Error ? error.message : 'Falha ao carregar traces')
      })
    return () => {
      cancelled = true
    }
  }, [attemptId])

  const trace = useMemo(() => traces?.find((t) => t.traceId === selected) ?? null, [traces, selected])

  if (failure) return <ErrorNote>{failure}</ErrorNote>
  if (!traces) return <Loading label="carregando traces" />
  if (traces.length === 0) return <Empty title="Sem traces neste caso" hint="Este tipo de caso não expõe tracing distribuído." />

  return (
    <div className="traces">
      <ol className="traces__list">
        {traces.map((item) => (
          <li key={item.traceId}>
            <button
              type="button"
              className={`traces__item ${item.traceId === selected ? 'traces__item--on' : ''}`}
              onClick={() => setSelected(item.traceId)}
            >
              <span className="traces__duration mono">{item.durationMs}ms</span>
              <span className="traces__op">{item.rootOperation}</span>
              <span className="traces__service mono">{item.rootService}</span>
              <span className="traces__spans mono">{item.spans.length} spans</span>
              <span className="traces__time mono">{hhmmss(item.startedAt)}</span>
            </button>
          </li>
        ))}
      </ol>
      {trace && <Waterfall trace={trace} />}
    </div>
  )
}

/**
 * Cascata de spans. O agrupamento por operação repetida é deliberado: um N+1 aparece como uma
 * barra única com "× 47", que é exatamente a leitura que o investigador precisa fazer.
 */
function Waterfall({ trace }: { trace: Trace }) {
  const total = Math.max(1, trace.durationMs)
  const groups = groupRepeated(trace.spans)

  return (
    <div className="waterfall">
      <header className="waterfall__head mono">
        <span>{trace.traceId.slice(0, 16)}</span>
        <span className="spacer" />
        <span>{trace.durationMs}ms no total</span>
      </header>
      <ol className="waterfall__rows">
        {groups.map((group, index) => {
          const left = (group.startOffsetMs / total) * 100
          const width = Math.max(0.6, (group.durationMs / total) * 100)
          const share = Math.round((group.durationMs / total) * 100)
          return (
            <li key={index} className="waterfall__row">
              <span className="waterfall__label">
                <span className="waterfall__service mono">{group.service}</span>
                <span className="waterfall__operation">{group.operation}</span>
                {group.count > 1 && <span className="waterfall__count mono">× {group.count}</span>}
              </span>
              <span className="waterfall__track">
                <span
                  className={`waterfall__bar ${share >= 40 ? 'waterfall__bar--hot' : ''}`}
                  style={{ left: `${left}%`, width: `${width}%` }}
                />
              </span>
              <span className="waterfall__ms mono">{group.durationMs}ms</span>
              <span className="waterfall__share mono">{share}%</span>
            </li>
          )
        })}
      </ol>
      <footer className="waterfall__foot mono">
        {groups.some((g) => g.count > 5)
          ? 'Muitos spans idênticos no mesmo request: assinatura de N+1.'
          : 'Um span dominante costuma indicar query cara, lock ou dependência lenta.'}
      </footer>
    </div>
  )
}

interface SpanGroup {
  service: string
  operation: string
  startOffsetMs: number
  durationMs: number
  count: number
}

function groupRepeated(spans: Span[]): SpanGroup[] {
  const ordered = [...spans].sort((a, b) => a.startOffsetMs - b.startOffsetMs)
  const groups: SpanGroup[] = []
  for (const span of ordered) {
    const previous = groups[groups.length - 1]
    if (previous && previous.service === span.service && previous.operation === span.operation) {
      previous.durationMs = span.startOffsetMs + span.durationMs - previous.startOffsetMs
      previous.count += 1
      continue
    }
    groups.push({
      service: span.service,
      operation: span.operation,
      startOffsetMs: span.startOffsetMs,
      durationMs: span.durationMs,
      count: 1,
    })
  }
  return groups
}
