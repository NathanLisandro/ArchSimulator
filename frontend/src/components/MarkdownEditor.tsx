import { useState } from 'react'
import { Markdown } from './Markdown'
import './editor.css'

/** Conta palavras do texto, não da marcação: marcadores de título, lista e ênfase não são palavras escritas. */
function countWords(text: string): number {
  const prose = text
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/^\s{0,3}#{1,6}\s+/gm, ' ')
    .replace(/^\s*[-*+]\s+/gm, ' ')
    .replace(/^\s*\d+[.)]\s+/gm, ' ')
    .replace(/[*_`>|]/g, ' ')
    .trim()
  return prose ? prose.split(/\s+/).length : 0
}

/**
 * Editor de markdown com pré-visualização. As seções obrigatórias (## Impacto, ## Decisão…) aparecem como
 * atalhos porque a rubrica avalia presença de seção: o atalho ensina o formato esperado enquanto se escreve.
 */
export function MarkdownEditor({
  value,
  onChange,
  placeholder,
  sections = [],
  rows = 16,
  id,
}: {
  value: string
  onChange: (next: string) => void
  placeholder?: string
  sections?: string[]
  rows?: number
  id?: string
}) {
  const [preview, setPreview] = useState(false)
  const words = countWords(value)

  function appendSection(name: string) {
    const heading = `## ${name}`
    if (value.includes(heading)) return
    const separator = value.trim() ? '\n\n' : ''
    onChange(`${value.trimEnd()}${separator}${heading}\n`)
  }

  return (
    <div className="editor">
      <div className="editor__bar">
        <div className="editor__sections">
          {sections.map((section) => (
            <button
              key={section}
              type="button"
              className={`editor__section ${value.includes(`## ${section}`) ? 'editor__section--done' : ''}`}
              onClick={() => appendSection(section)}
            >
              {section}
            </button>
          ))}
        </div>
        <span className="spacer" />
        <span className="editor__count mono">{words} palavras</span>
        <button type="button" className="btn btn--quiet btn--small" onClick={() => setPreview((on) => !on)}>
          {preview ? 'Editar' : 'Pré-visualizar'}
        </button>
      </div>
      {preview ? (
        <div className="editor__preview">
          {value.trim() ? <Markdown>{value}</Markdown> : <p className="muted">Nada escrito ainda.</p>}
        </div>
      ) : (
        <textarea
          id={id}
          className="textarea editor__input"
          rows={rows}
          value={value}
          placeholder={placeholder}
          onChange={(event) => onChange(event.target.value)}
        />
      )}
    </div>
  )
}
