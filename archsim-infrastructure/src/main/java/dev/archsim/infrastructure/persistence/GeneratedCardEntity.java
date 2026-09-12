package dev.archsim.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "generated_cards")
@IdClass(GeneratedCardEntity.Key.class)
public class GeneratedCardEntity {
    @Id @Column(name = "user_id") private String userId;
    @Id @Column(name = "card_id") private String cardId;
    @Column(name = "deck_id", nullable = false) private String deckId;
    private String origin;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private String document;

    protected GeneratedCardEntity() {}
    public GeneratedCardEntity(String userId, String cardId, String deckId, String origin, String document) {
        this.userId = userId; this.cardId = cardId; this.deckId = deckId; this.origin = origin; this.document = document;
    }
    public String getUserId() { return userId; }
    public String getCardId() { return cardId; }
    public String getDeckId() { return deckId; }
    public String getDocument() { return document; }

    public record Key(String userId, String cardId) implements Serializable {
        public Key() { this(null, null); }
        @Override public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(cardId, k.cardId);
        }
        @Override public int hashCode() { return Objects.hash(userId, cardId); }
    }
}
