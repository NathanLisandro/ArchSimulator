import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from './client'

function mockFetch(status: number, body: string, contentType = 'application/json') {
  // 204 não pode carregar corpo: o Response nem aceita construir assim
  const spy = vi.fn().mockResolvedValue(
    new Response(status === 204 ? null : body, { status, headers: { 'Content-Type': contentType } }),
  )
  vi.stubGlobal('fetch', spy)
  return spy
}

afterEach(() => vi.unstubAllGlobals())

describe('cliente da API', () => {
  it('envia JSON e devolve o corpo já parseado', async () => {
    const spy = mockFetch(200, '{"id":"u1","username":"nathan","displayName":"Nathan"}')
    const user = await api.login('nathan', 'secret1')

    expect(user.username).toBe('nathan')
    const [path, init] = spy.mock.calls[0]
    expect(path).toBe('/api/auth/login')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual({ username: 'nathan', password: 'secret1' })
    expect(init.credentials).toBe('same-origin')
  })

  it('usa a mensagem do servidor no erro, e não o status cru', async () => {
    mockFetch(404, '{"error":"Nenhum template para empresa=saude tipo=ADR nível=JUNIOR"}')
    await expect(api.startCase('saude', 'ADR', 'JUNIOR')).rejects.toThrowError(
      /Nenhum template para empresa=saude/,
    )
  })

  it('preserva o status no erro para quem quiser tratar', async () => {
    mockFetch(403, '{"error":"Esta tentativa pertence a outro usuário"}')
    await expect(api.attempt('x')).rejects.toMatchObject({ status: 403 })
    expect(new ApiError(403, 'x')).toBeInstanceOf(Error)
  })

  it('trata resposta sem conteúdo como ausência, não como erro', async () => {
    mockFetch(204, '')
    await expect(api.currentSprint()).resolves.toBeUndefined()
  })

  it('monta a consulta de métricas com múltiplas métricas', async () => {
    const spy = mockFetch(200, '{"from":"","to":"","services":[],"series":[]}')
    await api.metrics('a1', 'account-api', ['latency_p99', 'error_rate'])
    expect(spy.mock.calls[0][0]).toBe('/api/attempts/a1/console/metrics?service=account-api&metric=latency_p99&metric=error_rate')
  })

  it('escapa identificadores na URL do card', async () => {
    const spy = mockFetch(200, '{"cardId":"x","intervalDays":1,"dueAt":"","easeFactor":2.5,"remainingToday":0}')
    await api.reviewCard('consistencia:outbox why', 'GOOD')
    expect(spy.mock.calls[0][0]).toContain('consistencia%3Aoutbox%20why')
  })
})
