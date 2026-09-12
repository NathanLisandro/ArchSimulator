import { useState } from 'react'
import { api } from '../../api/client'
import type { LogSearchView } from '../../api/types'
import { hhmmss } from '../../api/format'
import { Empty, ErrorNote, Loading } from '../ui'

const EXAMPLES = [
  'level=ERROR',
  'service=account-api level=ERROR | stats count by service',
  '"Connection timeout"',
  'latency>1000 | sort -latency | head 20',
  '| timechart span=5m count by level',
]

export function LogSearch({ attemptId }: { attemptId: string }) {
  const [query, setQuery] = useState('level=ERROR | stats count by service | sort -count')
  const [result, setResult] = useState<LogSearchView | null>(null)
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)

  async function run(next = query) {
    setBusy(true)
    setFailure(null)
    try {
      setResult(await api.searchLogs(attemptId, next))
    } catch (error) {
      setFailure(error instanceof Error ? error.message : 'Falha ao buscar')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="logs">
      <form
        className="logs__form"
        onSubmit={(event) => {
          event.preventDefault()
          void run()
        }}
      >
        <label className="sr-only" htmlFor="log-query">
          Consulta de logs
        </label>
        <textarea
          id="log-query"
          className="logs__input mono"
          value={query}
          rows={2}
          spellCheck={false}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
              event.preventDefault()
              void run()
            }
          }}
        />
        <div className="logs__actions">
          <button className="btn btn--primary btn--small" type="submit" disabled={busy}>
            Buscar
          </button>
          <span className="logs__hint mono">ctrl + enter</span>
        </div>
      </form>

      <div className="logs__examples">
        {EXAMPLES.map((example) => (
          <button
            key={example}
            type="button"
            className="logs__example mono"
            onClick={() => {
              setQuery(example)
              void run(example)
            }}
          >
            {example}
          </button>
        ))}
      </div>

      {busy && <Loading label="buscando" />}
      {failure && <ErrorNote>{failure}</ErrorNote>}
      {result?.error && <ErrorNote>{result.error}</ErrorNote>}

      {result && !result.error && (
        <>
          <p className="logs__summary mono">
            {result.totalMatched} linha(s) casaram
            {result.truncated && ` · exibindo ${result.events.length}`}
          </p>
          {result.kind === 'EVENTS' && <EventList result={result} />}
          {(result.kind === 'TABLE' || result.kind === 'TIMECHART') && <ResultTable result={result} />}
        </>
      )}

      {!result && !busy && !failure && (
        <Empty
          title="Nenhuma busca ainda"
          hint="Filtre por campo, combine com stats ou timechart. Os dados são os do incidente deste caso."
        />
      )}
    </div>
  )
}

function EventList({ result }: { result: LogSearchView }) {
  if (result.events.length === 0) return <Empty title="Nada casou com essa consulta" />
  return (
    <ol className="logs__events">
      {result.events.map((entry, index) => (
        <li key={index} className={`logs__event logs__event--${entry.level.toLowerCase()}`}>
          <span className="logs__time mono">{hhmmss(entry.timestamp)}</span>
          <span className={`logs__level mono logs__level--${entry.level.toLowerCase()}`}>{entry.level}</span>
          <span className="logs__service mono">{entry.service}</span>
          <span className="logs__message">{entry.message}</span>
          {entry.latencyMs !== null && <span className="logs__latency mono">{entry.latencyMs}ms</span>}
          <span className="logs__fields mono">
            {entry.traceId && <span title="traceId">trace={entry.traceId.slice(0, 12)}</span>}
            {Object.entries(entry.fields)
              .filter(([key]) => key !== 'env')
              .map(([key, value]) => (
                <span key={key}>
                  {key}={value}
                </span>
              ))}
          </span>
        </li>
      ))}
    </ol>
  )
}

function ResultTable({ result }: { result: LogSearchView }) {
  if (result.rows.length === 0) return <Empty title="Nada casou com essa consulta" />
  const numeric = result.columns.map((_, index) => result.rows.every((row) => typeof row[index] === 'number'))
  return (
    <div className="logs__table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            {result.columns.map((column, index) => (
              <th key={column} className={numeric[index] ? 'num' : undefined}>
                {column}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {result.rows.map((row, rowIndex) => (
            <tr key={rowIndex}>
              {row.map((cell, index) => (
                <td key={index} className={numeric[index] ? 'num' : undefined}>
                  {formatCell(cell)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function formatCell(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'number') return Number.isInteger(value) ? String(value) : value.toFixed(2)
  const text = String(value)
  return /^\d{4}-\d{2}-\d{2}T/.test(text) ? hhmmss(text) : text
}
