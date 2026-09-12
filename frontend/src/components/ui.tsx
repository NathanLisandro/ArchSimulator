import type { ReactNode } from 'react'
import './ui.css'

export function Panel({
  title,
  meta,
  actions,
  children,
  tone = 'paper',
  className,
}: {
  title?: ReactNode
  meta?: ReactNode
  actions?: ReactNode
  children: ReactNode
  tone?: 'paper' | 'ink'
  className?: string
}) {
  return (
    <section className={['panel', `panel--${tone}`, className].filter(Boolean).join(' ')}>
      {(title || actions) && (
        <header className="panel__head">
          {title && <h2 className="panel__title">{title}</h2>}
          {meta && <span className="panel__meta mono">{meta}</span>}
          {actions && <div className="panel__actions">{actions}</div>}
        </header>
      )}
      <div className="panel__body">{children}</div>
    </section>
  )
}

export function Severity({ level, children }: { level: 'ok' | 'warn' | 'crit' | 'neutral'; children: ReactNode }) {
  return (
    <span className={`sev sev--${level}`}>
      <span className="dot" aria-hidden />
      {children}
    </span>
  )
}

export function Empty({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="empty">
      <p className="empty__title">{title}</p>
      {hint && <p className="empty__hint">{hint}</p>}
    </div>
  )
}

export function Loading({ label = 'Carregando' }: { label?: string }) {
  return (
    <div className="loading" role="status">
      <span className="loading__bar" aria-hidden />
      <span className="loading__label mono">{label}</span>
    </div>
  )
}

export function ErrorNote({ children }: { children: ReactNode }) {
  return (
    <p className="error-note mono" role="alert">
      {children}
    </p>
  )
}

/** Nota de 0 a 100 como anel; o tamanho do arco é o dado, a cor é a leitura rápida. */
export function ScoreRing({ score, size = 116 }: { score: number; size?: number }) {
  const tone = score >= 75 ? 'var(--ok)' : score >= 50 ? 'var(--warn)' : 'var(--crit)'
  const radius = size / 2 - 9
  const circumference = 2 * Math.PI * radius
  const filled = (Math.max(0, Math.min(100, score)) / 100) * circumference
  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="score-ring" role="img"
         aria-label={`Pontuação ${Math.round(score)} de 100`}>
      <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke="var(--paper-sunken)" strokeWidth="9" />
      <circle
        cx={size / 2}
        cy={size / 2}
        r={radius}
        fill="none"
        stroke={tone}
        strokeWidth="9"
        strokeLinecap="butt"
        strokeDasharray={`${filled} ${circumference - filled}`}
        transform={`rotate(-90 ${size / 2} ${size / 2})`}
      />
      <text x="50%" y="50%" textAnchor="middle" dominantBaseline="central" className="score-ring__value">
        {Math.round(score)}
      </text>
    </svg>
  )
}

/** Trilha de nível: júnior → staff, com o atual destacado. */
export function RankTrack({ rank }: { rank: string }) {
  const levels = ['JUNIOR', 'PLENO', 'SENIOR', 'STAFF']
  const labels: Record<string, string> = { JUNIOR: 'júnior', PLENO: 'pleno', SENIOR: 'sênior', STAFF: 'staff' }
  const current = levels.indexOf(rank)
  return (
    <ol className="rank-track">
      {levels.map((level, index) => (
        <li
          key={level}
          className={[
            'rank-track__step',
            index < current ? 'rank-track__step--done' : '',
            index === current ? 'rank-track__step--current' : '',
          ]
            .filter(Boolean)
            .join(' ')}
        >
          {labels[level]}
        </li>
      ))}
    </ol>
  )
}
