export type Level = 'JUNIOR' | 'PLENO' | 'SENIOR' | 'STAFF'
export type Verdict = 'GOOD' | 'RISKY' | 'BAD'
export type ConsoleTool = 'SQL' | 'METRICS' | 'LOGS' | 'TRACES' | 'DEPLOYS' | 'INFRA'
export type CaseType =
  | 'SLOW_QUERY'
  | 'INCIDENT'
  | 'ADR'
  | 'SOLUTION_DESIGN'
  | 'TRADE_OFF'
  | 'CODE_REVIEW'
  | 'CAPACITY_PLANNING'
  | 'MIGRATION'
export type FieldType = 'SELECT' | 'MULTI_SELECT' | 'TEXT' | 'SQL' | 'MARKDOWN' | 'DIAGRAM' | 'NUMBER'
export type ReviewGrade = 'AGAIN' | 'HARD' | 'GOOD' | 'EASY'

export interface User {
  id: string
  username: string
  displayName: string
}

export interface Narrative {
  title: string
  symptom: string
  businessContext: string
  hints: string[]
}

export interface FieldOption {
  value: string
  label: string
}

export interface FieldSpec {
  id: string
  label: string
  type: FieldType
  options: FieldOption[]
  required: boolean
  help: string | null
}

export interface CheckpointOption {
  id: string
  label: string
}

export interface CheckpointAnswerView {
  optionId: string
  verdict: Verdict
  feedback: string
  confirmed: boolean
  proceededDespiteWarning: boolean
}

export interface Checkpoint {
  id: string
  question: string
  options: CheckpointOption[]
  answer: CheckpointAnswerView | null
}

export interface PathView {
  id: string
  name: string
  summary: string
}

export interface MitigationOption {
  id: string
  label: string
  description: string
}

export interface StakeholderMessage {
  id: string
  from: string
  text: string
  expectsUpdate: boolean
  atElapsedSeconds: number
}

export interface CaseArtifact {
  id: string
  title: string
  kind: 'CODE' | 'SQL' | 'DIAGRAM' | 'CONFIG' | 'DOCUMENT' | 'LOG_SAMPLE'
  language: string
  content: string
}

export interface CaseView {
  id: string
  companyId: string
  type: CaseType
  level: Level
  narrative: Narrative
  checkpoints: Checkpoint[]
  solutionPaths: PathView[]
  answerForm: { fields: FieldSpec[]; pathFieldId: string }
  competencies: string[]
  tools: ConsoleTool[]
  mitigations: MitigationOption[]
  artifacts: CaseArtifact[]
}

export interface CriterionScore {
  id: string
  name: string
  weight: number
  score: number
  matched: number
  total: number
  feedback: string
}

export interface PathSummary {
  id: string
  name: string
  bestWhen: string
  pros: string[]
  cons: string[]
}

export interface Evaluation {
  totalScore: number
  followedPathId: string
  criteria: CriterionScore[]
  strengths: string[]
  gaps: string[]
  comparison: {
    followedPathId: string
    recommendedPathId: string
    message: string
    alternatives: PathSummary[]
  } | null
  referenceMarkdown: string
  investigationNotes: string[]
}

export interface ConsoleAction {
  kind: string
  target: string
  detail: string
  at: string
}

export interface MitigationRecord {
  mitigationId: string
  verdict: Verdict
  effect: 'RESOLVES' | 'PARTIAL' | 'NONE' | 'HARMFUL'
  informed: boolean
  appliedAt: string
}

export interface StatusUpdate {
  text: string
  postedAt: string
  elapsedSeconds: number
}

export interface AttemptView {
  id: string
  userId: string
  status: 'IN_PROGRESS' | 'SUBMITTED'
  startedAt: string
  submittedAt: string | null
  durationSeconds: number
  theCase: CaseView
  trail: ConsoleAction[]
  finalAnswer: { fields: { values: Record<string, string> }; markdown: string; mermaid: string | null } | null
  evaluation: Evaluation | null
  appliedMitigations: MitigationRecord[]
  statusUpdates: StatusUpdate[]
  stakeholderMessages: StakeholderMessage[]
  pendingStakeholderMessages: StakeholderMessage[]
  resolvedAt: string | null
  elapsedSeconds: number
  followUp: { templateId: string; teaser: string } | null
}

export interface CompanySummary {
  id: string
  name: string
  domain: string
  description: string
  scale: { dailyActiveUsers: number; peakTps: number; dataVolume: string; growthNote: string }
  stack: { languages: string[]; datastores: string[]; messaging: string[]; cloud: string }
  techDebts: { title: string; description: string }[]
  architecture: { services: { name: string; description: string; instances: number }[] }
  infra: { resources: { id: string; type: string; name: string }[]; edges: { from: string; to: string; label: string }[] }
  tables: { name: string; rowCount: number; sizeMb: number; columns: string[]; indexes: string[] }[]
}

export interface CheckpointFeedback {
  checkpointId: string
  optionId: string
  verdict: Verdict
  message: string
  requiresConfirmation: boolean
}

export interface SqlResult {
  columns: string[]
  rows: unknown[][]
  planText: string | null
  error: string | null
  elapsedMs: number
  truncated: boolean
}

export interface MetricPoint {
  at: string
  value: number
}

export interface MetricSeries {
  service: string
  metric: string
  unit: string
  points: MetricPoint[]
}

export interface MetricsView {
  from: string
  to: string
  services: string[]
  series: MetricSeries[]
}

export interface LogEntry {
  timestamp: string
  service: string
  level: string
  traceId: string
  latencyMs: number | null
  message: string
  fields: Record<string, string>
}

export interface LogSearchView {
  kind: 'EVENTS' | 'TABLE' | 'TIMECHART' | 'ERROR'
  events: LogEntry[]
  columns: string[]
  rows: unknown[][]
  totalMatched: number
  truncated: boolean
  error: string | null
}

export interface Span {
  spanId: string
  parentSpanId: string | null
  service: string
  operation: string
  startOffsetMs: number
  durationMs: number
  tags: Record<string, string>
}

export interface Trace {
  traceId: string
  startedAt: string
  durationMs: number
  rootService: string
  rootOperation: string
  spans: Span[]
}

export interface TimelineView {
  from: string
  to: string
  deploys: { at: string; service: string; version: string; author: string; description: string }[]
  flagChanges: { at: string; flag: string; from: string; to: string; actor: string; scope: string }[]
  alerts: { firedAt: string; name: string; service: string; severity: string; description: string }[]
  instances: { service: string; instanceId: string; status: string; cpuPercent: number; memoryPercent: number; restarts: number }[]
  infraResources: { id: string; type: string; name: string; properties: Record<string, string> }[]
  infraEdges: { from: string; to: string; label: string }[]
}

export interface MitigationResult {
  mitigationId: string
  verdict: Verdict
  effect: string
  message: string
  incidentResolved: boolean
}

export interface TypeStats {
  attempts: number
  totalScore: number
  bestScore: number
  totalSeconds: number
  averageScore?: number
}

export interface AttemptSummary {
  id: string
  caseId: string
  caseType: CaseType
  caseTitle: string
  companyId: string
  level: Level
  status: string
  score: number | null
  followedPath: string | null
  startedAt: string
  submittedAt: string | null
  durationSeconds: number
  sprintId: string | null
}

export interface ProgressView {
  rank: Level
  xp: number
  solvedCount: number
  averageScore: number
  byType: Record<string, TypeStats>
  byCompetency: Record<string, TypeStats>
  weakest: string[]
  strongest: string[]
  missingForNextRank: string
  suggestedNextType: CaseType
  recentAttempts: AttemptSummary[]
}

export interface SprintView {
  id: string
  status: 'RUNNING' | 'FINISHED' | 'EXPIRED'
  totalCases: number
  completedCases: number
  remainingCases: number
  remainingSeconds: number
  timeLimitSeconds: number
  level: Level
  caseType: CaseType | null
  averageScore: number
  startedAt: string
  deadline: string
  results: { attemptId: string; caseType: CaseType; score: number; elapsedSeconds: number }[]
}

export interface DeckView {
  id: string
  name: string
  description: string
  totalCards: number
  generatedCards: number
  dueCount: number
  newCount: number
}

export interface ReviewCard {
  cardId: string
  deckId: string
  front: string
  back: string
  remainingToday: number
  repetitions: number
  lapses: number
}

export interface ReviewResult {
  cardId: string
  intervalDays: number
  dueAt: string
  easeFactor: number
  remainingToday: number
}

export interface GraphNode {
  id: string
  type: string
  label: string
  props: Record<string, string>
}

export interface GraphEdge {
  from: string
  to: string
  label: string
  async: boolean
  props: Record<string, string>
}

export interface SolutionGraph {
  nodes: GraphNode[]
  edges: GraphEdge[]
}
