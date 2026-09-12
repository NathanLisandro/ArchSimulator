# ADR-0005: Assinaturas de incidente geradas por causa raiz

Data: 2026-09-12 · Status: aceito

## Contexto
Casos de incidente precisam ser investigáveis: o usuário tem de **encontrar** a evidência em logs, métricas, traces e
linha do tempo, não recebê-la pronta. Escrever cada dataset à mão por template seria inviável e repetitivo.

## Decisão
O domínio tem um catálogo de causas raiz (`IncidentRootCause`, 12 hoje: N+1, índice removido, pool saturado, hot partition,
retry storm, cache stampede, lock longo, memory leak, timeout de terceiro, clock skew, DLQ crescendo, feature flag).
Cada uma tem uma *assinatura*: um conjunto de formas (`Shape`) aplicadas sobre métricas saudáveis, linhas de log com
mensagens específicas, traces com o formato correspondente (muitos spans curtos vs. um span dominante) e estado de banco.
O template só declara a causa e os parâmetros do cenário; o `IncidentDatasetGenerator` pinta a assinatura sobre o ruído.

Todo incidente recebe também **red herrings**: deploys de outros serviços na mesma janela, mudanças de feature flag não
relacionadas e erros de fundo em todos os serviços.

## Consequências
+ Um template novo custa YAML, não código; a mesma causa raiz gera dados diferentes por seed.
+ As assinaturas são testáveis: cada uma tem um teste que verifica o padrão que o usuário deve reconhecer.
− Assinaturas são aproximações: representam o padrão didático da causa, não a variedade completa do mundo real.
