import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import type {
  AttemptView,
  CheckpointFeedback,
  FieldSpec,
  MitigationResult,
  SolutionGraph,
} from '../api/types'
import { CASE_TYPE_LABEL, LEVEL_LABEL, clock, duration } from '../api/format'
import { Console } from '../components/console/Console'
import { Markdown } from '../components/Markdown'
import { MarkdownEditor } from '../components/MarkdownEditor'
import { ArchitectureDesigner } from '../components/designer/ArchitectureDesigner'
import { ErrorNote, Loading, Severity } from '../components/ui'
import { Result } from './Result'
import './attempt.css'

const SECTIONS: Record<string, string[]> = {
  INCIDENT: ['Impacto', 'Linha do tempo', 'Causa raiz', 'Ações'],
  ADR: ['Contexto', 'Opções consideradas', 'Decisão', 'Consequências'],
  SLOW_QUERY: ['Diagnóstico', 'Correção', 'Mitigação', 'Validação', 'Prevenção'],
  MIGRATION: ['Diagnóstico', 'Estratégia', 'Rollout', 'Riscos'],
  CAPACITY_PLANNING: ['Projeção', 'Gargalo', 'Plano', 'Gatilhos'],
}

export function AttemptPage() {
  const { attemptId = '' } = useParams()
  const navigate = useNavigate()
  const [attempt, setAttempt] = useState<AttemptView | null>(null)
  const [failure, setFailure] = useState<string | null>(null)

  const reload = useCallback(async () => {
    try {
      setAttempt(await api.attempt(attemptId))
    } catch (error) {
      setFailure(error instanceof Error ? error.message : 'Tentativa não encontrada')
    }
  }, [attemptId])

  useEffect(() => {
    void reload()
  }, [reload])

  if (failure) {
    return (
      <div className="page">
        <ErrorNote>{failure}</ErrorNote>
        <button className="btn btn--quiet" type="button" onClick={() => navigate('/')}>
          Voltar
        </button>
      </div>
    )
  }
  if (!attempt) return <div className="page"><Loading label="montando o cenário" /></div>

  if (attempt.status === 'SUBMITTED') return <Result attempt={attempt} />
  return <Workbench attempt={attempt} reload={reload} />
}

function Workbench({ attempt, reload }: { attempt: AttemptView; reload: () => Promise<void> }) {
  const navigate = useNavigate()
  const theCase = attempt.theCase
  const [side, setSide] = useState<'briefing' | 'console'>('briefing')
  const [feedback, setFeedback] = useState<Record<string, CheckpointFeedback>>({})
  const [fields, setFields] = useState<Record<string, string>>({})
  const [graph, setGraph] = useState<SolutionGraph>({ nodes: [], edges: [] })
  const [statusText, setStatusText] = useState('')
  const [mitigation, setMitigation] = useState<MitigationResult | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [elapsed, setElapsed] = useState(attempt.elapsedSeconds)

  useEffect(() => {
    const timer = window.setInterval(() => setElapsed((seconds) => seconds + 1), 1000)
    return () => window.clearInterval(timer)
  }, [])

  // Stakeholders chegam com o tempo; o servidor decide quais já apareceram.
  useEffect(() => {
    const timer = window.setInterval(() => {
      void reload()
    }, 60_000)
    return () => window.clearInterval(timer)
  }, [reload])

  const markdownField = useMemo(
    () => theCase.answerForm.fields.find((field) => field.type === 'MARKDOWN') ?? null,
    [theCase],
  )
  const sections = SECTIONS[theCase.type] ?? []

  async function answerCheckpoint(checkpointId: string, optionId: string, proceedAnyway = false) {
    const result = await api.answerCheckpoint(attempt.id, checkpointId, optionId, proceedAnyway)
    setFeedback((current) => ({ ...current, [checkpointId]: result }))
    await reload()
  }

  async function submit() {
    setSubmitting(true)
    setSubmitError(null)
    try {
      const markdown = markdownField ? (fields[markdownField.id] ?? '') : ''
      await api.submit(attempt.id, {
        fields,
        markdown,
        graph: graph.nodes.length > 0 ? graph : null,
      })
      await reload()
      window.scrollTo({ top: 0 })
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : 'Não foi possível enviar')
      setSubmitting(false)
    }
  }

  const missingRequired = theCase.answerForm.fields.filter(
    (field) => field.required && !(fields[field.id] ?? '').trim(),
  )

  return (
    <div className="workbench">
      <header className="workbench__bar">
        <button type="button" className="workbench__back" onClick={() => navigate('/')} title="Sair do caso">
          ←
        </button>
        <span className="workbench__company mono">{theCase.companyId}</span>
        <span className="workbench__type">{CASE_TYPE_LABEL[theCase.type]}</span>
        <span className="workbench__level mono">{LEVEL_LABEL[theCase.level]}</span>
        <span className="spacer" />
        {attempt.resolvedAt && <Severity level="ok">incidente mitigado</Severity>}
        <span className="workbench__clock mono" title="tempo decorrido">
          {clock(elapsed)}
        </span>
        <div className="workbench__switch" role="tablist" aria-label="Alternar painel">
          <button
            type="button"
            role="tab"
            aria-selected={side === 'briefing'}
            className={side === 'briefing' ? 'on' : ''}
            onClick={() => setSide('briefing')}
          >
            Briefing
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={side === 'console'}
            className={side === 'console' ? 'on' : ''}
            onClick={() => setSide('console')}
          >
            Console
          </button>
        </div>
      </header>

      <div className={`workbench__split workbench__split--${side}`}>
        <section className="workbench__paper">
          <article className="briefing">
            <h1 className="briefing__title">{theCase.narrative.title}</h1>
            <Markdown className="briefing__symptom">{theCase.narrative.symptom}</Markdown>

            <details className="briefing__context" open>
              <summary>Contexto de negócio</summary>
              <Markdown>{theCase.narrative.businessContext}</Markdown>
            </details>

            {theCase.narrative.hints.length > 0 && (
              <details className="briefing__hints">
                <summary>Dicas ({theCase.narrative.hints.length})</summary>
                <ul>
                  {theCase.narrative.hints.map((hint, index) => (
                    <li key={index}>{hint}</li>
                  ))}
                </ul>
              </details>
            )}

            {theCase.artifacts.map((artifact) => (
              <details key={artifact.id} className="briefing__artifact" open>
                <summary>{artifact.title}</summary>
                <pre className="briefing__code">
                  <code>{artifact.content}</code>
                </pre>
              </details>
            ))}
          </article>

          {attempt.stakeholderMessages.length > 0 && (
            <section className="stakeholders">
              <h2 className="workbench__section-title">Cobranças</h2>
              <ul className="stakeholders__list">
                {attempt.stakeholderMessages.map((message) => {
                  const answered = !attempt.pendingStakeholderMessages.some((pending) => pending.id === message.id)
                  return (
                    <li key={message.id} className={`stakeholder ${answered ? 'stakeholder--answered' : ''}`}>
                      <span className="stakeholder__from">{message.from}</span>
                      <p className="stakeholder__text">{message.text}</p>
                      {message.expectsUpdate && (
                        <span className={`stakeholder__flag mono ${answered ? 'stakeholder__flag--ok' : ''}`}>
                          {answered ? 'respondido' : 'aguardando status'}
                        </span>
                      )}
                    </li>
                  )
                })}
              </ul>
              <form
                className="stakeholders__form"
                onSubmit={(event) => {
                  event.preventDefault()
                  if (!statusText.trim()) return
                  void api.postStatus(attempt.id, statusText).then(() => {
                    setStatusText('')
                    return reload()
                  })
                }}
              >
                <label className="sr-only" htmlFor="status-update">
                  Status update
                </label>
                <textarea
                  id="status-update"
                  className="textarea stakeholders__input"
                  rows={2}
                  value={statusText}
                  placeholder="Impacto, o que se sabe, o que está sendo feito e quando vem a próxima atualização."
                  onChange={(event) => setStatusText(event.target.value)}
                />
                <button className="btn btn--small" type="submit" disabled={!statusText.trim()}>
                  Publicar status
                </button>
              </form>
              {attempt.statusUpdates.length > 0 && (
                <ol className="stakeholders__log">
                  {attempt.statusUpdates.map((update, index) => (
                    <li key={index}>
                      <span className="mono">+{duration(update.elapsedSeconds)}</span> {update.text}
                    </li>
                  ))}
                </ol>
              )}
            </section>
          )}

          {theCase.checkpoints.length > 0 && (
            <section className="checkpoints">
              <h2 className="workbench__section-title">Checkpoints</h2>
              {theCase.checkpoints.map((checkpoint) => {
                const answer = checkpoint.answer
                const card = feedback[checkpoint.id]
                return (
                  <article key={checkpoint.id} className="checkpoint">
                    <h3 className="checkpoint__question">{checkpoint.question}</h3>
                    <div className="checkpoint__options">
                      {checkpoint.options.map((option) => (
                        <button
                          key={option.id}
                          type="button"
                          className={`checkpoint__option ${answer?.optionId === option.id ? 'checkpoint__option--on' : ''}`}
                          onClick={() => void answerCheckpoint(checkpoint.id, option.id)}
                        >
                          {option.label}
                        </button>
                      ))}
                    </div>
                    {answer && (
                      <div className={`guidance guidance--${answer.verdict.toLowerCase()}`}>
                        <p className="guidance__text">{answer.feedback}</p>
                        {card?.requiresConfirmation && !answer.confirmed && (
                          <div className="guidance__choice">
                            <button
                              type="button"
                              className="btn btn--small btn--quiet"
                              onClick={() => setFeedback((current) => ({ ...current, [checkpoint.id]: { ...card, requiresConfirmation: false } }))}
                            >
                              Rever a escolha
                            </button>
                            <button
                              type="button"
                              className="btn btn--small"
                              onClick={() => void answerCheckpoint(checkpoint.id, answer.optionId, true)}
                            >
                              Seguir mesmo assim
                            </button>
                          </div>
                        )}
                        {answer.proceededDespiteWarning && (
                          <p className="guidance__note mono">você seguiu mesmo assim — isso entra na avaliação</p>
                        )}
                      </div>
                    )}
                  </article>
                )
              })}
            </section>
          )}

          {theCase.mitigations.length > 0 && (
            <section className="mitigations">
              <h2 className="workbench__section-title">Ações de mitigação</h2>
              <p className="mitigations__lede">
                Ações têm efeito real no cenário, e a avaliação registra se você agiu antes ou depois de investigar.
              </p>
              <div className="mitigations__grid">
                {theCase.mitigations.map((option) => {
                  const applied = attempt.appliedMitigations.find((record) => record.mitigationId === option.id)
                  return (
                    <button
                      key={option.id}
                      type="button"
                      className={`mitigation ${applied ? 'mitigation--applied' : ''}`}
                      disabled={Boolean(applied)}
                      onClick={() => {
                        void api.mitigate(attempt.id, option.id).then((result) => {
                          setMitigation(result)
                          return reload()
                        })
                      }}
                    >
                      <span className="mitigation__label">{option.label}</span>
                      <span className="mitigation__description">{option.description}</span>
                      {applied && <span className="mitigation__stamp mono">aplicada</span>}
                    </button>
                  )
                })}
              </div>
              {mitigation && (
                <div className={`guidance guidance--${mitigation.verdict.toLowerCase()}`}>
                  <p className="guidance__text">{mitigation.message}</p>
                </div>
              )}
            </section>
          )}

          <section className="answer">
            <h2 className="workbench__section-title">Sua resposta</h2>
            {theCase.answerForm.fields.map((field) => (
              <AnswerField
                key={field.id}
                field={field}
                value={fields[field.id] ?? ''}
                sections={field.type === 'MARKDOWN' ? sections : []}
                onChange={(value) => setFields((current) => ({ ...current, [field.id]: value }))}
                graph={graph}
                onGraphChange={setGraph}
              />
            ))}

            {submitError && <ErrorNote>{submitError}</ErrorNote>}

            <div className="answer__submit">
              <button
                className="btn btn--primary"
                type="button"
                disabled={submitting || missingRequired.length > 0}
                onClick={() => void submit()}
              >
                {submitting ? 'Avaliando' : 'Enviar resposta'}
              </button>
              {missingRequired.length > 0 && (
                <span className="answer__missing">
                  Falta preencher: {missingRequired.map((field) => field.label).join(', ')}.
                </span>
              )}
            </div>
          </section>
        </section>

        <section className="workbench__ink">
          <Console attemptId={attempt.id} tools={theCase.tools} />
        </section>
      </div>
    </div>
  )
}

function AnswerField({
  field,
  value,
  onChange,
  sections,
  graph,
  onGraphChange,
}: {
  field: FieldSpec
  value: string
  onChange: (value: string) => void
  sections: string[]
  graph: SolutionGraph
  onGraphChange: (graph: SolutionGraph) => void
}) {
  const id = `field-${field.id}`

  if (field.type === 'DIAGRAM') {
    return (
      <div className="field">
        <span className="field__label">{field.label}</span>
        {field.help && <span className="field__help">{field.help}</span>}
        <ArchitectureDesigner value={graph} onChange={onGraphChange} />
      </div>
    )
  }

  return (
    <div className="field">
      <label className="field__label" htmlFor={id}>
        {field.label}
        {field.required && <span className="field__required"> obrigatório</span>}
      </label>
      {field.help && <span className="field__help">{field.help}</span>}

      {field.type === 'SELECT' && (
        <select id={id} className="select" value={value} onChange={(event) => onChange(event.target.value)}>
          <option value="">escolha</option>
          {field.options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      )}

      {field.type === 'MULTI_SELECT' && (
        <div className="field__checks">
          {field.options.map((option) => {
            const selected = value.split(',').map((item) => item.trim()).filter(Boolean)
            const checked = selected.includes(option.value)
            return (
              <label key={option.value} className="field__check">
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={() =>
                    onChange(
                      (checked ? selected.filter((item) => item !== option.value) : [...selected, option.value]).join(','),
                    )
                  }
                />
                {option.label}
              </label>
            )
          })}
        </div>
      )}

      {field.type === 'NUMBER' && (
        <input
          id={id}
          className="input mono"
          inputMode="decimal"
          value={value}
          onChange={(event) => onChange(event.target.value)}
        />
      )}

      {field.type === 'TEXT' && (
        <input id={id} className="input" value={value} onChange={(event) => onChange(event.target.value)} />
      )}

      {field.type === 'SQL' && (
        <textarea
          id={id}
          className="textarea textarea--code"
          spellCheck={false}
          value={value}
          onChange={(event) => onChange(event.target.value)}
        />
      )}

      {field.type === 'MARKDOWN' && (
        <MarkdownEditor id={id} value={value} onChange={onChange} sections={sections} />
      )}
    </div>
  )
}
