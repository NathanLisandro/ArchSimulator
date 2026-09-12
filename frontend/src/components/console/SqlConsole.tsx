import { useState } from 'react'
import { api } from '../../api/client'
import type { SqlResult } from '../../api/types'
import { Empty, ErrorNote, Loading } from '../ui'

const SNIPPETS: { label: string; sql: string }[] = [
  {
    label: 'queries mais caras',
    sql: 'SELECT query, calls, round(mean_exec_time) AS media_ms, round(total_exec_time) AS total_ms\nFROM pg_stat_statements\nORDER BY mean_exec_time DESC\nLIMIT 10',
  },
  {
    label: 'queries mais chamadas',
    sql: 'SELECT query, calls, round(mean_exec_time) AS media_ms\nFROM pg_stat_statements\nORDER BY calls DESC\nLIMIT 10',
  },
  {
    label: 'sessões ativas',
    sql: "SELECT pid, state, wait_event_type, wait_event, query\nFROM pg_stat_activity\nWHERE state <> 'idle'",
  },
  { label: 'locks', sql: 'SELECT pid, relation, mode, granted, waiting_ms FROM pg_locks ORDER BY waiting_ms DESC' },
  {
    label: 'varreduras por tabela',
    sql: 'SELECT relname, seq_scan, idx_scan, n_live_tup, n_dead_tup\nFROM pg_stat_user_tables\nORDER BY seq_scan DESC',
  },
  { label: 'índices existentes', sql: 'SELECT tablename, indexname, indexdef FROM pg_indexes' },
  { label: 'tamanho das tabelas', sql: 'SELECT * FROM table_sizes ORDER BY total_size_mb DESC' },
]

export function SqlConsole({ attemptId }: { attemptId: string }) {
  const [sql, setSql] = useState(SNIPPETS[0].sql)
  const [result, setResult] = useState<SqlResult | null>(null)
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)

  async function run(statement = sql) {
    setBusy(true)
    setFailure(null)
    try {
      setResult(await api.runSql(attemptId, statement))
    } catch (error) {
      setFailure(error instanceof Error ? error.message : 'Falha ao executar')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="sql">
      <div className="sql__snippets">
        {SNIPPETS.map((snippet) => (
          <button
            key={snippet.label}
            type="button"
            className="chip"
            onClick={() => {
              setSql(snippet.sql)
              void run(snippet.sql)
            }}
          >
            {snippet.label}
          </button>
        ))}
      </div>

      <form
        className="sql__form"
        onSubmit={(event) => {
          event.preventDefault()
          void run()
        }}
      >
        <label className="sr-only" htmlFor="sql-input">
          Consulta SQL
        </label>
        <textarea
          id="sql-input"
          className="sql__input mono"
          rows={5}
          spellCheck={false}
          value={sql}
          onChange={(event) => setSql(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
              event.preventDefault()
              void run()
            }
          }}
        />
        <div className="sql__actions">
          <button className="btn btn--primary btn--small" type="submit" disabled={busy}>
            Executar
          </button>
          <button
            className="btn btn--ink btn--small"
            type="button"
            disabled={busy}
            onClick={() => {
              const explained = sql.trim().toLowerCase().startsWith('explain') ? sql : `EXPLAIN ${sql.trim()}`
              setSql(explained)
              void run(explained)
            }}
          >
            Explain
          </button>
          <span className="sql__hint mono">somente leitura · ctrl + enter</span>
        </div>
      </form>

      {busy && <Loading label="executando" />}
      {failure && <ErrorNote>{failure}</ErrorNote>}
      {result?.error && <ErrorNote>{result.error}</ErrorNote>}

      {result?.planText && (
        <pre className="sql__plan mono">
          <code>{result.planText}</code>
        </pre>
      )}

      {result && !result.error && !result.planText && (
        <>
          <p className="sql__summary mono">
            {result.rows.length} linha(s) · {result.elapsedMs}ms{result.truncated && ' · resultado truncado'}
          </p>
          {result.rows.length === 0 ? (
            <Empty title="Nenhuma linha retornada" />
          ) : (
            <div className="sql__table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    {result.columns.map((column) => (
                      <th key={column}>{column}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {result.rows.map((row, rowIndex) => (
                    <tr key={rowIndex}>
                      {row.map((cell, index) => (
                        <td key={index} className={typeof cell === 'number' ? 'num' : undefined}>
                          {renderCell(cell)}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {!result && !busy && !failure && (
        <Empty
          title="Console somente leitura"
          hint="SELECT, WITH e EXPLAIN. As tabelas do cenário trazem uma amostra; volumes reais estão em table_sizes e pg_stat_user_tables."
        />
      )}
    </div>
  )
}

function renderCell(value: unknown): string {
  if (value === null || value === undefined) return 'NULL'
  if (typeof value === 'number') return Number.isInteger(value) ? String(value) : value.toFixed(2)
  const text = String(value)
  return text.length > 160 ? `${text.slice(0, 160)}…` : text
}
