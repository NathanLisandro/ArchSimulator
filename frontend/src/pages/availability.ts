import type { CatalogEntry } from '../api/types'

/**
 * A matriz de conteúdo é esparsa de propósito: migração só existe de sênior para cima, e nem toda empresa
 * tem todo tipo de caso. Estas funções dizem o que a tela pode oferecer, para ninguém escolher um 404.
 */
export function companyHasContent(catalog: CatalogEntry[], companyId: string, caseType: string, level: string): boolean {
  return catalog.some(
    (entry) =>
      entry.companyId === companyId &&
      (!caseType || entry.caseType === caseType) &&
      (!level || entry.levels.includes(level)),
  )
}

export function typeHasContent(catalog: CatalogEntry[], caseType: string, companyId: string, level: string): boolean {
  return catalog.some(
    (entry) =>
      entry.caseType === caseType &&
      (!companyId || entry.companyId === companyId) &&
      (!level || entry.levels.includes(level)),
  )
}

export function levelHasContent(catalog: CatalogEntry[], level: string, companyId: string, caseType: string): boolean {
  return catalog.some(
    (entry) =>
      entry.levels.includes(level) &&
      (!companyId || entry.companyId === companyId) &&
      (!caseType || entry.caseType === caseType),
  )
}

/** Quantos templates a escolha atual alcança. Zero significa que o botão de começar não deve estar ativo. */
export function reachableTemplates(catalog: CatalogEntry[], companyId: string, caseType: string, level: string): number {
  return catalog
    .filter(
      (entry) =>
        (!companyId || entry.companyId === companyId) &&
        (!caseType || entry.caseType === caseType) &&
        (!level || entry.levels.includes(level)),
    )
    .reduce((total, entry) => total + entry.templates, 0)
}
