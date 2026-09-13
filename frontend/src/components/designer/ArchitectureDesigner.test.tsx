import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ArchitectureDesigner } from './ArchitectureDesigner'
import type { SolutionGraph } from '../../api/types'

function Harness({ onChange }: { onChange?: (graph: SolutionGraph) => void }) {
  const [graph, setGraph] = useState<SolutionGraph>({ nodes: [], edges: [] })
  return (
    <ArchitectureDesigner
      value={graph}
      onChange={(next) => {
        setGraph(next)
        onChange?.(next)
      }}
    />
  )
}

describe('ArchitectureDesigner', () => {
  it('começa vazio e explica o que fazer', () => {
    render(<Harness />)
    expect(screen.getByText(/Escolha um componente acima/)).toBeInTheDocument()
  })

  it('adiciona componentes com o tipo que a rubrica avalia', async () => {
    const user = userEvent.setup()
    let last: SolutionGraph = { nodes: [], edges: [] }
    render(<Harness onChange={(graph) => (last = graph)} />)

    await user.click(screen.getByRole('button', { name: /Serviço/ }))
    await user.click(screen.getByRole('button', { name: /Fila \/ tópico/ }))

    expect(last.nodes).toHaveLength(2)
    expect(last.nodes.map((node) => node.type)).toEqual(['service', 'queue'])
    expect(last.nodes[0].props.x).toBeDefined()
  })

  it('liga dois componentes e marca a ligação como assíncrona', async () => {
    const user = userEvent.setup()
    let last: SolutionGraph = { nodes: [], edges: [] }
    render(<Harness onChange={(graph) => (last = graph)} />)

    await user.click(screen.getByRole('button', { name: /Serviço/ }))
    await user.click(screen.getByRole('button', { name: /Fila \/ tópico/ }))

    const nodes = document.querySelectorAll('.designer__node')
    await user.click(nodes[0] as HTMLElement)
    await user.click(screen.getByRole('button', { name: 'Ligar a…' }))
    await user.click(nodes[1] as HTMLElement)

    expect(last.edges).toHaveLength(1)
    expect(last.edges[0].async).toBe(false)

    await user.click(screen.getByRole('checkbox'))
    expect(last.edges[0].async).toBe(true)
  })

  it('remove um componente e as ligações dele', async () => {
    const user = userEvent.setup()
    let last: SolutionGraph = { nodes: [], edges: [] }
    render(<Harness onChange={(graph) => (last = graph)} />)

    await user.click(screen.getByRole('button', { name: /Serviço/ }))
    await user.click(screen.getByRole('button', { name: /Banco/ }))
    const nodes = document.querySelectorAll('.designer__node')
    await user.click(nodes[0] as HTMLElement)
    await user.click(screen.getByRole('button', { name: 'Ligar a…' }))
    await user.click(nodes[1] as HTMLElement)
    expect(last.edges).toHaveLength(1)

    await user.click(document.querySelectorAll('.designer__node')[0] as HTMLElement)
    await user.click(screen.getByRole('button', { name: 'Remover' }))

    expect(last.nodes).toHaveLength(1)
    expect(last.edges).toHaveLength(0)
  })
})
