import { useEffect, useId, useRef, useState } from 'react'

type MermaidApi = typeof import('mermaid').default

let mermaidPromise: Promise<MermaidApi> | null = null

/** Mermaid pesa quase 1 MB: só é carregado quando um diagrama aparece de fato na tela. */
function loadMermaid(): Promise<MermaidApi> {
  mermaidPromise ??= import('mermaid').then(({ default: mermaid }) => {
    mermaid.initialize({
      startOnLoad: false,
      securityLevel: 'strict',
      fontFamily: "'IBM Plex Mono', monospace",
      theme: 'base',
      themeVariables: {
        background: '#ffffff',
        primaryColor: '#eef3f2',
        primaryTextColor: '#172028',
        primaryBorderColor: '#7b8891',
        lineColor: '#46545f',
        tertiaryColor: '#f1f4f3',
        fontSize: '13px',
      },
    })
    return mermaid
  })
  return mermaidPromise
}

/** Renderiza um diagrama Mermaid; se a sintaxe estiver quebrada, mostra o erro em vez de sumir com o conteúdo. */
export function MermaidPreview({ source }: { source: string }) {
  const id = useId().replace(/:/g, '')
  const container = useRef<HTMLDivElement>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    const text = source.trim()
    if (!text) {
      setError(null)
      if (container.current) container.current.innerHTML = ''
      return
    }
    loadMermaid()
      .then((mermaid) => mermaid.render(`mermaid-${id}`, text))
      .then(({ svg }) => {
        if (cancelled) return
        setError(null)
        if (container.current) container.current.innerHTML = svg
      })
      .catch((e: unknown) => {
        if (cancelled) return
        if (container.current) container.current.innerHTML = ''
        setError(e instanceof Error ? e.message : 'Não foi possível desenhar o diagrama.')
      })
    return () => {
      cancelled = true
    }
  }, [source, id])

  return (
    <figure className="mermaid-figure">
      <div ref={container} className="mermaid-figure__canvas" />
      {error && (
        <figcaption className="mermaid-figure__error">
          Diagrama não pôde ser desenhado: {error.split('\n')[0]}
        </figcaption>
      )}
    </figure>
  )
}
