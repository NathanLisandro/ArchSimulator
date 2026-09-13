import { describe, expect, it } from 'vitest'
import type { CatalogEntry } from '../api/types'
import { companyHasContent, levelHasContent, reachableTemplates, typeHasContent } from './availability'

const catalog: CatalogEntry[] = [
  { companyId: 'banco-digital', caseType: 'SLOW_QUERY', levels: ['JUNIOR', 'PLENO', 'SENIOR'], templates: 2 },
  { companyId: 'banco-digital', caseType: 'MIGRATION', levels: ['SENIOR', 'STAFF'], templates: 1 },
  { companyId: 'saude', caseType: 'INCIDENT', levels: ['PLENO', 'SENIOR', 'STAFF'], templates: 1 },
]

describe('disponibilidade de conteúdo', () => {
  it('reconhece a empresa que tem o tipo escolhido no nível escolhido', () => {
    expect(companyHasContent(catalog, 'banco-digital', 'SLOW_QUERY', 'PLENO')).toBe(true)
    expect(companyHasContent(catalog, 'saude', 'SLOW_QUERY', 'PLENO')).toBe(false)
  })

  it('trata migração como conteúdo só de sênior para cima', () => {
    expect(typeHasContent(catalog, 'MIGRATION', 'banco-digital', 'PLENO')).toBe(false)
    expect(typeHasContent(catalog, 'MIGRATION', 'banco-digital', 'SENIOR')).toBe(true)
  })

  it('considera campo vazio como sorteio, e não como filtro', () => {
    expect(typeHasContent(catalog, 'INCIDENT', '', 'STAFF')).toBe(true)
    expect(levelHasContent(catalog, 'JUNIOR', '', '')).toBe(true)
    expect(levelHasContent(catalog, 'JUNIOR', 'saude', '')).toBe(false)
  })

  it('conta quantos templates a escolha atual alcança', () => {
    expect(reachableTemplates(catalog, '', '', 'SENIOR')).toBe(4)
    expect(reachableTemplates(catalog, 'banco-digital', '', 'JUNIOR')).toBe(2)
    expect(reachableTemplates(catalog, 'saude', 'MIGRATION', '')).toBe(0)
  })
})
