import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import type { AttemptView } from '../api/types'
import { CASE_TYPE_LABEL, duration } from '../api/format'
import { Markdown } from '../components/Markdown'
import { Panel, ScoreRing, Severity } from '../components/ui'
import './result.css'

export function Result({ attempt }: { attempt: AttemptView }) {
  const navigate = useNavigate()
  const evaluation = attempt.evaluation
  const theCase = attempt.theCase
  const [showReference, setShowReference] = useState(false)
  const [startingFollowUp, setStartingFollowUp] = useState(false)

  if (!evaluation) {
    return (
      <div className="page">
        <p>Esta tentativa foi enviada, mas não tem avaliação registrada.</p>
      </div>
    )
  }

  const followed = theCase.solutionPaths.find((path) => path.id === evaluation.followedPathId)
  const recommendedId = evaluation.comparison?.recommendedPathId
  const followedRecommended = recommendedId === evaluation.followedPathId

  return (
    <div className="page result">
      <header className="result__head">
        <div className="result__identity">
          <p className="result__kicker mono">
            {CASE_TYPE_LABEL[theCase.type]} · {theCase.companyId} · {duration(attempt.durationSeconds)}
          </p>
          <h1 className="result__title">{theCase.narrative.title}</h1>
          {followed && (
            <p className="result__path">
              Caminho seguido: <strong>{followed.name}</strong>
              {followedRecommended ? (
                <Severity level="ok">o mais indicado aqui</Severity>
              ) : (
                <Severity level="warn">caminho válido, não o mais indicado</Severity>
              )}
            </p>
          )}
        </div>
        <ScoreRing score={evaluation.totalScore} />
      </header>

      <div className="result__grid">
        <Panel title="Nota por critério">
          <ul className="criteria">
            {evaluation.criteria.map((criterion) => {
              const ratio = criterion.weight === 0 ? 0 : criterion.score / criterion.weight
              const tone = ratio >= 0.99 ? 'ok' : ratio >= 0.5 ? 'warn' : 'crit'
              return (
                <li key={criterion.id} className="criteria__row">
                  <div className="criteria__head">
                    <span className="criteria__name">{criterion.name}</span>
                    <span className="criteria__score mono">
                      {formatScore(criterion.score)}/{criterion.weight}
                    </span>
                  </div>
                  <span className="criteria__track">
                    <span className={`criteria__fill criteria__fill--${tone}`} style={{ width: `${ratio * 100}%` }} />
                  </span>
                  <p className="criteria__feedback">{criterion.feedback}</p>
                </li>
              )
            })}
          </ul>
        </Panel>

        <div className="result__side">
          {evaluation.strengths.length > 0 && (
            <Panel title="O que foi bem">
              <ul className="result__list result__list--ok">
                {evaluation.strengths.map((item, index) => (
                  <li key={index}>{item}</li>
                ))}
              </ul>
            </Panel>
          )}

          {evaluation.gaps.length > 0 && (
            <Panel title="O que faltou">
              <ul className="result__list result__list--gap">
                {evaluation.gaps.map((item, index) => (
                  <li key={index}>{item}</li>
                ))}
              </ul>
            </Panel>
          )}

          {evaluation.investigationNotes.length > 0 && (
            <Panel title="Como você investigou">
              <ul className="result__list">
                {evaluation.investigationNotes.map((item, index) => (
                  <li key={index}>{item}</li>
                ))}
              </ul>
            </Panel>
          )}
        </div>
      </div>

      {evaluation.comparison && (
        <Panel title="Caminhos possíveis" className="result__paths">
          <p className="result__comparison">{evaluation.comparison.message}</p>
          {evaluation.comparison.alternatives.length > 0 && (
            <div className="alternatives">
              {evaluation.comparison.alternatives.map((alternative) => (
                <article key={alternative.id} className="alternative">
                  <h3 className="alternative__name">{alternative.name}</h3>
                  <p className="alternative__when">Indicado quando: {alternative.bestWhen}</p>
                  {alternative.pros.length > 0 && (
                    <p className="alternative__line">
                      <span className="alternative__key mono">a favor</span> {alternative.pros.join('; ')}
                    </p>
                  )}
                  {alternative.cons.length > 0 && (
                    <p className="alternative__line">
                      <span className="alternative__key mono">custo</span> {alternative.cons.join('; ')}
                    </p>
                  )}
                </article>
              ))}
            </div>
          )}
        </Panel>
      )}

      <Panel
        title="Resposta de referência"
        meta="como um sênior resolveria"
        actions={
          <button className="btn btn--quiet btn--small" type="button" onClick={() => setShowReference((on) => !on)}>
            {showReference ? 'Esconder' : 'Mostrar'}
          </button>
        }
      >
        {showReference ? (
          <Markdown className="result__reference">{evaluation.referenceMarkdown}</Markdown>
        ) : (
          <p className="muted">
            Compare depois de reler a sua. A referência resolve o mesmo cenário, com os mesmos números.
          </p>
        )}
      </Panel>

      {attempt.finalAnswer && (
        <Panel title="O que você escreveu">
          <Markdown>{longestText(attempt)}</Markdown>
        </Panel>
      )}

      {attempt.followUp && (
        <section className="follow-up">
          <h2 className="follow-up__title">Duas semanas depois</h2>
          <p className="follow-up__teaser">{attempt.followUp.teaser}</p>
          <button
            className="btn btn--primary"
            type="button"
            disabled={startingFollowUp}
            onClick={() => {
              setStartingFollowUp(true)
              void api
                .startCase(undefined, undefined, theCase.level, attempt.followUp!.templateId)
                .then((next) => navigate(`/caso/${next.id}`))
                .catch(() => setStartingFollowUp(false))
            }}
          >
            {startingFollowUp ? 'Abrindo' : 'Ver a consequência'}
          </button>
        </section>
      )}

      <div className="result__actions">
        <button className="btn btn--primary" type="button" onClick={() => navigate('/')}>
          Próximo caso
        </button>
        <a className="btn btn--quiet" href={api.exportUrl(attempt.id)} download>
          Exportar em markdown
        </a>
        <button className="btn btn--quiet" type="button" onClick={() => navigate('/progresso')}>
          Ver progresso
        </button>
      </div>
    </div>
  )
}

function longestText(attempt: AttemptView): string {
  const answer = attempt.finalAnswer
  if (!answer) return ''
  let longest = answer.markdown ?? ''
  for (const value of Object.values(answer.fields?.values ?? {})) {
    if (typeof value === 'string' && value.length > longest.length) longest = value
  }
  return longest.trim() || '_(sem texto)_'
}

const formatScore = (score: number) => (Number.isInteger(score) ? String(score) : score.toFixed(1))
