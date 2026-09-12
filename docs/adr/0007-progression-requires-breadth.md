# ADR-0007: Promoção exige volume, qualidade e amplitude

Data: 2026-09-12 · Status: aceito

## Contexto
A progressão júnior → staff é o principal sinal de evolução do usuário. Baseá-la só em quantidade de casos premia quem
repete o mesmo exercício; baseá-la só em nota premia quem escolhe o tipo de caso em que já é bom.

## Decisão
`PromotionPolicy` exige as três coisas ao mesmo tempo: **volume** (casos resolvidos), **qualidade** (média mínima) e
**amplitude** (tipos de caso distintos praticados). Pleno: 10 casos, média 70, 4 tipos. Sênior: 28 / 78 / 6.
Staff: 60 / 85 / 8. A tela de progresso mostra o que falta em cada uma das três dimensões.

Cargo conquistado não é perdido: uma sequência ruim não rebaixa. O que muda é a sugestão do próximo caso, que prioriza
a área fraca (média abaixo de 60) antes de buscar amplitude.

## Consequências
+ Não existe caminho curto por repetição, e o usuário é empurrado para o que evita.
+ A mesma métrica de amplitude alimenta a sugestão de próximo caso, sem regra separada.
− Os números são calibração inicial sem dados de uso; devem ser revistos quando houver histórico real.
