package dev.archsim.domain.template;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArithmeticExpressionTest {
    @Test
    void evaluatesWithPrecedenceParenthesesAndParams() {
        Map<String, String> p = Map.of("rows", "80000000", "p99", "4200", "share", "0.25");
        assertThat(ArithmeticExpression.evaluate("rows * share", p)).isEqualTo(20_000_000);
        assertThat(ArithmeticExpression.evaluate("(rows - 1000) / 2 + 1", p)).isEqualTo(39_999_501);
        assertThat(ArithmeticExpression.evaluate("p99 * 1.5 - 300", p)).isEqualTo(6000);
        assertThat(ArithmeticExpression.evaluate("round(p99 / 1000)", p)).isEqualTo(4);
        assertThat(ArithmeticExpression.evaluate("max(p99, 5000)", p)).isEqualTo(5000);
    }
}
