import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import type { DeckView, ReviewCard, ReviewGrade, ReviewResult } from '../api/types'
import { Markdown } from '../components/Markdown'
import { Empty, ErrorNote, Loading, Panel } from '../components/ui'
import './study.css'

const GRADES: { grade: ReviewGrade; label: string; hint: string }[] = [
  { grade: 'AGAIN', label: 'Errei', hint: 'volta amanhã' },
  { grade: 'HARD', label: 'Difícil', hint: 'intervalo menor' },
  { grade: 'GOOD', label: 'Acertei', hint: 'intervalo normal' },
  { grade: 'EASY', label: 'Fácil', hint: 'intervalo maior' },
]

export function StudyPage() {
  const [decks, setDecks] = useState<DeckView[] | null>(null)
  const [deckId, setDeckId] = useState<string | null>(null)
  const [card, setCard] = useState<ReviewCard | null>(null)
  const [revealed, setRevealed] = useState(false)
  const [lastResult, setLastResult] = useState<ReviewResult | null>(null)
  const [finished, setFinished] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)

  const loadDecks = useCallback(async () => {
    try {
      setDecks(await api.decks())
    } catch (error) {
      setFailure(error instanceof Error ? error.message : 'Falha ao carregar os decks')
    }
  }, [])

  useEffect(() => {
    void loadDecks()
  }, [loadDecks])

  const drawNext = useCallback(async (deck: string) => {
    setRevealed(false)
    const next = await api.nextCard(deck)
    setCard(next ?? null)
    setFinished(!next)
  }, [])

  async function grade(value: ReviewGrade) {
    if (!card || !deckId) return
    const result = await api.reviewCard(card.cardId, value)
    setLastResult(result)
    await Promise.all([drawNext(deckId), loadDecks()])
  }

  if (failure) return <div className="page"><ErrorNote>{failure}</ErrorNote></div>
  if (!decks) return <div className="page"><Loading label="abrindo os decks" /></div>

  if (deckId && card) {
    return (
      <div className="page study">
        <header className="study__session-head">
          <button
            type="button"
            className="btn btn--quiet btn--small"
            onClick={() => {
              setDeckId(null)
              setCard(null)
              setLastResult(null)
            }}
          >
            Sair da revisão
          </button>
          <span className="study__remaining mono">{card.remainingToday} card(s) na fila de hoje</span>
        </header>

        <article className="card">
          <div className="card__face card__face--front">
            <Markdown>{card.front}</Markdown>
          </div>
          {revealed ? (
            <div className="card__face card__face--back">
              <Markdown>{card.back}</Markdown>
            </div>
          ) : (
            <button type="button" className="card__reveal" onClick={() => setRevealed(true)}>
              Mostrar resposta
            </button>
          )}
          {revealed && (
            <div className="card__grades">
              {GRADES.map((option) => (
                <button
                  key={option.grade}
                  type="button"
                  className={`card__grade card__grade--${option.grade.toLowerCase()}`}
                  onClick={() => void grade(option.grade)}
                >
                  <span className="card__grade-label">{option.label}</span>
                  <span className="card__grade-hint mono">{option.hint}</span>
                </button>
              ))}
            </div>
          )}
          {card.lapses > 0 && (
            <p className="card__meta mono">
              você já esqueceu este card {card.lapses}× · revisão {card.repetitions + 1}
            </p>
          )}
        </article>

        {lastResult && (
          <p className="study__last mono">
            card anterior volta em {lastResult.intervalDays} dia(s) · facilidade {lastResult.easeFactor.toFixed(2)}
          </p>
        )}
      </div>
    )
  }

  if (deckId && finished) {
    return (
      <div className="page study">
        <Panel title="Fila vazia">
          <Empty
            title="Nada para revisar neste deck agora"
            hint="A repetição espaçada decide a hora certa. Volte quando o próximo card vencer, ou escolha outro tema."
          />
          <div className="study__done-actions">
            <button type="button" className="btn btn--quiet" onClick={() => setDeckId(null)}>
              Voltar aos decks
            </button>
          </div>
        </Panel>
      </div>
    )
  }

  const totalDue = decks.reduce((sum, deck) => sum + deck.dueCount, 0)

  return (
    <div className="page study">
      <header className="page__head">
        <h1 className="page__title">Estudo</h1>
        <p className="page__lede">
          Cem cards de base mais os que os seus casos resolvidos geraram. A revisão usa repetição espaçada: o
          intervalo cresce quando você acerta e volta a um dia quando erra.
        </p>
      </header>

      {totalDue === 0 ? (
        <Empty
          title="Nenhum card vencido hoje"
          hint="Resolva um caso para gerar material novo, ou volte amanhã para a próxima rodada."
        />
      ) : (
        <p className="study__summary">
          <strong>{totalDue}</strong> card(s) vencidos hoje, em {decks.filter((deck) => deck.dueCount > 0).length} tema(s).
        </p>
      )}

      <div className="study__decks">
        {decks.map((deck) => (
          <article key={deck.id} className={`deck ${deck.dueCount > 0 ? 'deck--due' : ''}`}>
            <header className="deck__head">
              <h2 className="deck__name">{deck.name}</h2>
              <span className="deck__count mono">{deck.totalCards} cards</span>
            </header>
            <p className="deck__description">{deck.description}</p>
            <dl className="deck__stats">
              <div>
                <dt>vencidos</dt>
                <dd className={`mono ${deck.dueCount > 0 ? 'deck__due-count' : ''}`}>{deck.dueCount}</dd>
              </div>
              <div>
                <dt>novos</dt>
                <dd className="mono">{deck.newCount}</dd>
              </div>
              <div>
                <dt>dos seus casos</dt>
                <dd className="mono">{deck.generatedCards}</dd>
              </div>
            </dl>
            <button
              type="button"
              className={deck.dueCount > 0 ? 'btn btn--primary btn--small' : 'btn btn--quiet btn--small'}
              disabled={deck.dueCount === 0}
              onClick={() => {
                setDeckId(deck.id)
                setFinished(false)
                void drawNext(deck.id)
              }}
            >
              {deck.dueCount > 0 ? `Revisar ${deck.dueCount}` : 'Em dia'}
            </button>
          </article>
        ))}
      </div>
    </div>
  )
}
