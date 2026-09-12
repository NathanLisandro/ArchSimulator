package dev.archsim.infrastructure.content;

import com.fasterxml.jackson.databind.JsonNode;
import dev.archsim.domain.company.*;
import dev.archsim.domain.sql.SqlDialect;

import static dev.archsim.infrastructure.content.Yaml.*;

final class CompanyMapper {
    Company map(JsonNode n, String where) {
        String id = req(n, "id", where);
        JsonNode scale = n.get("scale");
        JsonNode stack = n.get("stack");
        JsonNode team = n.get("team");
        JsonNode arch = n.get("architecture");
        JsonNode infra = n.get("infra");
        JsonNode schema = n.get("schema");
        if (schema == null) throw new ContentException(where + ": 'schema' é obrigatório");
        return new Company(new CompanyId(id), req(n, "name", where), req(n, "domain", where), opt(n, "description", ""),
                new Scale(optLong(scale, "dailyActiveUsers", 0), optInt(scale, "peakTps", 0), opt(scale, "dataVolume", ""), opt(scale, "growthNote", "")),
                new TechStack(strings(stack, "languages"), strings(stack, "frameworks"), strings(stack, "datastores"), strings(stack, "messaging"), opt(stack, "cloud", "AWS"), opt(stack, "notes", null)),
                new Team(optInt(team, "developers", 0), optInt(team, "squads", 0), opt(team, "notes", null)),
                list(n, "regulations").stream().map(r -> new RegulatoryConstraint(req(r, "name", where), opt(r, "description", ""))).toList(),
                new Architecture(
                        list(arch, "services").stream().map(s -> new ServiceComponent(req(s, "name", where), opt(s, "description", ""), opt(s, "language", null), strings(s, "dependsOn"), opt(s, "datastore", null), optInt(s, "instances", 2))).toList(),
                        list(arch, "datastores").stream().map(d -> new Datastore(req(d, "name", where), opt(d, "engine", "postgres"), opt(d, "description", ""), opt(d, "sizing", ""))).toList(),
                        list(arch, "brokers").stream().map(b -> new MessageBroker(req(b, "name", where), opt(b, "type", "kafka"), strings(b, "topics"))).toList(),
                        list(arch, "caches").stream().map(c -> new CacheComponent(req(c, "name", where), opt(c, "type", "redis"), opt(c, "usage", ""))).toList()),
                new InfraTopology(
                        list(infra, "resources").stream().map(r -> new InfraResource(req(r, "id", where), req(r, "type", where), opt(r, "name", req(r, "id", where)), stringMap(r.get("properties")))).toList(),
                        list(infra, "edges").stream().map(e -> new InfraEdge(req(e, "from", where), req(e, "to", where), opt(e, "label", ""))).toList()),
                new DatabaseSchema(SqlDialect.valueOf(opt(schema, "dialect", "POSTGRES").toUpperCase()),
                        list(schema, "tables").stream().map(t -> new Table(req(t, "name", where), reqLong(t, "rowCount", where), optLong(t, "sizeMb", 100),
                                list(t, "columns").stream().map(c -> new Column(req(c, "name", where), opt(c, "type", "varchar(64)"), optLong(c, "distinct", 100), optBool(c, "nullable", false))).toList(),
                                list(t, "indexes").stream().map(i -> new Index(req(i, "name", where), strings(i, "columns"), optBool(i, "unique", false), opt(i, "type", "btree"), optLong(i, "sizeMb", 10))).toList())).toList()),
                list(n, "techDebts").stream().map(d -> new TechDebt(req(d, "title", where), opt(d, "description", ""))).toList());
    }
}
