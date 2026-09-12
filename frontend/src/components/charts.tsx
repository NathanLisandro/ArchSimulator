import { useMemo, useState } from 'react'
import type { MetricSeries } from '../api/types'
import { compact, hhmm } from '../api/format'
import './charts.css'

export interface TimeMarker {
  at: string
  label: string
  tone: 'crit' | 'warn' | 'neutral'
}

/**
 * Série temporal desenhada à mão em SVG. Duas decisões carregam informação:
 * a faixa cinza é a mediana da primeira hora (a "linha de base" do serviço saudável), e as marcas verticais
 * são deploys e alertas — o mesmo eixo de tempo em todos os gráficos é o que permite correlacionar.
 */
export function TimeSeriesChart({
  series,
  markers = [],
  threshold,
  height = 128,
  tone = 'signal',
}: {
  series: MetricSeries
  markers?: TimeMarker[]
  /** Limite do recurso (max_connections, IOPS provisionado): desenhado como referência, não como outra série. */
  threshold?: { value: number; label: string }
  height?: number
  tone?: 'signal' | 'warn' | 'crit'
}) {
  const [hover, setHover] = useState<number | null>(null)
  const width = 640
  const padTop = 10
  const padBottom = 18
  const padLeft = 44
  const padRight = 8

  const geometry = useMemo(() => {
    // 90 dias de amostra horária em 640px viram uma mancha. Reduz por balde, guardando o pico de cada um:
    // em capacity planning o que importa é a tendência do pico, não a média.
    const points = downsample(series.points, width - padLeft - padRight)
    if (points.length === 0) return null
    const values = points.map((p) => p.value)
    const times = points.map((p) => new Date(p.at).getTime())
    const min = Math.min(...values)
    const max = Math.max(threshold ? threshold.value : -Infinity, ...values)
    // Uma métrica plana com ruído (erro em 0,2%) não pode ocupar a altura toda e parecer um incidente:
    // quando a variação é pequena perto do valor, ancora no zero e deixa a linha rasteira.
    const span = max - min
    const flat = span < Math.abs(max) * 0.25
    const lo = flat ? 0 : Math.max(0, min - span * 0.12)
    const hi = flat ? Math.max(1, max * 1.7) : max + span * 0.12
    const t0 = times[0]
    const t1 = times[times.length - 1] || t0 + 1

    const x = (t: number) => padLeft + ((t - t0) / Math.max(1, t1 - t0)) * (width - padLeft - padRight)
    const y = (v: number) => padTop + (1 - (v - lo) / Math.max(1e-9, hi - lo)) * (height - padTop - padBottom)

    const line = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(times[i]).toFixed(1)},${y(p.value).toFixed(1)}`).join(' ')
    const area = `${line} L${x(times[times.length - 1]).toFixed(1)},${height - padBottom} L${x(t0).toFixed(1)},${height - padBottom} Z`

    const baselineCount = Math.max(3, Math.floor(points.length * 0.15))
    const baseline = [...values.slice(0, baselineCount)].sort((a, b) => a - b)[Math.floor(baselineCount / 2)]

    return { points, times, x, y, line, area, lo, hi, t0, t1, baseline }
  }, [series, height, threshold])

  if (!geometry) return <p className="chart__empty mono">sem dados na janela</p>

  const { points, times, x, y, line, area, lo, hi, baseline } = geometry
  const hovered = hover === null ? null : points[hover]
  const colour = tone === 'crit' ? 'var(--crit)' : tone === 'warn' ? 'var(--warn)' : 'var(--signal)'

  return (
    <figure className="chart">
      <figcaption className="chart__caption">
        <span className="chart__metric mono">{series.metric}</span>
        <span className="chart__service">{series.service}</span>
        <span className="chart__value mono">
          {hovered ? compact(hovered.value) : compact(points[points.length - 1].value)}
          {series.unit && <span className="chart__unit"> {series.unit}</span>}
        </span>
      </figcaption>
      <svg
        viewBox={`0 0 ${width} ${height}`}
        className="chart__svg"
        preserveAspectRatio="none"
        role="img"
        aria-label={`${series.metric} de ${series.service}`}
        onMouseLeave={() => setHover(null)}
        onMouseMove={(event) => {
          const rect = event.currentTarget.getBoundingClientRect()
          const ratio = (event.clientX - rect.left) / rect.width
          const px = ratio * width
          let nearest = 0
          let best = Infinity
          for (let i = 0; i < times.length; i++) {
            const distance = Math.abs(x(times[i]) - px)
            if (distance < best) {
              best = distance
              nearest = i
            }
          }
          setHover(nearest)
        }}
      >
        <line x1={padLeft} x2={width - padRight} y1={y(baseline)} y2={y(baseline)} className="chart__baseline" />
        {threshold && (
          <g className="chart__threshold">
            <line x1={padLeft} x2={width - padRight} y1={y(threshold.value)} y2={y(threshold.value)} />
            <text x={width - padRight - 2} y={y(threshold.value) - 3} textAnchor="end">
              {threshold.label}
            </text>
          </g>
        )}
        <path d={area} fill={colour} className="chart__area" />
        <path d={line} fill="none" stroke={colour} strokeWidth="1.6" vectorEffect="non-scaling-stroke" />

        {markers.map((marker, index) => {
          const time = new Date(marker.at).getTime()
          if (time < times[0] || time > times[times.length - 1]) return null
          return (
            <g key={index} className={`chart__marker chart__marker--${marker.tone}`}>
              <line x1={x(time)} x2={x(time)} y1={padTop - 4} y2={height - padBottom} />
              <title>{`${marker.label} — ${hhmm(marker.at)}`}</title>
            </g>
          )
        })}

        {hovered && (
          <g className="chart__cursor">
            <line x1={x(times[hover!])} x2={x(times[hover!])} y1={padTop} y2={height - padBottom} />
            <circle cx={x(times[hover!])} cy={y(hovered.value)} r="3" fill={colour} />
          </g>
        )}

        <text x={4} y={padTop + 8} className="chart__axis">
          {compact(hi)}
        </text>
        <text x={4} y={height - padBottom - 2} className="chart__axis">
          {compact(lo)}
        </text>
        <text x={padLeft} y={height - 4} className="chart__axis">
          {hhmm(points[0].at)}
        </text>
        <text x={width - padRight} y={height - 4} textAnchor="end" className="chart__axis">
          {hhmm(points[points.length - 1].at)}
        </text>
      </svg>
      {hovered && (
        <p className="chart__readout mono">
          {hhmm(hovered.at)} · {compact(hovered.value)} {series.unit}
        </p>
      )}
    </figure>
  )
}

/** Reduz a série a no máximo `buckets` pontos, preservando o pico de cada balde. */
function downsample(points: MetricSeries['points'], buckets: number) {
  if (points.length <= buckets || buckets < 8) return points
  const size = Math.ceil(points.length / buckets)
  const out: MetricSeries['points'] = []
  for (let i = 0; i < points.length; i += size) {
    const slice = points.slice(i, i + size)
    let peak = slice[0]
    for (const point of slice) if (point.value > peak.value) peak = point
    out.push({ at: slice[Math.floor(slice.length / 2)].at, value: peak.value })
  }
  return out
}

/** Barras horizontais com rótulo à esquerda; usado para nota por tipo de caso e por competência. */
export function BarBreakdown({
  items,
  max = 100,
  suffix = '',
}: {
  items: { label: string; value: number; meta?: string }[]
  max?: number
  suffix?: string
}) {
  if (items.length === 0) return <p className="chart__empty">Nada praticado ainda.</p>
  return (
    <ul className="bars">
      {items.map((item) => {
        const ratio = Math.max(0, Math.min(1, item.value / max))
        const tone = item.value >= 75 ? 'ok' : item.value >= 50 ? 'warn' : 'crit'
        return (
          <li key={item.label} className="bars__row">
            <span className="bars__label">{item.label}</span>
            <span className="bars__track">
              <span className={`bars__fill bars__fill--${tone}`} style={{ width: `${ratio * 100}%` }} />
            </span>
            <span className="bars__value mono">
              {Math.round(item.value)}
              {suffix}
            </span>
            {item.meta && <span className="bars__meta mono">{item.meta}</span>}
          </li>
        )
      })}
    </ul>
  )
}

/** Sequência de notas ao longo do tempo, para o histórico. */
export function ScoreTrail({ scores }: { scores: { score: number; label: string }[] }) {
  if (scores.length < 2) return null
  const width = 300
  const height = 48
  const step = width / Math.max(1, scores.length - 1)
  const y = (value: number) => height - 4 - (Math.max(0, Math.min(100, value)) / 100) * (height - 10)
  const path = scores.map((s, i) => `${i === 0 ? 'M' : 'L'}${(i * step).toFixed(1)},${y(s.score).toFixed(1)}`).join(' ')
  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="trail" role="img" aria-label="Evolução das notas">
      <line x1="0" x2={width} y1={y(70)} y2={y(70)} className="trail__target" />
      <path d={path} fill="none" stroke="var(--signal)" strokeWidth="1.5" vectorEffect="non-scaling-stroke" />
      {scores.map((s, i) => (
        <circle key={i} cx={i * step} cy={y(s.score)} r="2.5" fill="var(--signal-deep)">
          <title>{`${s.label}: ${Math.round(s.score)}`}</title>
        </circle>
      ))}
    </svg>
  )
}
