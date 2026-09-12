import { useState } from 'react'
import type { ConsoleTool } from '../../api/types'
import { TOOL_LABEL } from '../../api/format'
import { LogSearch } from './LogSearch'
import { MetricsPanel } from './MetricsPanel'
import { TraceViewer } from './TraceViewer'
import { SqlConsole } from './SqlConsole'
import { DeployTimeline, InfraMap } from './DeployTimeline'

const ORDER: ConsoleTool[] = ['METRICS', 'LOGS', 'TRACES', 'SQL', 'DEPLOYS', 'INFRA']

/**
 * O console é o lado da máquina. As abas aparecem na ordem em que costumam ser úteis numa investigação —
 * métrica para isolar o escopo, log e trace para descer ao detalhe, SQL e linha do tempo para confirmar.
 */
export function Console({ attemptId, tools }: { attemptId: string; tools: ConsoleTool[] }) {
  const available = ORDER.filter((tool) => tools.includes(tool))
  const [active, setActive] = useState<ConsoleTool | null>(available[0] ?? null)
  const [visited, setVisited] = useState<Set<ConsoleTool>>(new Set(available.slice(0, 1)))

  if (available.length === 0) {
    return (
      <div className="console console--empty">
        <p className="console__no-tools">
          Este caso se responde pelo briefing. Não há console: a decisão vem do que está escrito, não do que
          você consegue medir.
        </p>
      </div>
    )
  }

  return (
    <div className="console">
      <nav className="console__tabs" role="tablist" aria-label="Ferramentas do console">
        {available.map((tool) => (
          <button
            key={tool}
            type="button"
            role="tab"
            aria-selected={active === tool}
            className={`console__tab ${active === tool ? 'console__tab--on' : ''}`}
            onClick={() => {
              setActive(tool)
              setVisited((current) => new Set(current).add(tool))
            }}
          >
            <span className={`console__led ${visited.has(tool) ? 'console__led--seen' : ''}`} aria-hidden />
            {TOOL_LABEL[tool]}
          </button>
        ))}
      </nav>
      <div className="console__body" role="tabpanel">
        {active === 'LOGS' && <LogSearch attemptId={attemptId} />}
        {active === 'METRICS' && <MetricsPanel attemptId={attemptId} />}
        {active === 'TRACES' && <TraceViewer attemptId={attemptId} />}
        {active === 'SQL' && <SqlConsole attemptId={attemptId} />}
        {active === 'DEPLOYS' && <DeployTimeline attemptId={attemptId} />}
        {active === 'INFRA' && <InfraMap attemptId={attemptId} />}
      </div>
    </div>
  )
}
