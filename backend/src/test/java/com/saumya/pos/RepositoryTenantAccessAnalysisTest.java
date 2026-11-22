package com.saumya.pos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Fail-closed source analysis for Spring Data repositories, declared JPQL/native queries,
 * dynamic JPA query construction and specification-based data access.
 */
class RepositoryTenantAccessAnalysisTest {
    private static Path sourceRoot() {
        for (Path candidate : List.of(Path.of("backend/src/main/java"), Path.of("src/main/java"))) {
            if (Files.isDirectory(candidate)) return candidate;
        }
        throw new AssertionError("Cannot locate backend/src/main/java for repository analysis");
    }

    private static JsonNode policy() throws Exception {
        try (InputStream in = getClassResource("tenant-data-access-policy.json")) {
            assertThat(in).as("tenant data-access policy").isNotNull();
            return new ObjectMapper().readTree(in);
        }
    }

    private static InputStream getClassResource(String name) {
        return RepositoryTenantAccessAnalysisTest.class.getResourceAsStream("/" + name);
    }

    private static Map<String, String> policyEntries(JsonNode root, String key) {
        Map<String, String> entries = new TreeMap<>();
        root.path(key).forEach(item -> {
            String operation = item.path("operation").asText();
            String scope = item.path("scope").asText();
            String trustedSource = item.path("trustedSource").asText();
            assertThat(operation).isNotBlank();
            assertThat(scope).isNotBlank();
            assertThat(trustedSource).isNotBlank();
            assertThat(entries.put(operation, scope)).as("duplicate policy operation %s", operation).isNull();
        });
        return entries;
    }

    private static String repositoryName(Path path) {
        String name = path.getFileName().toString();
        return name.substring(0, name.length() - ".java".length());
    }

    private static List<Path> repositoryFiles() throws Exception {
        try (var paths = Files.walk(sourceRoot())) {
            return paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith("Repository.java"))
                .sorted().toList();
        }
    }

    private static List<String> queryAnnotations(String source) {
        List<String> annotations = new ArrayList<>();
        int cursor = 0;
        while ((cursor = source.indexOf("@Query", cursor)) >= 0) {
            int open = source.indexOf('(', cursor);
            if (open < 0) break;
            int depth = 1;
            boolean escaped = false;
            char quote = 0;
            boolean textBlock = false;
            int i = open + 1;
            while (i < source.length() && depth > 0) {
                if (quote == 0 && i + 2 < source.length()
                    && source.startsWith("\"\"\"", i)) {
                    textBlock = !textBlock;
                    if (!textBlock) quote = 0;
                    else quote = '"';
                    i += 3;
                    continue;
                }
                char ch = source.charAt(i);
                if (quote != 0) {
                    if (quote == '"' && !textBlock && escaped) escaped = false;
                    else if (quote == '"' && !textBlock && ch == '\\') escaped = true;
                    else if (quote == '"' && textBlock && i + 2 < source.length()
                        && source.startsWith("\"\"\"", i)) {
                        quote = 0; textBlock = false; i += 3; continue;
                    } else if (quote == '"' && !textBlock && ch == '"') quote = 0;
                    else if (quote == '\'' && ch == '\'') quote = 0;
                } else if (ch == '"' || ch == '\'') quote = ch;
                else if (ch == '(') depth++;
                else if (ch == ')') depth--;
                i++;
            }
            if (depth != 0) throw new AssertionError("Unclosed @Query annotation");
            annotations.add(source.substring(open + 1, i - 1));
            cursor = i;
        }
        return annotations;
    }

    private static List<String> javaStringLiterals(String annotation) {
        List<String> values = new ArrayList<>();
        Matcher blocks = Pattern.compile("\"\"\"([\\s\\S]*?)\"\"\"|\"((?:\\\\.|[^\"\\\\])*)\"")
            .matcher(annotation);
        while (blocks.find()) values.add(blocks.group(1) != null ? blocks.group(1) : blocks.group(2));
        return values;
    }

    private static boolean hasTenantPredicate(String query) {
        return Pattern.compile("(?is)(?:\\b[a-z_][a-z0-9_]*\\.)?(?:tenantId|tenant|tenant_id)\\s*=\\s*(?::[a-z][a-zA-Z0-9_]*|\\?\\d*)")
            .matcher(query).find();
    }

    private static boolean hasOwnerPredicate(String query) {
        return Pattern.compile("(?is)(?:\\b[a-z_][a-z0-9_]*\\.)?(?:customerId|customer_id|username|tokenHash|token_hash|hash|family|subscriptionId|invoiceId|planId)\\s*=\\s*(?::[a-z][a-zA-Z0-9_]*|\\?\\d*)")
            .matcher(query).find();
    }

    private static String methodAfterAnnotation(String source, int annotationEnd) {
        String tail = source.substring(annotationEnd, Math.min(source.length(), annotationEnd + 1200));
        Matcher method = Pattern.compile("(?s)\\b(?:Optional|List|Set|Page|Slice|Stream|Collection|long|int|boolean|void|[A-Z][A-Za-z0-9_]*(?:\\s*<[^;{}]+>)?)\\s+(\\w+)\\s*\\(")
            .matcher(tail);
        if (!method.find()) throw new AssertionError("Cannot identify repository method after @Query annotation");
        return method.group(1);
    }

    private static Set<String> queryMethodNames(String source) {
        // Mask Java text blocks, strings, chars, and comments before inspecting
        // method signatures; SQL tokens such as count(*) are not Java methods.
        String code = source
            .replaceAll("(?s)\"\"\".*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'", " ")
            .replaceAll("(?s)/\\*.*?\\*/|//[^\\r\\n]*", " ");
        Set<String> names = new TreeSet<>();
        Matcher matcher = Pattern.compile("(?m)^\\s*(?:default\\s+)?(?:[\\w.$<>?,\\[\\] ]+)\\s+(\\w+)\\s*\\([^;{}]*\\)\\s*(?:throws\\s+[\\w., ]+)?\\s*(?:;|\\{)")
            .matcher(code);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (name.matches("(?:find|findAll|findTop\\d*|exists|count|delete|remove|read|query|lock|locked).*")) names.add(name);
        }
        return names;
    }

    private static boolean hasExplicitScopeToken(String method) {
        return Pattern.compile("(?i)(?:Tenant(?:Id)?|Customer(?:Id)?|Username|TokenHash|Hash|Family|SubscriptionId|InvoiceId|PlanId|Owner)")
            .matcher(method).find();
    }

    @Test
    void everyRepositoryQueryHasTenantOwnerOrExplicitReviewedGlobalScope() throws Exception {
        JsonNode registry = policy();
        Map<String, String> global = policyEntries(registry, "globalMethods");
        Map<String, String> composite = policyEntries(registry, "compositeKeyMethods");
        Set<String> observed = new TreeSet<>();
        for (Path file : repositoryFiles()) {
            String source = Files.readString(file);
            String repo = repositoryName(file);
            Matcher queryMarker = Pattern.compile("@Query\\s*\\(").matcher(source);
            while (queryMarker.find()) {
                int annotationStart = queryMarker.start();
                int open = source.indexOf('(', queryMarker.start());
                int depth = 1;
                boolean inString = false, inTextBlock = false, escaped = false;
                int i = open + 1;
                while (i < source.length() && depth > 0) {
                    if (source.startsWith("\"\"\"", i)) { inTextBlock = !inTextBlock; i += 3; continue; }
                    char ch = source.charAt(i);
                    if (!inTextBlock && ch == '"' && !escaped) inString = !inString;
                    if (inString && ch == '\\' && !escaped) escaped = true; else escaped = false;
                    if (!inString && !inTextBlock) { if (ch == '(') depth++; if (ch == ')') depth--; }
                    i++;
                }
                if (depth != 0) throw new AssertionError("Unclosed @Query in " + repo);
                String annotation = source.substring(open + 1, i - 1);
                String method = methodAfterAnnotation(source, i);
                String operation = repo + "#" + method;
                observed.add(operation);
                List<String> literals = javaStringLiterals(annotation);
                List<String> sqlOrJpql = literals.stream()
                    .filter(value -> Pattern.compile("(?is)\\b(select|update|delete|insert)\\b").matcher(value).find())
                    .toList();
                assertThat(sqlOrJpql).as("query text must be statically visible for %s", operation).isNotEmpty();
                boolean scoped = sqlOrJpql.stream().allMatch(q -> hasTenantPredicate(q) || hasOwnerPredicate(q));
                if (!scoped) assertThat(global).as("unscoped query requires a named global/owner exception: %s", operation)
                    .containsKey(operation);
                else {
                    for (String query : sqlOrJpql) {
                        if (hasTenantPredicate(query) && query.contains(":tenant")) {
                            assertThat(source.substring(i, Math.min(source.length(), i + 1000)))
                                .as("named tenant query parameter must be declared on %s", operation)
                                .contains("tenant");
                        }
                    }
                }
            }

            Set<String> methods = queryMethodNames(source);
            for (String method : methods) {
                String operation = repo + "#" + method;
                if (observed.contains(operation) || hasExplicitScopeToken(method)) continue;
                if (composite.containsKey(operation)) continue;
                assertThat(global).as("derived repository method requires tenant/owner scope token or reviewed exception: %s", operation)
                    .containsKey(operation);
            }
        }
    }

    private static boolean usesUnreviewedSqlExecutionApi(String source) {
        return Pattern.compile("(?is)\\b(?:JdbcClient|DatabaseClient|DSLContext)\\b|"
            + "\\.\\s*(?:prepareStatement|createStatement|executeQuery|executeUpdate|executeLargeUpdate|createNativeQuery|createStoredProcedureQuery|createSQLQuery)\\s*\\(")
            .matcher(source).find();
    }

    @Test
    void everySqlExecutionApiMustUseTheReviewedJdbcOrJpaAnalysisBoundary() throws Exception {
        Set<String> unreviewed=new TreeSet<>();
        try(var files=Files.walk(sourceRoot())){
            for(Path file:files.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".java")).toList()){
                String source=Files.readString(file);
                if(usesUnreviewedSqlExecutionApi(source))
                    unreviewed.add(sourceRoot().relativize(file).toString().replace('\\','/'));
            }
        }
        assertThat(unreviewed).as("unreviewed JDBC clients, raw JDBC statements, and alternate SQL execution APIs")
            .isEmpty();
        assertThat(usesUnreviewedSqlExecutionApi("jdbcClient.sql(dynamicSql).query()")).isTrue();
        assertThat(usesUnreviewedSqlExecutionApi("connection.prepareStatement(dynamicSql)")).isTrue();
        assertThat(usesUnreviewedSqlExecutionApi("entityManager.createNativeQuery(dynamicSql)")).isTrue();
        assertThat(usesUnreviewedSqlExecutionApi("namedJdbc.queryForList(staticSql,params)")).isFalse();
    }

    @Test
    void dynamicJpaQueryConstructionIsProhibitedOutsideReviewedSpecificationBoundary() throws Exception {
        Set<String> dynamicJpa = new TreeSet<>();
        try (var files = Files.walk(sourceRoot())) {
            for (Path file : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (Pattern.compile("\\b(?:createNativeQuery|createQuery|createStoredProcedureQuery)\\s*\\(").matcher(source).find())
                    dynamicJpa.add(sourceRoot().relativize(file).toString().replace('\\','/'));
            }
        }
        assertThat(dynamicJpa).as("JPA EntityManager query construction must be statically reviewed and tenant-scoped")
            .isEmpty();
        assertSpecificationBoundary();
    }

    private static void assertSpecificationBoundary() throws Exception {
        List<Path> files = repositoryFiles();
        Set<String> specifications = new TreeSet<>();
        for (Path file : files) {
            String source = Files.readString(file);
            if (source.contains("JpaSpecificationExecutor")) specifications.add(repositoryName(file));
        }
        assertThat(specifications).containsExactlyInAnyOrder("CashRefundRepository", "DraftRepository");
        Path service = sourceRoot().resolve("com/saumya/pos/returns/RefundHistoryService.java");
        String source = Files.readString(service);
        assertThat(source).contains("retail.scope()")
            .contains("b.equal(r.get(\"tenant\"), scope.tenant())")
            .contains("b.equal(r.get(\"branch\"), scope.branch())")
            .contains("refunds.findAll(owned");
        Path draftService = sourceRoot().resolve("com/saumya/pos/checkout/OrderHistoryService.java");
        String draftSource = Files.readString(draftService);
        assertThat(draftSource).contains("retail.scope()")
            .contains("b.equal(r.get(\"tenantId\"),scope.tenant())")
            .contains("b.equal(r.get(\"branchId\"),scope.branch())")
            .contains("customers.resolve(username)")
            .contains("orders.findAll(filtered(scope,filter)")
            .contains("orders.findOne(scope.and");
    }

    @Test
    void queryTextCannotMasqueradeAsDerivedRepositoryMethodDeclarations() {
        String source = "@Query(value=\\\"select count(*) from rows\\\")" + System.lineSeparator() + "long countByTenantId(long tenant);";
        assertThat(queryMethodNames(source)).contains("countByTenantId").doesNotContain("count");
    }

    @Test
    void negativeFixturesRejectUnscopedJpaAndNativeQueries() {
        assertThat(hasTenantPredicate("select r from Refund r where r.id=:id")).isFalse();
        assertThat(hasOwnerPredicate("select r from Refund r where r.id=:id")).isFalse();
        assertThat(hasTenantPredicate("select * from refunds where id=?1")).isFalse();
        assertThat(hasTenantPredicate("update refunds set status=? where id=?")).isFalse();
        assertThat(hasTenantPredicate("delete from refunds where id=?")).isFalse();
        assertThat(hasTenantPredicate("select * from refunds where tenant_id=?1 and id=?2")).isTrue();
        assertThat(hasOwnerPredicate("select c from Cart c where c.customerId=:customer and c.id=:id")).isTrue();
    }
    @Test
    void directJdbcTenantParametersHaveReviewedTrustedAuthorityProvenance() throws Exception {
        JsonNode root = policy();
        JsonNode sources = root.path("trustedTenantParameterSources");
        assertThat(sources.isArray()).isTrue();
        Map<String, String> expected = new TreeMap<>();
        for (JsonNode item : sources) {
            String sourceName = item.path("source").asText();
            assertThat(sourceName).isNotBlank();
            assertThat(item.path("authority").asText()).isNotBlank();
            assertThat(item.path("methods").isArray()).isTrue();
            assertThat(item.path("evidence").isArray()).isTrue();
            Path file = sourceRoot().resolve(sourceName.replace('.', '/') + ".java");
            assertThat(Files.isRegularFile(file)).as("trusted JDBC source %s exists", sourceName).isTrue();
            String source = Files.readString(file);
            for (JsonNode evidence : item.path("evidence")) {
                assertThat(source).as("trusted tenant provenance evidence in %s", sourceName)
                    .contains(evidence.asText());
            }
            assertThat(expected.put(sourceName, item.path("authority").asText())).isNull();
        }
        Set<String> actualJdbc = new TreeSet<>();
        try (var files = Files.walk(sourceRoot())) {
            for (Path file : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (source.contains("JdbcTemplate") || source.contains("NamedParameterJdbcTemplate")) {
                    if (Pattern.compile("\\b(?:JdbcTemplate|NamedParameterJdbcTemplate)\\b").matcher(source).find()
                        && (source.contains("queryFor") || source.contains(".update(") || source.contains(".batchUpdate(")))
                        actualJdbc.add(sourceRoot().relativize(file).toString()
                            .replace('\\','/').replace('/', '.').replace(".java",""));
                }
            }
        }
        assertThat(actualJdbc).as("every direct JDBC production consumer has an explicit trusted tenant provenance record")
            .containsExactlyInAnyOrderElementsOf(expected.keySet());
    }

    @Test
    void clientSuppliedTenantIdentifiersCannotFlowIntoProductionServiceOrPersistenceCode() throws Exception {
        Pattern direct = Pattern.compile(
            "(?i)\\b(?:request|body|payload|filter|criteria|query|params|dto)\\s*\\.\\s*(?:get)?tenantId\\b");
        Pattern alias = Pattern.compile(
            "(?i)\\b(?:long|Long|var)\\s+([a-zA-Z_$][\\w$]*)\\s*=\\s*(?:request|body|payload|filter|criteria|query|params|dto)\\s*\\.\\s*(?:get)?tenantId\\s*\\(");
        try (var files = Files.walk(sourceRoot())) {
            for (Path file : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                boolean directRead = direct.matcher(source).find();
                boolean aliasRead = alias.matcher(source).find();
                if (directRead || aliasRead) {
                    String relative = sourceRoot().relativize(file).toString().replace('\\','/');
                    boolean platformTenantSelector = relative.equals("com/saumya/pos/platform/PlatformAdminController.java")
                        && source.contains("@PreAuthorize(\"hasAuthority('PLATFORM_SUPPORT_ACCESS')\")")
                        && source.contains("var actor=require(\"PLATFORM_SUPPORT_ACCESS\")")
                        && source.contains("support.grant(body.tenantId(),actor.username()");
                    assertThat(platformTenantSelector)
                        .as("client tenant IDs are forbidden except a platform-authorized tenant resource selector in %s", relative)
                        .isTrue();
                }
            }
        }
    }

    @Test
    void negativeFixturesRejectForgedTenantAuthorityAndUntrustedAliases() {
        Pattern direct = Pattern.compile(
            "(?i)\\b(?:request|body|payload|filter|criteria|query|params|dto)\\s*\\.\\s*(?:get)?tenantId\\s*\\(");
        Pattern alias = Pattern.compile(
            "(?i)\\b(?:long|Long|var)\\s+([a-zA-Z_$][\\w$]*)\\s*=\\s*(?:request|body|payload|filter|criteria|query|params|dto)\\s*\\.\\s*(?:get)?tenantId\\s*\\(");
        assertThat(direct.matcher("long tenant = request.getTenantId();").find()).isTrue();
        assertThat(alias.matcher("long tenant = request.getTenantId();").find()).isTrue();
        assertThat(direct.matcher("long tenant = authorizationContext.current().tenantId();").find()).isFalse();
    }

    @Test
    void everyInheritedFindByIdCallHasExplicitTrustedResourceScope() throws Exception {
        JsonNode entries = policy().path("inheritedIdLookups");
        assertThat(entries.isArray()).isTrue();
        Set<String> expectedSources = new TreeSet<>();
        for (JsonNode entry : entries) {
            String sourceName = entry.path("source").asText();
            assertThat(entry.path("repository").asText()).isNotBlank();
            assertThat(entry.path("scope").asText()).isNotBlank();
            assertThat(entry.path("authority").asText()).isNotBlank();
            Path file = sourceRoot().resolve(sourceName);
            assertThat(Files.isRegularFile(file)).as("findById source exists: %s", sourceName).isTrue();
            String source = Files.readString(file);
            for (JsonNode evidence : entry.path("evidence")) {
                assertThat(source).as("trusted findById provenance for %s", sourceName)
                    .contains(evidence.asText());
            }
            assertThat(expectedSources.add(sourceName)).as("duplicate findById source %s", sourceName).isTrue();
        }
        Set<String> observedSources = new TreeSet<>();
        try (var files = Files.walk(sourceRoot())) {
            for (Path file : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (Pattern.compile("\\.findById\\s*\\(").matcher(source).find()) {
                    observedSources.add(sourceRoot().relativize(file).toString().replace('\\','/'));
                }
            }
        }
        assertThat(observedSources).as("all inherited Spring Data findById call sites must have a tenant/owner/global scope review")
            .containsExactlyInAnyOrderElementsOf(expectedSources);
    }

    @Test
    void inheritedFindByIdNegativeFixturesRejectUnscopedScalarResourceKeys() {
        assertThat(hasTenantPredicate("select x from TenantOwned x where x.id=:id")).isFalse();
        assertThat(hasTenantPredicate("select x from TenantOwned x where x.tenantId=:tenant and x.id=:id")).isTrue();
        assertThat(hasOwnerPredicate("select x from CustomerOwned x where x.customerId=:customer and x.id=:id")).isTrue();
    }

    @Test
    void directJdbcSqlMustNotBeAssembledFromRuntimeFragments() throws Exception {
        JsonNode sources = policy().path("trustedTenantParameterSources");
        Pattern dynamicArgument = Pattern.compile(
            "(?is)(?:db|jdbc)\\.(?:query|queryFor\\w*|update|execute|batchUpdate)\\s*\\(\\s*(?:[a-zA-Z_$][\\w$]*\\s*\\+|String\\.format\\s*\\(|new\\s+StringBuilder\\s*\\()");
        for (JsonNode item : sources) {
            String sourceName = item.path("source").asText();
            Path file = sourceRoot().resolve(sourceName.replace('.', '/') + ".java");
            String source = Files.readString(file);
            assertThat(dynamicArgument.matcher(source).find())
                .as("JDBC SQL expression must be a reviewed static literal, not runtime concatenation: %s", sourceName)
                .isFalse();
            assertThat(source).as("direct JDBC adapters must not construct SQL through StringBuilder: %s", sourceName)
                .doesNotContain("new StringBuilder", "String.format(");
        }
    }

    @Test
    void dynamicJdbcSqlNegativeFixturesAreRejected() {
        Pattern dynamicArgument = Pattern.compile(
            "(?is)(?:db|jdbc)\\.(?:query|queryFor\\w*|update|execute|batchUpdate)\\s*\\(\\s*(?:[a-zA-Z_$][\\w$]*\\s*\\+|String\\.format\\s*\\(|new\\s+StringBuilder\\s*\\()");
        assertThat(dynamicArgument.matcher("db.queryForList(sql + filter, tenant)").find()).isTrue();
        assertThat(dynamicArgument.matcher("db.update(String.format(\"DELETE FROM rows WHERE tenant_id=%s\", tenant))").find()).isTrue();
        assertThat(dynamicArgument.matcher("db.query(new StringBuilder(\"SELECT * FROM rows\").toString())").find()).isTrue();
        assertThat(dynamicArgument.matcher("db.queryForList(\"SELECT * FROM rows WHERE tenant_id=?\", tenant)").find()).isFalse();
    }

    @Test
    void unscopedInheritedBulkRepositoryOperationsAreProhibited() throws Exception {
        Pattern unscopedRead = Pattern.compile("\\.findAll\\s*\\(\\s*\\)");
        Pattern bulkMutation = Pattern.compile("\\.(findAllById|deleteAll|deleteAllInBatch|deleteAllById|deleteById)\\s*\\(");
        Map<String, String> reviewed = policyEntries(policy(), "scopedBulkMethods");
        try (var files = Files.walk(sourceRoot())) {
            for (Path file : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                String relative = sourceRoot().relativize(file).toString().replace('\\','/');
                Matcher reads=unscopedRead.matcher(source);
                int approvedGlobalReads=0;
                while(reads.find()){
                    boolean approvedGlobalCatalogRead=relative.equals("com/saumya/pos/subscription/SubscriptionPlanServiceImpl.java")
                        && reviewed.containsKey("SubscriptionPlanServiceImpl#list")
                        && source.contains("requireRead();return plans.findAll()")
                        && source.contains("context.isPlatformUser()")
                        && source.contains("context.hasAnyPermission(");
                    boolean approvedGlobalConfigurationRead=relative.equals("com/saumya/pos/platform/PlatformConfigurationServiceImpl.java")
                        && reviewed.containsKey("PlatformConfigurationServiceImpl#list")
                        && source.contains("PlatformAuthorization.require(\"PLATFORM_CONFIG_MANAGE\",null)")
                        && source.contains("return repository.findAll()");
                    assertThat(approvedGlobalCatalogRead || approvedGlobalConfigurationRead)
                        .as("inherited findAll() requires a reviewed platform/global resource method in %s",file).isTrue();
                    approvedGlobalReads++;
                }
                if(relative.equals("com/saumya/pos/subscription/SubscriptionPlanServiceImpl.java"))
                    assertThat(approvedGlobalReads).as("only the reviewed platform plan catalogue may use inherited findAll()").isEqualTo(1);
                Matcher calls = bulkMutation.matcher(source);
                while (calls.find()) {
                    String method = calls.group(1);
                    boolean approvedCompositeDelete = method.equals("deleteById")
                        && relative.equals("com/saumya/pos/checkout/BuyerDetailsService.java")
                        && reviewed.containsKey("BuyerDetailsService#discardUnpaid")
                        && source.contains("snapshots.deleteById(new BuyerSnapshot.Key(tenant,id))");
                    assertThat(approvedCompositeDelete)
                        .as("inherited bulk/delete-by-ID operation %s requires a reviewed tenant-scoped key in %s", method, relative)
                        .isTrue();
                }
            }
        }
        assertThat(reviewed).containsKey("BuyerDetailsService#discardUnpaid");
        assertThat(reviewed).containsKey("SubscriptionPlanServiceImpl#list");
        assertThat(reviewed).containsKey("PlatformConfigurationServiceImpl#list");
    }

    @Test
    void negativeFixturesRejectUnscopedInheritedRepositoryMethods() {
        Pattern unscopedRead = Pattern.compile("\\.findAll\\s*\\(\\s*\\)");
        Pattern bulkMutation = Pattern.compile("\\.(?:findAllById|deleteAll|deleteAllInBatch|deleteAllById|deleteById)\\s*\\(");
        Pattern unscopedCount = Pattern.compile("\\.count\\s*\\(\\s*\\)");
        assertThat(unscopedRead.matcher("repository.findAll()").find()).isTrue();
        assertThat(bulkMutation.matcher("repository.deleteById(id)").find()).isTrue();
        assertThat(bulkMutation.matcher("repository.findAllById(ids)").find()).isTrue();
        assertThat(unscopedRead.matcher("repository.findAll(tenantSpecification)").find()).isFalse();
    }

}
