import { Fragment, useMemo, type ReactNode } from 'react'
import { MermaidPreview } from './MermaidPreview'
import './markdown.css'

/**
 * Renderizador de markdown do tamanho do que o conteúdo do ArchSim usa: títulos, parágrafos, listas,
 * tabelas, citações, blocos de código (com mermaid renderizado) e ênfase inline. Sem dependência externa,
 * e sem `dangerouslySetInnerHTML` — o texto vem de YAML versionado, mas também do que o usuário escreve.
 */
export function Markdown({ children, className }: { children: string; className?: string }) {
  const blocks = useMemo(() => parseBlocks(children ?? ''), [children])
  return <div className={className ? `md ${className}` : 'md'}>{blocks}</div>
}

function parseBlocks(source: string): ReactNode[] {
  const lines = source.replace(/\r\n/g, '\n').split('\n')
  const out: ReactNode[] = []
  let index = 0
  let key = 0

  while (index < lines.length) {
    const line = lines[index]

    if (!line.trim()) {
      index++
      continue
    }

    const fence = line.match(/^\s*```\s*([\w-]*)\s*$/)
    if (fence) {
      const language = fence[1]
      const body: string[] = []
      index++
      while (index < lines.length && !/^\s*```/.test(lines[index])) body.push(lines[index++])
      index++
      const code = body.join('\n')
      out.push(
        language === 'mermaid' ? (
          <MermaidPreview key={key++} source={code} />
        ) : (
          <pre key={key++} className="md__code" data-language={language || undefined}>
            <code>{code}</code>
          </pre>
        ),
      )
      continue
    }

    const heading = line.match(/^(#{1,6})\s+(.*)$/)
    if (heading) {
      const level = heading[1].length
      const Tag = `h${Math.min(6, level + 1)}` as 'h2'
      out.push(
        <Tag key={key++} className={`md__h md__h--${level}`}>
          {inline(heading[2])}
        </Tag>,
      )
      index++
      continue
    }

    if (/^\s*(---|\*\*\*|___)\s*$/.test(line)) {
      out.push(<hr key={key++} className="md__rule" />)
      index++
      continue
    }

    if (/^\s*\|.*\|\s*$/.test(line) && index + 1 < lines.length && /^\s*\|[\s:|-]+\|\s*$/.test(lines[index + 1])) {
      const header = splitRow(line)
      const aligns = splitRow(lines[index + 1]).map((cell) =>
        cell.startsWith(':') && cell.endsWith(':') ? 'center' : cell.endsWith(':') ? 'right' : 'left',
      )
      index += 2
      const rows: string[][] = []
      while (index < lines.length && /^\s*\|.*\|\s*$/.test(lines[index])) rows.push(splitRow(lines[index++]))
      out.push(
        <div key={key++} className="md__table-wrap">
          <table className="md__table">
            <thead>
              <tr>
                {header.map((cell, i) => (
                  <th key={i} style={{ textAlign: aligns[i] ?? 'left' }}>
                    {inline(cell)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row, r) => (
                <tr key={r}>
                  {row.map((cell, c) => (
                    <td key={c} style={{ textAlign: aligns[c] ?? 'left' }}>
                      {inline(cell)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>,
      )
      continue
    }

    if (/^\s*>/.test(line)) {
      const body: string[] = []
      while (index < lines.length && /^\s*>/.test(lines[index])) body.push(lines[index++].replace(/^\s*>\s?/, ''))
      out.push(
        <blockquote key={key++} className="md__quote">
          {body.map((text, i) => (
            <p key={i}>{inline(text)}</p>
          ))}
        </blockquote>,
      )
      continue
    }

    const bullet = /^\s*[-*+]\s+/
    const ordered = /^\s*\d+[.)]\s+/
    if (bullet.test(line) || ordered.test(line)) {
      const isOrdered = ordered.test(line)
      const pattern = isOrdered ? ordered : bullet
      const items: string[] = []
      while (index < lines.length && (pattern.test(lines[index]) || /^\s{2,}\S/.test(lines[index]))) {
        if (pattern.test(lines[index])) items.push(lines[index].replace(pattern, ''))
        else items[items.length - 1] += ` ${lines[index].trim()}`
        index++
      }
      const ListTag = isOrdered ? 'ol' : 'ul'
      out.push(
        <ListTag key={key++} className="md__list">
          {items.map((item, i) => (
            <li key={i}>{inline(item)}</li>
          ))}
        </ListTag>,
      )
      continue
    }

    const paragraph: string[] = []
    while (index < lines.length && lines[index].trim() && !isBlockStart(lines[index])) paragraph.push(lines[index++])
    out.push(
      <p key={key++} className="md__p">
        {inline(paragraph.join(' '))}
      </p>,
    )
  }

  return out
}

function isBlockStart(line: string): boolean {
  return (
    /^\s*```/.test(line) ||
    /^#{1,6}\s/.test(line) ||
    /^\s*[-*+]\s+/.test(line) ||
    /^\s*\d+[.)]\s+/.test(line) ||
    /^\s*>/.test(line) ||
    /^\s*\|.*\|\s*$/.test(line) ||
    /^\s*(---|\*\*\*|___)\s*$/.test(line)
  )
}

const splitRow = (line: string) =>
  line
    .trim()
    .replace(/^\||\|$/g, '')
    .split('|')
    .map((cell) => cell.trim())

const INLINE = /(`[^`]+`)|(\*\*[^*]+\*\*)|(__[^_]+__)|(\*[^*\n]+\*)|(_[^_\n]+_)|(\[[^\]]+\]\([^)]+\))/g

function inline(text: string): ReactNode[] {
  const out: ReactNode[] = []
  let lastIndex = 0
  let key = 0
  for (const match of text.matchAll(INLINE)) {
    const start = match.index ?? 0
    if (start > lastIndex) out.push(<Fragment key={key++}>{text.slice(lastIndex, start)}</Fragment>)
    const token = match[0]
    if (token.startsWith('`')) {
      out.push(
        <code key={key++} className="md__inline-code">
          {token.slice(1, -1)}
        </code>,
      )
    } else if (token.startsWith('**') || token.startsWith('__')) {
      out.push(<strong key={key++}>{token.slice(2, -2)}</strong>)
    } else if (token.startsWith('[')) {
      const link = token.match(/^\[([^\]]+)\]\(([^)]+)\)$/)
      out.push(
        link ? (
          <a key={key++} href={link[2]} target="_blank" rel="noreferrer">
            {link[1]}
          </a>
        ) : (
          <Fragment key={key++}>{token}</Fragment>
        ),
      )
    } else {
      out.push(<em key={key++}>{token.slice(1, -1)}</em>)
    }
    lastIndex = start + token.length
  }
  if (lastIndex < text.length) out.push(<Fragment key={key++}>{text.slice(lastIndex)}</Fragment>)
  return out
}
