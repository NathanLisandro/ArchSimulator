# ADR-0009: Dois registros visuais no frontend

Data: 2026-09-12 · Status: aceito

## Contexto
A tela do caso faz duas coisas ao mesmo tempo: mostrar o que a máquina diz (logs, métricas, traces, SQL, infraestrutura)
e receber o que a pessoa decide (checkpoints, ADR, post-mortem, desenho). Tratar as duas com a mesma linguagem visual
faz o usuário perder de vista qual informação é evidência e qual é a própria opinião.

## Decisão
A interface tem **dois registros**, lado a lado na mesma tela:

- **Console (escuro):** fundo azul-ardósia profundo, densidade alta, tipografia monoespaçada (IBM Plex Mono).
- **Papel (claro):** fundo claro, medida de leitura confortável, tipografia proporcional (Archivo).

Duas regras sustentam isso e valem em todo o produto:

1. **Monoespaçado significa que a máquina escreveu.** Nomes de serviço, consultas, carimbos de tempo, planos de
   execução e valores de métrica. Texto humano nunca é monoespaçado, nem como rótulo decorativo.
2. **Âmbar e vermelho são severidade, nunca decoração.** Um ponto âmbar quer dizer degradação; um vermelho, crítico.
   Nada mais na interface usa essas cores — por isso o indicador de "aba ainda não aberta" é da cor de sinal, não âmbar.

Uma terceira decisão de leitura de dados: gráficos com variação pequena perto do valor (taxa de erro em 0,2% com ruído)
são ancorados no zero, para que ruído não vire desenho de incidente.

## Consequências
+ Em qualquer tela, dá para saber de relance se o que se lê é evidência ou opinião.
+ As cores de severidade continuam significando alguma coisa porque não são gastas em outro lugar.
− As duas paletas precisam ser mantidas em paralelo; componentes que aparecem nos dois lados (markdown, tabelas)
  carregam variação `--ink`.
