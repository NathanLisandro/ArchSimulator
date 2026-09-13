import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Markdown } from './Markdown'

describe('Markdown', () => {
  it('renderiza títulos, listas e ênfase', () => {
    render(
      <Markdown>{'## Diagnóstico\n\nO **p99** subiu.\n\n- primeiro\n- segundo\n'}</Markdown>,
    )
    expect(screen.getByRole('heading', { name: 'Diagnóstico' })).toBeInTheDocument()
    expect(screen.getByText('p99').tagName).toBe('STRONG')
    expect(screen.getAllByRole('listitem')).toHaveLength(2)
  })

  it('renderiza tabela com alinhamento declarado', () => {
    render(<Markdown>{'| Critério | Nota |\n|---|---:|\n| Causa raiz | 30 |\n'}</Markdown>)
    expect(screen.getByRole('table')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Nota' })).toHaveStyle({ textAlign: 'right' })
    expect(screen.getByRole('cell', { name: 'Causa raiz' })).toBeInTheDocument()
  })

  it('mantém bloco de código sem interpretar o conteúdo', () => {
    render(<Markdown>{'```sql\nSELECT * FROM ledger_entries\n```'}</Markdown>)
    expect(screen.getByText(/SELECT \* FROM ledger_entries/)).toBeInTheDocument()
  })

  it('não interpreta markdown dentro de código inline', () => {
    render(<Markdown>{'use `**isto**` literalmente'}</Markdown>)
    expect(screen.getByText('**isto**')).toBeInTheDocument()
  })

  it('não transforma identificador com sublinhado em itálico', () => {
    const { container } = render(<Markdown>{'consulte pg_stat_user_indexes antes de dropar'}</Markdown>)
    expect(container.querySelector('em')).toBeNull()
    expect(container.textContent).toContain('pg_stat_user_indexes')
  })

  it('ainda aplica ênfase com sublinhado quando ela está isolada', () => {
    render(<Markdown>{'isso é _importante_ mesmo'}</Markdown>)
    expect(screen.getByText('importante').tagName).toBe('EM')
  })

  it('aceita texto vazio sem quebrar', () => {
    const { container } = render(<Markdown>{''}</Markdown>)
    expect(container.querySelector('.md')).toBeEmptyDOMElement()
  })
})
