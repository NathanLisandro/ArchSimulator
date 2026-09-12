package dev.archsim.domain.sql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SelectParserTest {

    private final SelectParser parser = new SelectParser();

    @Test
    void parsesTablesAliasesPredicatesOrderAndLimit() {
        SelectStatement s = parser.parse(
                "SELECT e.id FROM ledger_entries e JOIN accounts a ON a.id = e.account_id " +
                "WHERE e.account_id = 88213 AND e.created_at >= '2026-08-01' AND lower(a.document) = 'x' ORDER BY e.created_at DESC LIMIT 50");

        assertThat(s.tables()).extracting(TableRef::name).containsExactly("ledger_entries", "accounts");
        assertThat(s.tables()).extracting(TableRef::alias).containsExactly("e", "a");
        assertThat(s.joins()).hasSize(1);
        assertThat(s.joins().getFirst().leftColumn().column()).isEqualTo("id");
        assertThat(s.joins().getFirst().rightColumn().column()).isEqualTo("account_id");
        assertThat(s.predicates()).hasSize(3);
        assertThat(s.predicates().get(0)).satisfies(p -> {
            assertThat(p.column().alias()).isEqualTo("e");
            assertThat(p.column().column()).isEqualTo("account_id");
            assertThat(p.operator()).isEqualTo(Operator.EQ);
            assertThat(p.literal()).isEqualTo("88213");
            assertThat(p.wrappedInFunction()).isFalse();
        });
        assertThat(s.predicates().get(1).operator()).isEqualTo(Operator.RANGE);
        assertThat(s.predicates().get(2).wrappedInFunction()).isTrue();
        assertThat(s.orderBy()).hasSize(1);
        assertThat(s.orderBy().getFirst().descending()).isTrue();
        assertThat(s.limit()).isEqualTo(50);
        assertThat(s.aggregate()).isFalse();
    }

    @Test
    void detectsAggregateAndLikePatterns() {
        SelectStatement s = parser.parse("select count(*) from accounts where document like '123%'");
        assertThat(s.aggregate()).isTrue();
        assertThat(s.predicates().getFirst().operator()).isEqualTo(Operator.LIKE_PREFIX);
        SelectStatement s2 = parser.parse("select * from accounts where document like '%123'");
        assertThat(s2.predicates().getFirst().operator()).isEqualTo(Operator.LIKE_CONTAINS);
    }

    @Test
    void parsesInAndNumericLiteralOnVarchar() {
        SelectStatement s = parser.parse("SELECT * FROM accounts WHERE status IN ('A','B') AND document = 12345678900");
        assertThat(s.predicates().get(0).operator()).isEqualTo(Operator.IN);
        assertThat(s.predicates().get(1).literalIsNumeric()).isTrue();
    }
}
