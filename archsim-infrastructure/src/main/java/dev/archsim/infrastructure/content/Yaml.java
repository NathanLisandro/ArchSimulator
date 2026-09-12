package dev.archsim.infrastructure.content;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small helpers for strict-but-friendly tree mapping. */
class Yaml {
    private Yaml() {}

    public static String req(JsonNode n, String field, String where) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) throw new ContentException(where + ": campo obrigatório '" + field + "'");
        return v.asText();
    }
    public static String opt(JsonNode n, String field, String def) {
        JsonNode v = n == null ? null : n.get(field);
        return v == null || v.isNull() ? def : v.asText();
    }
    static long reqLong(JsonNode n, String field, String where) { return Long.parseLong(req(n, field, where)); }
    static long optLong(JsonNode n, String field, long def) { return n == null || n.get(field) == null ? def : n.get(field).asLong(); }
    static int optInt(JsonNode n, String field, int def) { return n == null || n.get(field) == null ? def : n.get(field).asInt(); }
    static boolean optBool(JsonNode n, String field, boolean def) { return n == null || n.get(field) == null ? def : n.get(field).asBoolean(); }
    public static List<String> strings(JsonNode n, String field) {
        JsonNode v = n == null ? null : n.get(field);
        if (v == null || v.isNull()) return List.of();
        if (v.isTextual()) return List.of(v.asText());
        List<String> out = new ArrayList<>();
        v.forEach(e -> out.add(e.asText()));
        return out;
    }
    public static List<JsonNode> list(JsonNode n, String field) {
        JsonNode v = n == null ? null : n.get(field);
        if (v == null || v.isNull()) return List.of();
        List<JsonNode> out = new ArrayList<>();
        v.forEach(out::add);
        return out;
    }
    static Map<String, String> stringMap(JsonNode n) {
        Map<String, String> out = new LinkedHashMap<>();
        if (n != null) n.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asText()));
        return out;
    }
}
