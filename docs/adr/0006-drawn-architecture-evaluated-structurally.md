# ADR-0006: Desenho avaliado por estrutura, não por texto

Data: 2026-09-12 · Status: aceito

## Contexto
Casos de design de solução pedem um desenho. Avaliar desenho por palavra-chave no texto premia quem escreve "fila" e
não distingue quem colocou a fila **entre os componentes certos** de quem só citou o conceito.

## Decisão
O editor drag-and-drop produz um `SolutionGraph` (nós tipados + arestas com síncrono/assíncrono e propriedades como
`pattern=outbox`). A rubrica usa `GraphEvidence` para checagens estruturais: existe nó do tipo X, existe aresta de A
para B, essa aresta é assíncrona, **não** existe chamada síncrona entre A e B, alguma aresta declara uma propriedade.

Quem prefere escrever a desenhar não é penalizado: quando não há grafo, o mesmo avaliador interpreta um flowchart
Mermaid — seta pontilhada, rótulo de evento ou aresta tocando um nó de fila contam como assincronia.

## Consequências
+ "Colocou a fila no lugar certo" vira critério objetivo, não impressão do avaliador.
+ Texto e desenho são intercambiáveis para o usuário; a rubrica é a mesma.
− A leitura de Mermaid cobre flowcharts simples; diagramas com subgraphs e sintaxe exótica podem não ser reconhecidos,
  e nesse caso o usuário perde pontos de estrutura que teria com o editor.
