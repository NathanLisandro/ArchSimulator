# ADR-0008: Flashcards gerados a partir dos casos resolvidos

Data: 2026-09-12 · Status: aceito

## Contexto
Um deck estático vira conteúdo genérico que o usuário decora sem contexto. O material mais útil para revisão é o que ele
acabou de errar — e esse texto já existe, escrito pelo autor do template: o feedback direcional dos checkpoints e o
"quando este caminho é o indicado" de cada solution path.

## Decisão
Ao submeter um caso, o `FlashcardFactory` gera cards a partir dos checkpoints (com destaque para o que o usuário errou),
dos solution paths e da recomendação do cenário. Os ids derivam do id do caso, então resolver o mesmo caso de novo não
duplica cards. Eles entram no deck da primeira competência do caso, ao lado dos ~100 cards de seed em YAML.

A revisão usa SM-2 puro (intervalos 1, 6, depois × fator de facilidade, piso de 1,3), com estado por usuário e por card.

## Consequências
+ O estudo fica ancorado no que a pessoa viveu, com os números do cenário dentro do card.
+ Nenhum texto novo precisa ser escrito para gerar material: reaproveita o do template.
− Cards gerados herdam a qualidade do template; template com feedback fraco gera card fraco.
− Cards vivem por usuário (tabela `generated_cards`), não em deck compartilhado.
