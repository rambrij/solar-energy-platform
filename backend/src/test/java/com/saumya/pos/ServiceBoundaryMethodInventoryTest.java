package com.saumya.pos;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Service;
import static org.assertj.core.api.Assertions.*;

class ServiceBoundaryMethodInventoryTest {
    private record Entry(Set<String> methods, String policy) {}

    @Test
    void documentedInventoryMatchesEveryPublicMethodDeclaredByEveryServiceBean() throws Exception {
        Map<String, Entry> inventory = readInventory();
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Service.class, true));

        int checkedServices = 0;
        Set<String> inspectedNames = new HashSet<>();
        for (var candidate : scanner.findCandidateComponents("com.saumya.pos")) {
            Class<?> service = Class.forName(candidate.getBeanClassName());
            String simpleName = service.getSimpleName();
            if (!inspectedNames.add(simpleName)) {
                throw new AssertionError("Duplicate service simple name: " + simpleName);
            }
            Entry entry = inventory.get(simpleName);
            assertThat(entry)
                .as("method-by-method inventory row for @Service %s", service.getName())
                .isNotNull();

            Set<String> actual = new TreeSet<>();
            for (Method method : service.getDeclaredMethods()) {
                int modifiers = method.getModifiers();
                if (Modifier.isPublic(modifiers)
                    && !Modifier.isStatic(modifiers)
                    && !method.isSynthetic()
                    && !method.isBridge()) {
                    actual.add(method.getName());
                }
            }
            assertThat(actual)
                .as("public methods on %s must match its explicit inventory row", service.getName())
                .containsExactlyInAnyOrderElementsOf(entry.methods());

            assertThat(entry.policy())
                .as("authorization policy or trusted/public justification for %s", simpleName)
                .isNotBlank();
            checkedServices++;
        }

        assertThat(checkedServices)
            .as("all concrete Spring service implementations must participate in the inventory regression")
            .isGreaterThan(40);
    }

    private Map<String, Entry> readInventory() throws Exception {
        Path document = locateInventory();
        String markdown = Files.readString(document);
        int start = markdown.indexOf("## 9. Complete public business-method inventory");
        assertThat(start).as("method inventory section in " + document).isGreaterThanOrEqualTo(0);
        int end = markdown.indexOf("### 9.1", start);
        if (end < 0) end = markdown.length();

        Map<String, Entry> result = new LinkedHashMap<>();
        for (String line : markdown.substring(start, end).split("\\R")) {
            if (!line.startsWith("|")) continue;
            String[] cells = line.split("\\|", -1);
            if (cells.length < 4) continue;
            String service = cells[1].trim();
            String methodList = cells[2].trim();
            String policy = cells[3].trim();
            if (service.isBlank()
                || service.equals("Service implementation")
                || service.startsWith("---")) continue;

            Set<String> methods = new TreeSet<>();
            for (String method : methodList.split(",")) {
                String name = method.trim();
                if (!name.isBlank()) methods.add(name);
            }
            assertThat(result.put(service, new Entry(Set.copyOf(methods), policy)))
                .as("one method inventory row per service: %s", service)
                .isNull();
        }
        return result;
    }

    private Path locateInventory() {
        for (Path cursor = Path.of("").toAbsolutePath(); cursor != null; cursor = cursor.getParent()) {
            Path candidate = cursor.resolve("docs/security/SERVICE_BOUNDARY_AUTHORIZATION_INVENTORY.md");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new AssertionError("Could not find docs/security/SERVICE_BOUNDARY_AUTHORIZATION_INVENTORY.md from the test working directory");
    }
}
