# ADR-0003: Agregados Case e Attempt persistidos como documentos JSONB

Data: 2026-09-12 · Status: aceito

## Contexto
`Case` (checkpoints, solution paths, rubricas com evidências polimórficas) e `Attempt` (respostas, trilha, avaliação) são árvores profundas, sempre lidas e gravadas inteiras pela raiz do agregado. Mapear cada nó em tabela JPA geraria dezenas de entidades sem valor de consulta.

## Decisão
Tabelas `cases` e `attempts` com colunas indexáveis (id, user_id, company_id, case_type, status, timestamps) e uma coluna `document jsonb` com o agregado serializado na infraestrutura (Jackson com mixins; nenhuma anotação no domínio). Usuário, progresso, agenda de revisão de flashcards e sprint são relacionais, pois são consultados por campo.

## Consequências
+ Modelo de domínio evolui sem migração por campo; leitura/gravação em uma linha.
− Consultas analíticas sobre o conteúdo do documento exigem `jsonb` operators ou colunas derivadas (adicionadas quando necessárias, ex.: `score`).
