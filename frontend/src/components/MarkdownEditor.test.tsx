import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MarkdownEditor } from './MarkdownEditor'

function Harness({ sections = [] as string[] }) {
  const [value, setValue] = useState('')
  return <MarkdownEditor value={value} onChange={setValue} sections={sections} />
}

describe('MarkdownEditor', () => {
  it('insere a seção obrigatória com um clique e marca que ela já existe', async () => {
    const user = userEvent.setup()
    render(<Harness sections={['Impacto', 'Causa raiz']} />)

    const impacto = screen.getByRole('button', { name: 'Impacto' })
    expect(impacto).not.toHaveClass('editor__section--done')

    await user.click(impacto)
    expect(screen.getByRole('textbox')).toHaveValue('## Impacto\n')
    expect(screen.getByRole('button', { name: 'Impacto' })).toHaveClass('editor__section--done')
  })

  it('não duplica uma seção já escrita', async () => {
    const user = userEvent.setup()
    render(<Harness sections={['Impacto']} />)
    await user.click(screen.getByRole('button', { name: 'Impacto' }))
    await user.click(screen.getByRole('button', { name: 'Impacto' }))
    expect(screen.getByRole('textbox')).toHaveValue('## Impacto\n')
  })

  it('conta palavras e alterna para a pré-visualização renderizada', async () => {
    const user = userEvent.setup()
    render(<Harness />)
    await user.type(screen.getByRole('textbox'), '## Causa\nfoi um N+1')

    expect(screen.getByText(/palavras/)).toHaveTextContent('4 palavras')

    await user.click(screen.getByRole('button', { name: 'Pré-visualizar' }))
    expect(screen.getByRole('heading', { name: 'Causa' })).toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
  })
})
