package dev.archsim.domain.cases;

/**
 * Consequência de segunda ordem: quando o usuário segue um caminho, existe um caso de acompanhamento que mostra o
 * que aquela decisão causou semanas depois. O teaser é o que aparece na tela de resultado.
 */
public record FollowUp(String pathId, String templateId, String teaser) {}
