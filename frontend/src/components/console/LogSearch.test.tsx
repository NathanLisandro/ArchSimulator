import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { LogSearch } from './LogSearch'
import type { LogSearchView } from '../../api/types'

function respond(view: Partial<LogSearchView>) {
  const full: LogSearchView = {
    kind: 'EVENTS',
    events: [],
    columns: [],
    rows: [],
    totalMatched: 0,
    truncated: false,
    error: null,
    ...view,
  }
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(new Response(JSON.stringify(full), { status: 200, headers: { 'Content-Type': 'application/json' } })),
  )
}

afterEach(() => vi.unstubAllGlobals())

describe('LogSearch', () => {
  it('mostra os eventos com nível, serviço e mensagem', async () => {
    const user = userEvent.setup()
    respond({
      totalMatched: 2,
      events: [
        {
          timestamp: '2026-09-12T17:41:03Z',
          service: 'account-api',
          level: 'ERROR',
          traceId: 'abcdef0123456789',
          latencyMs: 4200,
          message: 'Connection timeout acquiring connection from pool',
          fields: { pod: 'account-api-2', env: 'prod' },
        },
      ],
    })
    render(<LogSearch attemptId="a1" />)
    await user.click(screen.getByRole('button', { name: 'Buscar' }))

    await waitFor(() => expect(screen.getByText(/Connection timeout acquiring/)).toBeInTheDocument())
    expect(screen.getByText('ERROR')).toBeInTheDocument()
    expect(screen.getByText('account-api')).toBeInTheDocument()
    expect(screen.getByText('4200ms')).toBeInTheDocument()
    expect(screen.getByText(/2 linha\(s\) casaram/)).toBeInTheDocument()
  })

  it('mostra resultado tabular de stats', async () => {
    const user = userEvent.setup()
    respond({ kind: 'TABLE', columns: ['service', 'count'], rows: [['account-api', 41]], totalMatched: 41 })
    render(<LogSearch attemptId="a1" />)
    await user.click(screen.getByRole('button', { name: 'Buscar' }))

    await waitFor(() => expect(screen.getByRole('table')).toBeInTheDocument())
    expect(screen.getByRole('columnheader', { name: 'count' })).toHaveClass('num')
    expect(screen.getByRole('cell', { name: '41' })).toBeInTheDocument()
  })

  it('mostra o erro de sintaxe da mini-linguagem em vez de sumir com tudo', async () => {
    const user = userEvent.setup()
    respond({ kind: 'ERROR', error: "Comando desconhecido no pipeline: 'frobnicate'." })
    render(<LogSearch attemptId="a1" />)
    await user.click(screen.getByRole('button', { name: 'Buscar' }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('frobnicate'))
  })

  it('os exemplos preenchem e executam a consulta', async () => {
    const user = userEvent.setup()
    respond({ kind: 'TABLE', columns: ['service', 'count'], rows: [['pix-api', 3]], totalMatched: 3 })
    render(<LogSearch attemptId="a1" />)

    await user.click(screen.getByRole('button', { name: 'level=ERROR' }))
    expect(screen.getByLabelText('Consulta de logs')).toHaveValue('level=ERROR')
    await waitFor(() => expect(screen.getByRole('table')).toBeInTheDocument())
  })
})
