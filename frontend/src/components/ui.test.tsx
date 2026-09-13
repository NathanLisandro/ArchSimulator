import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Empty, ErrorNote, Panel, RankTrack, ScoreRing, Severity } from './ui'

describe('componentes de interface', () => {
  it('o anel de nota é legível por leitor de tela', () => {
    render(<ScoreRing score={82} />)
    expect(screen.getByRole('img', { name: 'Pontuação 82 de 100' })).toBeInTheDocument()
    expect(screen.getByText('82')).toBeInTheDocument()
  })

  it('a trilha de cargo marca o atual e os já conquistados', () => {
    const { container } = render(<RankTrack rank="SENIOR" />)
    const steps = [...container.querySelectorAll('.rank-track__step')]
    expect(steps.map((step) => step.textContent)).toEqual(['júnior', 'pleno', 'sênior', 'staff'])
    expect(steps[0]).toHaveClass('rank-track__step--done')
    expect(steps[2]).toHaveClass('rank-track__step--current')
    expect(steps[3].className).not.toContain('--done')
  })

  it('o erro é anunciado como alerta', () => {
    render(<ErrorNote>Console somente leitura</ErrorNote>)
    expect(screen.getByRole('alert')).toHaveTextContent('Console somente leitura')
  })

  it('o estado vazio convida a agir', () => {
    render(<Empty title="Nenhum card vencido hoje" hint="Resolva um caso para gerar material novo." />)
    expect(screen.getByText('Nenhum card vencido hoje')).toBeInTheDocument()
    expect(screen.getByText(/Resolva um caso/)).toBeInTheDocument()
  })

  it('severidade usa a classe semântica correspondente', () => {
    const { container } = render(<Severity level="crit">incidente</Severity>)
    expect(container.querySelector('.sev--crit')).toHaveTextContent('incidente')
  })

  it('o painel mostra título, meta e ações', () => {
    render(
      <Panel title="Nota por critério" meta="5 critérios" actions={<button type="button">Mostrar</button>}>
        conteúdo
      </Panel>,
    )
    expect(screen.getByRole('heading', { name: 'Nota por critério' })).toBeInTheDocument()
    expect(screen.getByText('5 critérios')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Mostrar' })).toBeInTheDocument()
  })
})
