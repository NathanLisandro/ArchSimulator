# ArchSim — simulador de arquiteto de software

Um simulador para treinar **decisões de arquitetura** e **investigação de problemas em produção**. Você recebe um caso
de uma empresa fictícia com números de verdade, investiga em consoles simulados de logs, métricas, traces, SQL e
infraestrutura, decide o que fazer e defende a decisão por escrito. A avaliação é por rubrica, aceita vários caminhos
válidos e mostra como um sênior resolveria o mesmo cenário.

Tudo roda localmente. Não há API externa, serviço pago nem chamada de IA em tempo de execução: a geração dos casos é
por template com semente reproduzível, e a avaliação é determinística.

---

## Como funciona um caso

1. **Briefing.** Um endpoint em 4,2 segundos no p99, um batch que estourou a janela, um pedido do negócio. Com a
   escala, a stack, as restrições regulatórias e as dívidas técnicas conhecidas da empresa.
2. **Investigação.** O caso não entrega a evidência: você a procura. Logs com uma mini-linguagem estilo SPL, métricas
   por serviço com a linha de base e as marcas de deploy no mesmo eixo de tempo, traces distribuídos, console SQL
   somente-leitura com `EXPLAIN` simulado, linha do tempo de mudanças e mapa de infraestrutura AWS.
3. **Checkpoints.** Perguntas curtas no meio do caminho. A resposta devolve um card na hora: "você foi direto para os
   logs do banco, mas o p99 subiu só no account-api — comece pela métrica por serviço". Se a escolha é ruim, você pode
   rever ou seguir mesmo assim, e isso entra na avaliação.
4. **Ação.** Em incidentes há mitigações com efeito real no cenário (rollback, escalar, matar sessão, degradar), e o
   sistema registra se você agiu antes ou depois de investigar. Stakeholders cobram posição conforme o relógio anda.
5. **Resposta.** Campos estruturados mais texto livre: ADR, post-mortem, plano de capacidade, review. Design de solução
   aceita desenho no editor de arrastar e soltar ou Mermaid.
6. **Avaliação.** Nota por critério, o que faltou, o que foi bem, qual caminho você seguiu, por que outro seria mais
   indicado naquele contexto, e a resposta de referência. Exportável em markdown.

---

## Subindo tudo

```bash
docker compose up --build
```

- Aplicação: http://localhost:5173
- API: http://localhost:8080/api
- PostgreSQL: localhost:5433 (usuário e senha `archsim`), fora da 5432 para não brigar com um Postgres local

Crie uma conta na primeira tela.

### Desenvolvimento local

Requisitos: Java 25 (`JAVA_HOME` no JDK 25), Maven 3.8+, Node 20+, Docker (Postgres e Testcontainers).

```bash
docker compose up -d postgres
mvn test                                   # 143 testes; integração via Testcontainers
mvn -pl archsim-infrastructure spring-boot:run

cd frontend
npm install
npm test                                   # 47 testes de componente e de cliente
npm run dev                                # proxy para :8080 já configurado
```

---

## O que existe hoje

| | |
|---|---|
| Tipos de caso | 8, todos implementados com checkpoints, caminhos e rubricas próprias |
| Empresas | 4: banco digital, e-commerce, logística e saúde |
| Templates de conteúdo | 27, incluindo um caso de consequência de segunda ordem |
| Causas raiz de incidente | 12, cada uma com assinatura própria em métricas, logs e traces |
| Flashcards | 100 de base em 8 decks, mais os gerados por caso resolvido |
| Testes | 190: 143 no backend (domínio, aplicação, integração) e 47 no frontend |
| Decisões registradas | 9 ADRs em `docs/adr/` |

### Tipos de caso

| Tipo | Console disponível | Conteúdo atual |
|---|---|---|
| Query lenta | SQL, métricas | índice removido, predicado não-sargável, cast implícito, apuração de SLA analítica no transacional, grade de agenda recalculada por requisição |
| Incidente | logs, métricas, traces, SQL, deploys, infra | N+1 após deploy, lock longo da conciliação, retry storm no PIX, hot partition na sincronização do app de motorista, auditoria síncrona travando o prontuário, cache servindo saldo velho |
| ADR | — | agenda de recebíveis em tempo real, busca semântica com modelo de linguagem |
| Trade-off forçado | — | outbox contra commit em duas fases, monólito modular contra microserviços |
| Design de solução | métricas | reserva de estoque no checkout, ingestão de exames de 23 laboratórios sem trocar o paciente |
| Code review | — | controller de checkout, parser de resultado de exame com risco clínico |
| Capacity planning | métricas de 90 dias, SQL, infra | teto de conexões do banco, quota do provedor de mapas, Black Friday |
| Migração | SQL, métricas | batch noturno em stored procedure, roteirização monolítica de 3h, trilha de auditoria de bilhões de linhas |

### Causas raiz de incidente

N+1 após deploy · índice removido · saturação do pool · hot partition no Kafka · retry storm · expiração em massa do
cache · lock longo por batch · memory leak · timeout de terceiro ou DNS · clock skew na idempotência · DLQ crescendo em
silêncio · feature flag ligada.

Todo incidente vem com ruído deliberado: deploys de outros serviços na mesma janela, mudanças de flag não relacionadas
e erros de fundo em todos os serviços. A causa tem de ser encontrada, não adivinhada.

### Empresas

| Empresa | Domínio | O que a torna difícil |
|---|---|---|
| **Meridian Bank** | banco digital | PIX com SLA regulatório, ledger de 2,1 bilhões de linhas, conciliação D+1 com a CIP |
| **Pangeia Commerce** | marketplace | catálogo volátil, checkout na Black Friday, estoque com item disputado |
| **Rotamax Logística** | last mile | roteirização diária que não pode atrasar, app offline-first, multa por SLA de entrega |
| **Vitalis Saúde Digital** | telemedicina | dado clínico com consequência, auditoria obrigatória, 23 laboratórios com identificadores próprios |

Uma empresa nova é um arquivo novo em `content/companies/`, sem mudar código.

### Consequências de segunda ordem

Alguns caminhos têm continuação. Quem escolhe servir o extrato do cache durante a reconstrução do índice recebe, na
tela de resultado, o convite para ver o que aconteceu duas semanas depois: o cache servindo saldo que não existe mais,
porque a invalidação ficou pendente. A lição não é sobre cache; é sobre mitigação que vira permanente sem o alarme que
ela exigia.

### Mitigação muda a telemetria

Em incidentes, aplicar uma mitigação que resolve faz os gráficos se recuperarem e os erros pararem, a partir daquele
instante. O pico do incidente continua no gráfico: o passado não muda, e é ele que vai para a linha do tempo do
post-mortem.

### Mini-linguagem de logs

Inspirada em SPL e CloudWatch Logs Insights, com parser e engine próprios no domínio:

```
service=checkout-api level=ERROR "Connection timeout"   # campo, glob, frase e termo livre
latency>1000 NOT pod=api-3 (level=ERROR OR level=WARN)  # comparações e booleanos
| stats count, avg(latency) as media, p95(latency), dc(pod) by service
| timechart span=5m count by level
| where count > 100 | sort -count | head 20 | fields timestamp, service, message
```

### Progressão e estudo

A promoção de júnior a staff exige três coisas ao mesmo tempo: volume de casos, média mínima e variedade de tipos
praticados. Repetir o mesmo exercício não promove. Enquanto algum tipo estiver abaixo de 60 de média, o sistema sugere
justamente esse.

Cada caso resolvido vira flashcard a partir dos checkpoints (com destaque para o que você errou), dos caminhos de
solução e da recomendação do cenário. A revisão usa SM-2: intervalos de 1 e 6 dias, depois multiplicados pelo fator de
facilidade, com reset ao esquecer.

---

## Arquitetura

Clean Architecture com DDD tático, em três módulos Maven onde o compilador impede dependência invertida:

- **`archsim-domain`** — entidades ricas, value objects, agregados (`Company`, `Case`, `Attempt`, `UserProgress`,
  `Sprint`, `Deck`), domain services (geradores de caso e de telemetria, avaliador por rubrica, planner de `EXPLAIN`,
  parser de logs, SM-2, exportador) e as interfaces de repositório. Zero framework: nem Spring, nem JPA, nem Jackson.
  Um teste ArchUnit garante isso.
- **`archsim-application`** — casos de uso, DTOs de borda e portas (`SqlSandbox`, `ObservabilityDatasetProvider`,
  `SeedGenerator`, `DomainEventPublisher`). Depende só do domínio.
- **`archsim-infrastructure`** — Spring Boot: REST, Spring Data JPA com Flyway, leitura dos YAMLs de conteúdo, sandbox
  SQL em H2 e segurança por sessão.
- **`content/`** — empresas, catálogo de infraestrutura AWS, templates de caso, competências e flashcards em YAML
  versionado. Um domínio novo ou um caso novo é um arquivo novo, nunca uma mudança de código de domínio.
- **`frontend/`** — React, Vite e TypeScript. Dois registros visuais: console escuro para o que a máquina diz, papel
  claro para o que a pessoa escreve. Componentes `LogSearch`, `MetricsPanel`, `TraceViewer`, `SqlConsole`,
  `DeployTimeline`, `InfraMap`, `ArchitectureDesigner`, `MarkdownEditor`, `MermaidPreview` e gráficos em SVG próprio.

### Decisões registradas

O projeto pratica o que ensina: as decisões estão em `docs/adr/`.

| ADR | Decisão |
|---|---|
| 0001 | Clean Architecture com módulos Maven separados |
| 0002 | Dataset de observabilidade regenerado do seed, não persistido |
| 0003 | `Case` e `Attempt` persistidos como documentos JSONB |
| 0004 | Console SQL em H2 com `EXPLAIN` simulado no domínio |
| 0005 | Assinaturas de incidente geradas por causa raiz |
| 0006 | Desenho avaliado por estrutura, não por texto |
| 0007 | Promoção exige volume, qualidade e amplitude |
| 0008 | Flashcards gerados a partir dos casos resolvidos |
| 0009 | Dois registros visuais no frontend |

---

## API

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/users` | cadastro local |
| POST | `/api/auth/login` · `/api/auth/logout` · GET `/api/auth/me` | sessão por cookie |
| GET | `/api/companies` · `/api/companies/{id}` | contextos de empresa |
| POST | `/api/attempts` | próximo caso (`companyId?`, `caseType?`, `level?`) |
| GET | `/api/attempts/{id}` | estado da tentativa |
| POST | `/api/attempts/{id}/checkpoints/{cp}` | responde checkpoint, devolve o card direcional |
| POST | `/api/attempts/{id}/console/sql` | SQL somente-leitura e `EXPLAIN` simulado |
| GET | `/api/attempts/{id}/console/metrics` | explorador de métricas |
| POST | `/api/attempts/{id}/console/logs` | busca com a mini-linguagem |
| GET | `/api/attempts/{id}/console/traces` | traces, mais lentos primeiro |
| GET | `/api/attempts/{id}/console/timeline` | deploys, flags, alertas, instâncias e infraestrutura |
| POST | `/api/attempts/{id}/mitigations` | aplica mitigação e mostra o efeito |
| POST | `/api/attempts/{id}/status-updates` | responde à cobrança dos stakeholders |
| POST | `/api/attempts/{id}/submit` | resposta final, avaliação e referência |
| GET | `/api/progress` | cargo, média, estatísticas por tipo e competência |
| GET | `/api/attempts` | histórico |
| GET | `/api/attempts/{id}/export` | ADR ou post-mortem em markdown |
| POST | `/api/sprints` · GET `/api/sprints/current` · `/api/sprints/{id}` | modo sprint |
| GET | `/api/study/decks` · `/api/study/decks/{id}/next` | flashcards por tema |
| POST | `/api/study/cards/{id}/review` | avalia o card e agenda a revisão |

---

## O que ainda falta

Nada disso bloqueia o uso, mas está mapeado:

- **Mais conteúdo.** São 27 templates, entre dois e cinco por tipo. Mais casos por tipo e por empresa é só escrever
  mais YAML: o `ContentValidationTest` gera cada template com 15 sementes em todos os níveis e reprova o que estiver
  inconsistente.
- **Agentes e RAG como tipo próprio.** O módulo de IA do MBA está coberto por um ADR de busca semântica e por
  flashcards de custo de token e segurança, mas não há tipo de caso dedicado a arquitetura de agentes.
- **Autenticação.** Usuário e senha locais com sessão. Sem recuperação de senha nem OAuth, por escolha.
- **Teste de ponta a ponta automatizado.** O fluxo completo foi verificado no navegador a cada fase, com capturas de
  tela, mas não há suíte de navegador no CI.
