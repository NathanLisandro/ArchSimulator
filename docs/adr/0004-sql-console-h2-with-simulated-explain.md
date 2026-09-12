# ADR-0004: Console SQL com H2 para SELECT e EXPLAIN simulado no domínio

Data: 2026-09-12 · Status: aceito

## Contexto
O usuário precisa "achar" a query lenta com SQL de verdade (`pg_stat_statements`, `pg_stat_activity`, `EXPLAIN`), mas as tabelas
do cenário têm bilhões de linhas fictícias. Executar de fato sobre esse volume é inviável; e um `EXPLAIN` real sobre uma amostra
pequena mostraria planos irreais (tudo vira Seq Scan barato).

## Decisão
- `SELECT`/`WITH` rodam em um H2 em memória por caso (modo PostgreSQL), com amostra determinística de ~300 linhas por tabela e
  tabelas de catálogo (`pg_stat_statements`, `pg_stat_activity`, `pg_locks`, `pg_stat_user_tables`, `pg_indexes`, `table_sizes`;
  views `v$session`/`v$sql`/`dba_*` para Oracle) preenchidas a partir do `DbState` do cenário. Usuário somente-leitura + limite de linhas.
- `EXPLAIN` é interceptado pela aplicação e resolvido pelo `ExplainSimulator` do domínio: um planner aproximado que usa as
  estatísticas do schema (linhas, distintos, índices) para escolher Seq Scan / Index Scan / Nested Loop / Hash Join e estimar
  tempo, no formato textual do Postgres ou do Oracle.

## Consequências
+ Planos coerentes com o volume declarado (2,1 bi de linhas ⇒ segundos), independentes da amostra.
+ Nenhuma dependência além do H2; sem serviço externo.
− `count(*)` na amostra devolve ~300, não o volume real; o console avisa e aponta para `table_sizes`/`pg_stat_user_tables`.
− O planner é uma aproximação: cobre os casos dos templates (índice ausente, predicado não-sargável, cast implícito, join), não o Postgres inteiro.
