package dev.archsim.infrastructure.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Reads every *.yaml under a directory (recursively) as a Jackson tree. */
public final class YamlContentLoader {
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    public List<Loaded> loadAll(Path dir) {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.walk(dir)) {
            List<Loaded> out = new ArrayList<>();
            for (Path p : files.filter(f -> f.toString().endsWith(".yaml") || f.toString().endsWith(".yml")).sorted().toList()) {
                out.add(new Loaded(p, yaml.readTree(Files.readString(p))));
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read content in " + dir, e);
        }
    }

    public record Loaded(Path path, JsonNode node) {}
}
