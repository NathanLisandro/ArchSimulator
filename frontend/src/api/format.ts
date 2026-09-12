const TIME = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit', timeZone: 'America/Sao_Paulo' })
const TIME_SECONDS = new Intl.DateTimeFormat('pt-BR', {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  timeZone: 'America/Sao_Paulo',
})
const DATE_TIME = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'America/Sao_Paulo',
})
const FULL = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'America/Sao_Paulo',
})

export const hhmm = (iso: string) => TIME.format(new Date(iso))
export const hhmmss = (iso: string) => TIME_SECONDS.format(new Date(iso))
export const dayTime = (iso: string) => DATE_TIME.format(new Date(iso))
export const fullDate = (iso: string) => FULL.format(new Date(iso))

/** 1.234.567 */
export const number = (value: number) => new Intl.NumberFormat('pt-BR').format(Math.round(value))

/** 4,3K · 81,2M — para eixos e rótulos apertados. */
export function compact(value: number): string {
  const abs = Math.abs(value)
  if (abs >= 1_000_000_000) return `${round(value / 1_000_000_000)}B`
  if (abs >= 1_000_000) return `${round(value / 1_000_000)}M`
  if (abs >= 1_000) return `${round(value / 1_000)}K`
  if (abs >= 100) return String(Math.round(value))
  if (abs >= 10) return round(value)
  return round(value, 2)
}

function round(value: number, decimals = 1): string {
  const fixed = value.toFixed(decimals)
  return fixed.replace(/\.?0+$/, '').replace('.', ',')
}

/** 1h 20min · 12min 30s · 45s */
export function duration(totalSeconds: number): string {
  const seconds = Math.max(0, Math.round(totalSeconds))
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const rest = seconds % 60
  if (hours > 0) return minutes > 0 ? `${hours}h ${minutes}min` : `${hours}h`
  if (minutes > 0) return rest > 0 ? `${minutes}min ${rest}s` : `${minutes}min`
  return `${rest}s`
}

/** 04:37 — para cronômetro regressivo. */
export function clock(totalSeconds: number): string {
  const seconds = Math.max(0, Math.round(totalSeconds))
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
}

export const CASE_TYPE_LABEL: Record<string, string> = {
  SLOW_QUERY: 'Query lenta',
  INCIDENT: 'Incidente',
  ADR: 'ADR',
  SOLUTION_DESIGN: 'Design de solução',
  TRADE_OFF: 'Trade-off',
  CODE_REVIEW: 'Code review',
  CAPACITY_PLANNING: 'Capacity planning',
  MIGRATION: 'Migração',
}

export const LEVEL_LABEL: Record<string, string> = {
  JUNIOR: 'júnior',
  PLENO: 'pleno',
  SENIOR: 'sênior',
  STAFF: 'staff',
}

export const TOOL_LABEL: Record<string, string> = {
  LOGS: 'Logs',
  METRICS: 'Métricas',
  TRACES: 'Traces',
  SQL: 'SQL',
  DEPLOYS: 'Linha do tempo',
  INFRA: 'Infraestrutura',
}

export const COMPETENCY_LABEL: Record<string, string> = {
  databases: 'bancos de dados',
  observability: 'observabilidade',
  incident: 'resposta a incidentes',
  sre: 'SRE',
  capacity: 'capacity planning',
  eda: 'arquitetura orientada a eventos',
  microservices: 'microserviços',
  ddd: 'DDD',
  solid: 'SOLID e padrões',
  'clean-arch': 'Clean Architecture',
  adr: 'ADRs',
  'system-design': 'system design',
  characteristics: 'características arquiteturais',
  caching: 'caching',
  concurrency: 'concorrência',
  kafka: 'Kafka',
  cloud: 'cloud',
  edge: 'edge',
  security: 'segurança',
  legacy: 'modernização de legado',
  communication: 'comunicação',
  leadership: 'liderança',
  nosql: 'NoSQL',
  kubernetes: 'Kubernetes',
  iac: 'infra as code',
}

export const competencyLabel = (id: string) => COMPETENCY_LABEL[id] ?? id.replace(/-/g, ' ')

/** Cor semântica de uma nota de 0 a 100. */
export function scoreTone(score: number): 'ok' | 'warn' | 'crit' {
  if (score >= 75) return 'ok'
  if (score >= 50) return 'warn'
  return 'crit'
}
