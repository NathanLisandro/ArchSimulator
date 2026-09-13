import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'
import { BarBreakdown, TimeSeriesChart } from './charts'
import type { MetricSeries } from '../api/types'

function series(values: number[], metric = 'latency_p99'): MetricSeries {
  const start = Date.parse('2026-09-12T12:00:00Z')
  return {
    service: 'account-api',
    metric,
    unit: 'ms',
    points: values.map((value, index) => ({ at: new Date(start + index * 60_000).toISOString(), value })),
  }
}

describe('TimeSeriesChart', () => {
  it('desenha a linha e os eixos com o valor mais recente', () => {
    const { container, getByText } = render(<TimeSeriesChart series={series([180, 190, 4200])} />)
    expect(container.querySelector('path[stroke]')).toBeTruthy()
    expect(getByText('4,2K')).toBeInTheDocument()
  })

  it('não inventa drama em métrica plana com ruído', () => {
    const { container } = render(<TimeSeriesChart series={series([0.2, 0.24, 0.19, 0.22], 'error_rate')} />)
    const texts = [...container.querySelectorAll('text')].map((node) => node.textContent)
    // com a série ancorada no zero, o topo do eixo é bem maior que o valor máximo
    expect(texts).toContain('0')
  })

  it('marca alerta e deploy no mesmo eixo de tempo', () => {
    const { container } = render(
      <TimeSeriesChart
        series={series([180, 200, 3800])}
        markers={[{ at: '2026-09-12T12:01:00Z', label: 'alerta X', tone: 'crit' }]}
      />,
    )
    expect(container.querySelector('.chart__marker--crit')).toBeTruthy()
    expect(container.querySelector('title')?.textContent).toContain('alerta X')
  })

  it('desenha o limite do recurso como referência', () => {
    const { container } = render(
      <TimeSeriesChart series={series([1200, 1400, 1900], 'active_connections')} threshold={{ value: 3000, label: 'limite 3K' }} />,
    )
    expect(container.querySelector('.chart__threshold')).toBeTruthy()
    expect(container.textContent).toContain('limite 3K')
  })

  it('avisa quando não há dados em vez de quebrar', () => {
    const { getByText } = render(<TimeSeriesChart series={series([])} />)
    expect(getByText('sem dados na janela')).toBeInTheDocument()
  })
})

describe('BarBreakdown', () => {
  it('colore a barra pela faixa da nota', () => {
    const { container } = render(
      <BarBreakdown items={[{ label: 'ADR', value: 82 }, { label: 'Incidente', value: 35 }]} />,
    )
    expect(container.querySelector('.bars__fill--ok')).toBeTruthy()
    expect(container.querySelector('.bars__fill--crit')).toBeTruthy()
  })

  it('diz o que fazer quando não há nada praticado', () => {
    const { getByText } = render(<BarBreakdown items={[]} />)
    expect(getByText('Nada praticado ainda.')).toBeInTheDocument()
  })
})
