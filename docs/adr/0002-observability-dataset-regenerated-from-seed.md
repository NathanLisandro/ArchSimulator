# ADR-0002: Dataset de observabilidade regenerado a partir do seed, não persistido

Data: 2026-09-12 · Status: aceito

## Contexto
Cada caso de incidente/query lenta gera milhares de linhas de log, séries de métricas, traces e estado de banco. Persistir isso por tentativa custaria dezenas de MB por caso e acoplaria a persistência ao formato dos geradores.

## Decisão
O `Case` persiste apenas `seed` + `ScenarioSpec` (causa raiz, instantes, serviços, parâmetros). Os geradores do domínio são determinísticos (`RandomSource` seedado) e reconstroem o `ObservabilityDataset` sob demanda, em memória, com cache curto por tentativa aberta.

## Consequências
+ Persistência pequena; reprodutibilidade garantida (mesmo seed → mesmos dados), útil para testes e para "refazer o caso".
− Mudar um gerador altera datasets de casos antigos ainda abertos; aceitável, pois tentativas concluídas guardam a `Evaluation` e a trilha de investigação, não os dados brutos.
