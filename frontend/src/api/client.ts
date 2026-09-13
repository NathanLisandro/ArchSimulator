import type {
  AttemptSummary,
  AttemptView,
  CheckpointFeedback,
  CompanySummary,
  DeckView,
  LogSearchView,
  MetricsView,
  MitigationResult,
  ProgressView,
  ReviewCard,
  ReviewGrade,
  ReviewResult,
  SolutionGraph,
  SprintView,
  SqlResult,
  TimelineView,
  Trace,
  User,
} from './types'

export class ApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message)
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    credentials: 'same-origin',
    headers: init?.body ? { 'Content-Type': 'application/json' } : undefined,
    ...init,
  })
  if (response.status === 204) return undefined as T
  const text = await response.text()
  if (!response.ok) {
    let message = text || response.statusText
    try {
      const parsed = JSON.parse(text) as { error?: string }
      if (parsed.error) message = parsed.error
    } catch {
      /* resposta não é JSON: mantém o texto cru */
    }
    throw new ApiError(response.status, message)
  }
  return text ? (JSON.parse(text) as T) : (undefined as T)
}

const post = <T>(path: string, body?: unknown) =>
  request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) })

export const api = {
  register: (username: string, displayName: string, password: string) =>
    post<User>('/api/users', { username, displayName, password }),
  login: (username: string, password: string) => post<User>('/api/auth/login', { username, password }),
  logout: () => post<void>('/api/auth/logout'),
  me: () => request<User>('/api/auth/me'),

  companies: () => request<CompanySummary[]>('/api/companies'),
  company: (id: string) => request<CompanySummary>(`/api/companies/${id}`),

  startCase: (companyId?: string, caseType?: string, level?: string, templateId?: string) =>
    post<AttemptView>('/api/attempts', { companyId, caseType, level, templateId }),
  attempt: (id: string) => request<AttemptView>(`/api/attempts/${id}`),
  answerCheckpoint: (attemptId: string, checkpointId: string, optionId: string, proceedAnyway = false) =>
    post<CheckpointFeedback>(`/api/attempts/${attemptId}/checkpoints/${checkpointId}`, { optionId, proceedAnyway }),
  submit: (
    attemptId: string,
    answer: { fields: Record<string, string>; markdown: string; mermaid?: string | null; graph?: SolutionGraph | null },
  ) => post<AttemptView>(`/api/attempts/${attemptId}/submit`, answer),

  runSql: (attemptId: string, sql: string) => post<SqlResult>(`/api/attempts/${attemptId}/console/sql`, { sql }),
  metrics: (attemptId: string, service?: string, metrics?: string[]) => {
    const params = new URLSearchParams()
    if (service) params.set('service', service)
    metrics?.forEach((m) => params.append('metric', m))
    return request<MetricsView>(`/api/attempts/${attemptId}/console/metrics?${params}`)
  },
  searchLogs: (attemptId: string, query: string) =>
    post<LogSearchView>(`/api/attempts/${attemptId}/console/logs`, { query }),
  traces: (attemptId: string, traceId?: string) =>
    request<{ traces: Trace[]; total: number }>(
      `/api/attempts/${attemptId}/console/traces${traceId ? `?traceId=${encodeURIComponent(traceId)}` : ''}`,
    ),
  timeline: (attemptId: string) => request<TimelineView>(`/api/attempts/${attemptId}/console/timeline`),

  mitigate: (attemptId: string, mitigationId: string) =>
    post<MitigationResult>(`/api/attempts/${attemptId}/mitigations`, { mitigationId }),
  postStatus: (attemptId: string, text: string) => post<AttemptView>(`/api/attempts/${attemptId}/status-updates`, { text }),

  progress: () => request<ProgressView>('/api/progress'),
  history: () => request<AttemptSummary[]>('/api/attempts'),
  exportUrl: (attemptId: string) => `/api/attempts/${attemptId}/export`,

  startSprint: (totalCases: number, minutes: number, level: string, caseType?: string) =>
    post<SprintView>('/api/sprints', { totalCases, minutes, level, caseType }),
  currentSprint: () => request<SprintView | undefined>('/api/sprints/current'),
  sprint: (id: string) => request<SprintView>(`/api/sprints/${id}`),

  decks: () => request<DeckView[]>('/api/study/decks'),
  nextCard: (deckId: string) => request<ReviewCard | undefined>(`/api/study/decks/${encodeURIComponent(deckId)}/next`),
  reviewCard: (cardId: string, grade: ReviewGrade) =>
    post<ReviewResult>(`/api/study/cards/${encodeURIComponent(cardId)}/review`, { grade }),
}
