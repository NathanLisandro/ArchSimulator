# ADR-0001: Clean Architecture com módulos Maven separados

Data: 2026-09-12 · Status: aceito

## Contexto
O ArchSim treina decisões de arquitetura; o próprio código precisa ser exemplo. Queremos domínio rico (DDD tático) sem vazamento de framework e regras de dependência verificáveis, não só combinadas.

## Decisão
Três módulos Maven: `archsim-domain` (sem Spring/JPA/Jackson), `archsim-application` (casos de uso, ports, DTOs; depende só do domínio) e `archsim-infrastructure` (Spring Boot, JPA, REST, leitura de YAML). O compilador impede dependência invertida; um teste ArchUnit impede que domínio e aplicação importem framework.

## Consequências
+ Violação de camada quebra o build, não passa em review.
+ Domínio testável com JUnit puro, sem contexto Spring.
− Mapeamento entre domínio e persistência é explícito (ver ADR-0003).
