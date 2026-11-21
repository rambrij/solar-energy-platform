package com.saumya.pos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Proves every authoritative SRS permission is classified and every runtime refinement is documented. */
class PermissionCatalogueSrsParityTest {
    private static final String SRS_FILE = "saumya-solar-production-ready-srs-hld.md";
    private static final String CATALOGUE_RESOURCE = "/authorization-policy-registry.json";

    @Test
    void allSrsPermissionIdsAreClassifiedAndRuntimeOnlyIdsHaveApprovedRefinementRecords() throws Exception {
        JsonNode catalogue = catalogue();
        String srs = Files.readString(findFromRepositoryRoot(SRS_FILE));
        Set<String> srsPlatform = parsePermissionLine(section(srs), "Platform permissions:");
        Set<String> srsVendor = parsePermissionLine(section(srs), "Vendor permissions:");
        Set<String> srsPos = parsePermissionLine(section(srs), "POS permissions:");

        Set<String> srsAll = new TreeSet<>();
        srsAll.addAll(srsPlatform);
        srsAll.addAll(srsVendor);
        srsAll.addAll(srsPos);
        assertThat(srsAll).as("SRS §11.2 must not contain duplicate permission IDs")
            .hasSize(srsPlatform.size() + srsVendor.size() + srsPos.size());

        Set<String> classified = new TreeSet<>();
        addUnique(classified, strings(catalogue, "vendorPermissions"), "vendorPermissions");
        addUnique(classified, strings(catalogue, "platformPermissions"), "platformPermissions");
        addUnique(classified, strings(catalogue, "reservedVendorPermissions"), "reservedVendorPermissions");
        addUnique(classified, strings(catalogue, "orphanedPermissions"), "orphanedPermissions");
        addUnique(classified, conflicting(catalogue), "conflictingPermissions");

        assertThat(strings(catalogue, "unknownPermissions")).isEmpty();
        assertThat(classified).containsAll(srsAll);

        Set<String> runtimeOnly = new TreeSet<>(classified);
        runtimeOnly.removeAll(srsAll);
        runtimeOnly.removeAll(strings(catalogue, "customerPermissions"));

        Set<String> documentedRefinements = new TreeSet<>();
        for (JsonNode refinement : catalogue.path("approvedPermissionRefinements")) {
            String id = refinement.path("id").asText();
            assertThat(id).as("refinement identifier").isNotBlank();
            assertThat(documentedRefinements.add(id)).as("duplicate refinement %s", id).isTrue();
            assertThat(refinement.path("sourceSections").isArray()).isTrue();
            assertThat(refinement.path("sourceSections")).isNotEmpty();
            assertThat(refinement.path("rationale").asText()).isNotBlank();
            assertThat(refinement.path("parentSrsPermissions").isArray()).isTrue();
            for (JsonNode parent : refinement.path("parentSrsPermissions")) {
                assertThat(srsAll).as("SRS parent %s for refinement %s", parent.asText(), id).contains(parent.asText());
            }
            assertThat(classified).contains(id);
            assertThat(srsAll).doesNotContain(id);
        }
        assertThat(runtimeOnly).containsExactlyInAnyOrderElementsOf(documentedRefinements);
        assertThat(strings(catalogue, "platformPermissions")).containsAll(srsPlatform);
        assertThat(strings(catalogue, "vendorPermissions"))
            .containsAll(srsVendor.stream().filter(id -> !id.startsWith("PLATFORM_")).toList());
    }

    private static JsonNode catalogue() throws IOException {
        try (var input = PermissionCatalogueSrsParityTest.class.getResourceAsStream(CATALOGUE_RESOURCE)) {
            assertThat(input).as("canonical permission catalogue").isNotNull();
            return new ObjectMapper().readTree(input);
        }
    }

    private static String section(String srs) {
        int start = srs.indexOf("### 11.2 Permission catalogue");
        int end = srs.indexOf("## 12. Functional requirements", start);
        if (start < 0 || end < 0) throw new IllegalStateException("Could not locate SRS §11.2 Permission catalogue");
        return srs.substring(start, end);
    }

    private static Set<String> parsePermissionLine(String section, String label) {
        String line = section.lines().filter(value -> value.startsWith(label)).findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing SRS permission line: " + label));
        String body = line.substring(line.indexOf(':') + 1)
            .replace(" only when explicitly authorized", "")
            .replaceAll("\\s+Assignments additionally.*$", "")
            .replaceAll("\\s+Vendor custom roles.*$", "");
        Set<String> result = new TreeSet<>();
        for (String raw : body.split(",")) {
            String token = raw.strip().replaceAll("[.;]+$", "");
            if (token.isBlank()) continue;
            String[] parts = token.split("/");
            if (parts.length == 1) {
                result.add(parts[0]);
                continue;
            }
            int suffixStart = parts[0].lastIndexOf('_');
            if (suffixStart < 1) throw new IllegalStateException("Cannot expand SRS permission shorthand: " + token);
            String prefix = parts[0].substring(0, suffixStart + 1);
            result.add(parts[0]);
            for (int i = 1; i < parts.length; i++) result.add(prefix + parts[i]);
        }
        return result;
    }

    private static Set<String> strings(JsonNode root, String field) {
        Set<String> result = new TreeSet<>();
        JsonNode values = root.path(field);
        if (!values.isArray()) throw new IllegalStateException("Expected array in catalogue field " + field);
        values.forEach(value -> result.add(value.asText()));
        return result;
    }

    private static Set<String> conflicting(JsonNode root) {
        Set<String> result = new TreeSet<>();
        root.path("conflictingPermissions").forEach(value -> result.add(value.path("id").asText()));
        return result;
    }

    private static void addUnique(Set<String> all, Set<String> values, String field) {
        for (String value : values) assertThat(all.add(value))
            .as("permission %s appears in more than one classification (field %s)", value, field).isTrue();
    }

    private static Path findFromRepositoryRoot(String fileName) {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path candidate = cursor.resolve(fileName);
            if (Files.isRegularFile(candidate)) return candidate;
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Could not locate " + fileName + " above " + Path.of("").toAbsolutePath());
    }
}
