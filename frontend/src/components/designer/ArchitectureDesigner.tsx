import { useCallback, useRef, useState } from 'react'
import type { GraphEdge, GraphNode, SolutionGraph } from '../../api/types'
import './designer.css'

const PALETTE: { type: string; label: string; hint: string }[] = [
  { type: 'service', label: 'Serviço', hint: 'API, worker, job' },
  { type: 'database', label: 'Banco', hint: 'relacional ou documento' },
  { type: 'queue', label: 'Fila / tópico', hint: 'Kafka, SQS' },
  { type: 'cache', label: 'Cache', hint: 'Redis, CDN' },
  { type: 'external', label: 'Terceiro', hint: 'parceiro, adquirente' },
  { type: 'client', label: 'Cliente', hint: 'app, navegador' },
]

const NODE_WIDTH = 150
const NODE_HEIGHT = 52

/**
 * Editor de arquitetura por arrastar e soltar. O que ele produz é avaliado estruturalmente pela rubrica:
 * existe fila entre A e B, a ligação é assíncrona, não existe chamada síncrona entre este par, a aresta
 * declara outbox. Por isso a assincronia é um atributo explícito da ligação, não um detalhe de estilo.
 */
export function ArchitectureDesigner({
  value,
  onChange,
}: {
  value: SolutionGraph
  onChange: (graph: SolutionGraph) => void
}) {
  const surface = useRef<HTMLDivElement>(null)
  const [connectingFrom, setConnectingFrom] = useState<string | null>(null)
  const [selected, setSelected] = useState<string | null>(null)
  const dragging = useRef<{ id: string; offsetX: number; offsetY: number } | null>(null)

  const positions = useCallback(
    (node: GraphNode) => ({ x: Number(node.props.x ?? 40), y: Number(node.props.y ?? 40) }),
    [],
  )

  function addNode(type: string, label: string) {
    const index = value.nodes.length
    const node: GraphNode = {
      id: `${type}-${Date.now().toString(36)}-${index}`,
      type,
      label,
      props: { x: String(40 + (index % 4) * 170), y: String(30 + Math.floor(index / 4) * 90) },
    }
    onChange({ ...value, nodes: [...value.nodes, node] })
    setSelected(node.id)
  }

  function updateNode(id: string, patch: Partial<GraphNode> & { props?: Record<string, string> }) {
    onChange({
      ...value,
      nodes: value.nodes.map((node) =>
        node.id === id ? { ...node, ...patch, props: { ...node.props, ...(patch.props ?? {}) } } : node,
      ),
    })
  }

  function removeNode(id: string) {
    onChange({
      nodes: value.nodes.filter((node) => node.id !== id),
      edges: value.edges.filter((edge) => edge.from !== id && edge.to !== id),
    })
    setSelected(null)
  }

  function connect(from: string, to: string) {
    if (from === to) return
    const exists = value.edges.some((edge) => edge.from === from && edge.to === to)
    if (exists) return
    const edge: GraphEdge = { from, to, label: '', async: false, props: {} }
    onChange({ ...value, edges: [...value.edges, edge] })
  }

  function updateEdge(index: number, patch: Partial<GraphEdge>) {
    onChange({ ...value, edges: value.edges.map((edge, i) => (i === index ? { ...edge, ...patch } : edge)) })
  }

  function removeEdge(index: number) {
    onChange({ ...value, edges: value.edges.filter((_, i) => i !== index) })
  }

  function onPointerDown(event: React.PointerEvent, node: GraphNode) {
    if (connectingFrom) {
      connect(connectingFrom, node.id)
      setConnectingFrom(null)
      return
    }
    const rect = surface.current?.getBoundingClientRect()
    if (!rect) return
    const { x, y } = positions(node)
    dragging.current = { id: node.id, offsetX: event.clientX - rect.left - x, offsetY: event.clientY - rect.top - y }
    setSelected(node.id)
    ;(event.target as Element).setPointerCapture?.(event.pointerId)
  }

  function onPointerMove(event: React.PointerEvent) {
    const drag = dragging.current
    const rect = surface.current?.getBoundingClientRect()
    if (!drag || !rect) return
    const x = Math.max(0, Math.min(rect.width - NODE_WIDTH, event.clientX - rect.left - drag.offsetX))
    const y = Math.max(0, Math.min(rect.height - NODE_HEIGHT, event.clientY - rect.top - drag.offsetY))
    updateNode(drag.id, { props: { x: String(Math.round(x)), y: String(Math.round(y)) } })
  }

  const selectedNode = value.nodes.find((node) => node.id === selected) ?? null

  return (
    <div className="designer">
      <div className="designer__palette">
        {PALETTE.map((item) => (
          <button
            key={item.type}
            type="button"
            className="designer__palette-item"
            onClick={() => addNode(item.type, item.label)}
            title={item.hint}
          >
            <span className={`designer__swatch designer__swatch--${item.type}`} aria-hidden />
            {item.label}
          </button>
        ))}
        <span className="spacer" />
        {connectingFrom ? (
          <span className="designer__mode mono">clique no destino da ligação</span>
        ) : (
          <span className="designer__mode mono">arraste para posicionar</span>
        )}
      </div>

      <div
        ref={surface}
        className={`designer__surface ${connectingFrom ? 'designer__surface--connecting' : ''}`}
        onPointerMove={onPointerMove}
        onPointerUp={() => {
          dragging.current = null
        }}
        onClick={(event) => {
          if (event.target === event.currentTarget) {
            setSelected(null)
            setConnectingFrom(null)
          }
        }}
      >
        <svg className="designer__edges" aria-hidden>
          {value.edges.map((edge, index) => {
            const from = value.nodes.find((node) => node.id === edge.from)
            const to = value.nodes.find((node) => node.id === edge.to)
            if (!from || !to) return null
            const a = positions(from)
            const b = positions(to)
            const x1 = a.x + NODE_WIDTH / 2
            const y1 = a.y + NODE_HEIGHT / 2
            const x2 = b.x + NODE_WIDTH / 2
            const y2 = b.y + NODE_HEIGHT / 2
            return (
              <g key={index} className={edge.async ? 'designer__edge designer__edge--async' : 'designer__edge'}>
                <line x1={x1} y1={y1} x2={x2} y2={y2} markerEnd="url(#designer-arrow)" />
                {edge.label && (
                  <text x={(x1 + x2) / 2} y={(y1 + y2) / 2 - 6} textAnchor="middle">
                    {edge.label}
                  </text>
                )}
              </g>
            )
          })}
          <defs>
            <marker id="designer-arrow" markerWidth="9" markerHeight="9" refX="8" refY="3" orient="auto">
              <path d="M0,0 L0,6 L8,3 z" fill="currentColor" />
            </marker>
          </defs>
        </svg>

        {value.nodes.map((node) => {
          const { x, y } = positions(node)
          return (
            <div
              key={node.id}
              className={[
                'designer__node',
                `designer__node--${node.type}`,
                selected === node.id ? 'designer__node--selected' : '',
                connectingFrom === node.id ? 'designer__node--source' : '',
              ]
                .filter(Boolean)
                .join(' ')}
              style={{ left: x, top: y, width: NODE_WIDTH, height: NODE_HEIGHT }}
              onPointerDown={(event) => onPointerDown(event, node)}
            >
              <span className="designer__node-type mono">{node.type}</span>
              <span className="designer__node-label">{node.label}</span>
            </div>
          )
        })}

        {value.nodes.length === 0 && (
          <p className="designer__hint">
            Escolha um componente acima para começar. Ligações assíncronas entram pontilhadas — e é assim que a
            avaliação distingue uma fila de uma chamada direta.
          </p>
        )}
      </div>

      {selectedNode && (
        <div className="designer__inspector">
          <label className="designer__field">
            <span>Nome</span>
            <input
              className="input"
              value={selectedNode.label}
              onChange={(event) => updateNode(selectedNode.id, { label: event.target.value })}
            />
          </label>
          <button type="button" className="btn btn--quiet btn--small" onClick={() => setConnectingFrom(selectedNode.id)}>
            Ligar a…
          </button>
          <button type="button" className="btn btn--quiet btn--small" onClick={() => removeNode(selectedNode.id)}>
            Remover
          </button>
        </div>
      )}

      {value.edges.length > 0 && (
        <table className="designer__edge-table">
          <thead>
            <tr>
              <th>ligação</th>
              <th>rótulo</th>
              <th>assíncrona</th>
              <th>propriedade</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {value.edges.map((edge, index) => (
              <tr key={index}>
                <td className="mono">
                  {label(value, edge.from)} → {label(value, edge.to)}
                </td>
                <td>
                  <input
                    className="input input--tight"
                    value={edge.label}
                    placeholder="publica OrderPlaced"
                    onChange={(event) => updateEdge(index, { label: event.target.value })}
                  />
                </td>
                <td>
                  <label className="designer__toggle">
                    <input
                      type="checkbox"
                      checked={edge.async}
                      onChange={(event) => updateEdge(index, { async: event.target.checked })}
                    />
                    <span>{edge.async ? 'assíncrona' : 'síncrona'}</span>
                  </label>
                </td>
                <td>
                  <input
                    className="input input--tight mono"
                    value={edge.props.pattern ?? ''}
                    placeholder="outbox, retry, idempotent"
                    onChange={(event) => updateEdge(index, { props: { ...edge.props, pattern: event.target.value } })}
                  />
                </td>
                <td>
                  <button type="button" className="btn btn--quiet btn--small" onClick={() => removeEdge(index)}>
                    ×
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}

const label = (graph: SolutionGraph, id: string) => graph.nodes.find((node) => node.id === id)?.label ?? id
