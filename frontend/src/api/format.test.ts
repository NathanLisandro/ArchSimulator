import { describe, expect, it } from 'vitest'
import { clock, compact, competencyLabel, duration, number, scoreTone } from './format'

describe('formatação de números', () => {
  it('agrupa milhares no padrão brasileiro', () => {
    expect(number(1234567)).toBe('1.234.567')
    expect(number(0)).toBe('0')
  })

  it('compacta sem casa decimal inútil', () => {
    expect(compact(4300)).toBe('4,3K')
    expect(compact(210000)).toBe('210K')
    expect(compact(2000000)).toBe('2M')
    expect(compact(8900000000)).toBe('8,9B')
    expect(compact(42)).toBe('42')
  })
})

describe('formatação de tempo', () => {
  it('escreve duração como uma pessoa escreveria', () => {
    expect(duration(45)).toBe('45s')
    expect(duration(750)).toBe('12min 30s')
    expect(duration(3600)).toBe('1h')
    expect(duration(4800)).toBe('1h 20min')
    expect(duration(-5)).toBe('0s')
  })

  it('formata o cronômetro regressivo com dois dígitos', () => {
    expect(clock(0)).toBe('00:00')
    expect(clock(65)).toBe('01:05')
    expect(clock(3599)).toBe('59:59')
  })
})

describe('semântica de cor da nota', () => {
  it('usa os mesmos limites da avaliação', () => {
    expect(scoreTone(90)).toBe('ok')
    expect(scoreTone(75)).toBe('ok')
    expect(scoreTone(74)).toBe('warn')
    expect(scoreTone(50)).toBe('warn')
    expect(scoreTone(49)).toBe('crit')
  })
})

describe('rótulo de competência', () => {
  it('traduz o que conhece e degrada com elegância no resto', () => {
    expect(competencyLabel('databases')).toBe('bancos de dados')
    expect(competencyLabel('clean-arch')).toBe('Clean Architecture')
    expect(competencyLabel('coisa-nova')).toBe('coisa nova')
  })
})
