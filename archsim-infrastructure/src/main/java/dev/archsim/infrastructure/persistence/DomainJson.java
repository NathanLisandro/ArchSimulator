package dev.archsim.infrastructure.persistence;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.archsim.domain.rubric.*;

/** Jackson mapper for domain documents (ADR-0003). Polymorphism is declared here with mixins; the domain stays annotation-free. */
public final class DomainJson {
    private final ObjectMapper mapper;

    public DomainJson() {
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new Jdk8Module())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .addMixIn(Evidence.class, EvidenceMixin.class);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = KeywordEvidence.class, name = "keyword"),
            @JsonSubTypes.Type(value = RegexEvidence.class, name = "regex"),
            @JsonSubTypes.Type(value = SectionEvidence.class, name = "section"),
            @JsonSubTypes.Type(value = ChoiceEvidence.class, name = "choice"),
            @JsonSubTypes.Type(value = InvestigationEvidence.class, name = "investigation"),
            @JsonSubTypes.Type(value = MitigationEvidence.class, name = "mitigation"),
            @JsonSubTypes.Type(value = StatusUpdateEvidence.class, name = "status_update"),
            @JsonSubTypes.Type(value = GraphEvidence.class, name = "graph"),
            @JsonSubTypes.Type(value = NumberEvidence.class, name = "number")})
    interface EvidenceMixin {}

    public String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize " + value.getClass(), e); }
    }

    public <T> T read(String json, Class<T> type) {
        try { return mapper.readValue(json, type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot deserialize " + type.getSimpleName(), e); }
    }

    public ObjectMapper mapper() { return mapper; }
}
