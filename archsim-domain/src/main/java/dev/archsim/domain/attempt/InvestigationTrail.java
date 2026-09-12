package dev.archsim.domain.attempt;

import dev.archsim.domain.console.ConsoleActionKind;

import java.util.List;
import java.util.Locale;

public record InvestigationTrail(List<ConsoleAction> actions) {
    public static InvestigationTrail empty() { return new InvestigationTrail(List.of()); }

    public InvestigationTrail with(ConsoleAction action) {
        List<ConsoleAction> copy = new java.util.ArrayList<>(actions);
        copy.add(action);
        return new InvestigationTrail(List.copyOf(copy));
    }

    public int count(ConsoleActionKind kind) { return count(kind, null); }

    public int count(ConsoleActionKind kind, String targetContains) {
        return (int) actions.stream().filter(a -> a.kind() == kind).filter(a -> targetContains == null || targetContains.isBlank()
                || (a.target() != null && a.target().toLowerCase(Locale.ROOT).contains(targetContains.toLowerCase(Locale.ROOT)))
                || (a.detail() != null && a.detail().toLowerCase(Locale.ROOT).contains(targetContains.toLowerCase(Locale.ROOT)))).count();
    }

    public int size() { return actions.size(); }
}
