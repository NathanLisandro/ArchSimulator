package dev.archsim.domain.template;

import java.util.List;
import java.util.Map;

/** How a template parameter is sampled. */
public sealed interface ParameterSpec permits ParameterSpec.IntRange, ParameterSpec.Choice, ParameterSpec.Group {
    /** Integer in [min, max], snapped to multiples of step. */
    record IntRange(long min, long max, long step) implements ParameterSpec {
        public IntRange { if (max < min || step <= 0) throw new IllegalArgumentException("invalid range"); }
    }
    /** One of the given literal values. */
    record Choice(List<String> values) implements ParameterSpec {
        public Choice { if (values.isEmpty()) throw new IllegalArgumentException("empty choice"); }
    }
    /** One of several coherent bundles of values; all keys of the chosen bundle become parameters. */
    record Group(List<Map<String, String>> options) implements ParameterSpec {
        public Group { if (options.isEmpty()) throw new IllegalArgumentException("empty group"); }
    }
}
