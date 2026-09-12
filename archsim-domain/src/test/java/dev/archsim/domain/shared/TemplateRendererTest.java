package dev.archsim.domain.shared;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void replacesPlaceholders() {
        assertThat(renderer.render("p99 em {{p99}}ms desde {{since}}", Map.of("p99", "4200", "since", "ontem")))
                .isEqualTo("p99 em 4200ms desde ontem");
    }

    @Test
    void formatsNumbersInPortuguese() {
        assertThat(renderer.render("{{rows|number}} linhas", Map.of("rows", "81234567"))).isEqualTo("81.234.567 linhas");
        assertThat(renderer.render("{{rows|compact}}", Map.of("rows", "81234567"))).isEqualTo("81,2M");
        assertThat(renderer.render("{{rows|compact}}", Map.of("rows", "4300"))).isEqualTo("4,3K");
        assertThat(renderer.render("{{rows|compact}}", Map.of("rows", "210000"))).as("sem decimal inútil").isEqualTo("210K");
        assertThat(renderer.render("{{rows|compact}}", Map.of("rows", "2000000"))).isEqualTo("2M");
    }

    @Test
    void failsOnUnknownPlaceholder() {
        assertThatThrownBy(() -> renderer.render("{{missing}}", Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missing");
    }

    @Test
    void leavesTextWithoutPlaceholdersUntouched() {
        assertThat(renderer.render("SELECT 1", Map.of())).isEqualTo("SELECT 1");
    }
}
