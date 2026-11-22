package com.saumya.pos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * JDK compiler-tree analysis of JDBC SQL call sites. This parses Java syntax (including
 * text blocks and nested invocations) rather than relying on source substring allowlists.
 */
class JdbcTenantSqlAstAnalysisTest {
    private static Path sourceRoot() {
        Path path=Path.of("backend/src/main/java");
        if(Files.isDirectory(path))return path;
        return Path.of("src/main/java");
    }
    private static JsonNode policy() throws Exception {
        try(InputStream in=getClassResource()) {
            assertThat(in).as("tenant data-access policy resource").isNotNull();
            return new ObjectMapper().readTree(in);
        }
    }
    private static InputStream getClassResource() {
        return JdbcTenantSqlAstAnalysisTest.class.getResourceAsStream("/tenant-data-access-policy.json");
    }
    private static String literal(ExpressionTree expression) {
        if(expression instanceof LiteralTree value && value.getValue() instanceof String s)return s;
        if(expression instanceof BinaryTree binary && binary.getKind()==Tree.Kind.PLUS) {
            String left=literal(binary.getLeftOperand()),right=literal(binary.getRightOperand());
            return left==null||right==null?null:left+right;
        }
        return null;
    }
    private static String expressionText(CompilationUnitTree unit,Tree tree,SourcePositions positions,String source) {
        long start=positions.getStartPosition(unit,tree),end=positions.getEndPosition(unit,tree);
        if(start<0||end<start||end>source.length())return tree.toString();
        return source.substring((int)start,(int)end);
    }
    private static boolean tenantPredicate(String sql) {
        return java.util.regex.Pattern.compile("(?is)(?:where|and|on)\\s+(?:[a-z_][a-z0-9_]*\\.)?tenant_id\\s*=\\s*\\?")
            .matcher(sql).find();
    }
    private static List<String> splitArguments(String text) {
        List<String> result=new ArrayList<>();int start=0,depth=0;char quote=0;boolean escaped=false;
        for(int i=0;i<text.length();i++){
            char ch=text.charAt(i);
            if(quote!=0){if(escaped){escaped=false;continue;}if(ch=='\\'){escaped=true;continue;}if(ch==quote)quote=0;continue;}
            if(ch=='"'||ch=='\''){quote=ch;continue;}
            if(ch=='('||ch=='{'||ch=='[')depth++;
            else if(ch==')'||ch=='}'||ch==']')depth--;
            else if(ch==','&&depth==0){result.add(text.substring(start,i).trim());start=i+1;}
        }
        if(start<text.length())result.add(text.substring(start).trim());
        return result;
    }
    private static int tenantPlaceholderIndex(String sql) {
        var match=java.util.regex.Pattern.compile("(?is)(?:where|and|on)\\s+(?:[a-z_][a-z0-9_]*\\.)?tenant_id\\s*=\\s*\\?").matcher(sql);
        if(match.find())return (int)sql.substring(0,match.start()).chars().filter(ch->ch=='?').count();
        var insert=java.util.regex.Pattern.compile("(?is)insert\\s+into\\s+[a-z_][a-z0-9_]*\\s*\\(([^)]*)\\)\\s*values\\s*\\(([^)]*)\\)").matcher(sql);
        if(insert.find()){
            List<String> columns=splitArguments(insert.group(1));
            for(int i=0;i<columns.size();i++)if(columns.get(i).trim().equalsIgnoreCase("tenant_id"))return i;
        }
        return -1;
    }
    private static String trustedParameterAt(List<? extends ExpressionTree> args,int offset,int tenantIndex,
                                             String methodName,boolean batch) {
        if(batch && args.size()>1){
            String batchArgs=args.get(1).toString();
            var array=java.util.regex.Pattern.compile("(?s)new\\s+Object\\s*\\[\\s*\\]\\s*\\s*\\{([^}]*)\\}").matcher(batchArgs);
            if(array.find()){
                List<String> values=splitArguments(array.group(1));
                if(tenantIndex<values.size())return values.get(tenantIndex);
            }
        }
        int position=offset+tenantIndex;
        return position>=0&&position<args.size()?args.get(position).toString():null;
    }
    private static boolean tenantInsert(String sql) {
        var m=java.util.regex.Pattern.compile("(?is)insert\\s+into\\s+[a-z_][a-z0-9_]*\\s*\\(([^)]*)\\)\\s*values\\s*\\(([^)]*)\\)").matcher(sql);
        return m.find() && java.util.Arrays.stream(m.group(1).split(",")).anyMatch(c->c.trim().equalsIgnoreCase("tenant_id"));
    }
    private static boolean platformTenantSelector(String sql) {
        return java.util.regex.Pattern.compile("(?is)select\\s+id\\s+from\\s+tenants\\s+where\\s+id\\s*=\\s*\\?").matcher(sql).find();
    }
    private static boolean hasTrustedTenantArgument(String args) {
        if (java.util.regex.Pattern.compile("(?i)\\b(?:request|body|payload|dto|filter|criteria)\\s*\\.\\s*(?:get)?tenantId\\s*\\(").matcher(args).find()) return false;
        return java.util.regex.Pattern.compile("(?i)(?:\\btenant(?:Id)?\\b|\\b(?:auth|authorization|ctx|c|actor|scope|s|event|m|order)\\s*\\.|\\.tenant\\s*\\(|\\.tenantId\\s*\\()")
            .matcher(args).find();
    }
    private static Set<String> trustedSources(JsonNode policy) {
        Set<String> result=new TreeSet<>();
        policy.path("trustedTenantParameterSources").forEach(item->result.add(item.path("source").asText()));
        return result;
    }

    @Test
    void everyJdbcSqlCallUsesStaticSqlAndTrustedTenantProvenance() throws Exception {
        JsonNode registry=policy();
        Map<String,JsonNode> trusted=new TreeMap<>();
        registry.path("trustedTenantParameterSources").forEach(item->trusted.put(item.path("source").asText(),item));
        Set<String> observedSources=new TreeSet<>(), observedSqlSites=new TreeSet<>();
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK compiler is required for AST analysis").isNotNull();
        List<Path> javaFiles;
        try(var stream=Files.walk(sourceRoot())) {
            javaFiles=stream.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".java")).sorted().toList();
        }
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(null,null,null)) {
            Iterable<? extends JavaFileObject> units=manager.getJavaFileObjectsFromPaths(javaFiles);
            JavacTask task=(JavacTask)compiler.getTask(null,manager,null,List.of("-proc:none"),null,units);
            Trees trees=Trees.instance(task);
            SourcePositions positions=trees.getSourcePositions();
            for(CompilationUnitTree unit:task.parse()) {
                Path file=Path.of(unit.getSourceFile().toUri());
                String relative=sourceRoot().toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString().replace('\\','/');
                String className=relative.replace('/','.').replace(".java","");
                String fileSource=Files.readString(file);
                JsonNode classPolicy=trusted.get(className);
                new TreePathScanner<Void,Void>() {
                    String methodName="";
                    @Override public Void visitMethod(MethodTree node,Void unused) {
                        String previous=methodName;methodName=node.getName().toString();
                        try{return super.visitMethod(node,unused);}finally{methodName=previous;}
                    }
                    @Override public Void visitMethodInvocation(MethodInvocationTree node,Void unused) {
                        String call=node.getMethodSelect().toString();
                        String name=call.substring(call.lastIndexOf('.')+1);
                        List<String> sqlMethods=List.of("query","queryForList","queryForObject","queryForMap","queryForRowSet","update","batchUpdate","execute");
                        if(name.equals("one") && className.equals("com.saumya.pos.retail.PosService")) {
                            assertThat(node.getArguments()).as("PosService.one call has SQL and tenant arguments: %s",node).isNotEmpty();
                            String sql=literal(node.getArguments().getFirst());
                            assertThat(sql).as("every PosService.one call must pass a static SQL literal: %s",node).isNotNull();
                            String args=node.getArguments().stream().skip(1).map(Object::toString).reduce("",(a,b)->a+" "+b);
                            assertThat(hasTrustedTenantArgument(args)).as("PosService.one call must receive trusted tenant provenance: %s",node).isTrue();
                        }
                        if(sqlMethods.contains(name) && call.contains(".")) {
                            String receiver=call.substring(0,call.lastIndexOf('.'));
                            if(!receiver.matches("(?:this\\.)?(?:db|jdbc|namedJdbc|jdbcTemplate|template)"))return super.visitMethodInvocation(node,unused);
                            assertThat(classPolicy).as("JDBC SQL consumer must have a trusted-source policy: %s",className).isNotNull();
                            observedSources.add(className);
                            JsonNode methods=classPolicy.path("methods");
                            boolean declared=methods.isArray() && (methods.toString().contains("\"*\"") ||
                                java.util.stream.StreamSupport.stream(methods.spliterator(),false).anyMatch(x->x.asText().equals(methodName)));
                            assertThat(declared).as("JDBC method %s#%s must be explicitly covered by provenance inventory",className,methodName).isTrue();
                            assertThat(node.getArguments()).as("SQL method has a first argument: %s",node).isNotEmpty();
                            ExpressionTree sqlArg=node.getArguments().getFirst();
                            String sql=literal(sqlArg);
                            if(sql==null && className.equals("com.saumya.pos.retail.PosService") && methodName.equals("one")
                                && sqlArg instanceof IdentifierTree identifier && identifier.getName().contentEquals("sql")) {
                                // The only delegated SQL variable is the private one(sql,args) helper; all call sites above are checked.
                                return super.visitMethodInvocation(node,unused);
                            }
                            assertThat(sql).as("JDBC SQL must be a literal or constant expression, never runtime concatenation: %s",node).isNotNull();
                            String normalized=sql.replaceAll("\\s+"," ").trim();
                            observedSqlSites.add(className+"#"+methodName+" "+Integer.toHexString(normalized.hashCode()));
                            boolean scoped=tenantPredicate(sql)||tenantInsert(sql);
                            if(!scoped) {
                                assertThat(platformTenantSelector(sql))
                                    .as("unscoped JDBC SQL requires an explicitly platform-owned tenant-directory selector: %s",node).isTrue();
                            } else {
                                int tenantIndex=tenantPlaceholderIndex(sql);
                                assertThat(tenantIndex).as("tenant SQL must have a traceable positional tenant parameter: %s",node).isGreaterThanOrEqualTo(0);
                                boolean delegated=className.equals("com.saumya.pos.retail.PosService")&&methodName.equals("one");
                                String tenantArgument;
                                if(delegated) tenantArgument=trustedParameterAt(node.getArguments(),1,tenantIndex,methodName,false);
                                else {
                                    boolean batch=name.equals("batchUpdate");
                                    int offset=(name.equals("query")||name.equals("queryForList")||name.equals("queryForObject"))
                                        && node.getArguments().size()>1
                                        && (node.getArguments().get(1).toString().endsWith(".class")
                                            || name.equals("query") || name.equals("queryForObject")) ? 2 : 1;
                                    tenantArgument=trustedParameterAt(node.getArguments(),offset,tenantIndex,methodName,batch);
                                }
                                assertThat(tenantArgument).as("tenant placeholder %s must bind to an explicit trusted argument in %s",tenantIndex,node).isNotNull();
                                assertThat(hasTrustedTenantArgument(tenantArgument))
                                    .as("tenant placeholder must bind to authenticated actor, persisted membership, platform authorization or trusted domain event: %s",node).isTrue();
                            }
                        }
                        return super.visitMethodInvocation(node,unused);
                    }
                }.scan(unit,null);
            }
        }
        assertThat(observedSources).as("all trusted direct-JDBC source inventory entries are reached by AST analysis")
            .containsExactlyInAnyOrderElementsOf(trustedSources(registry));
        assertThat(observedSqlSites).as("AST analyzer must discover direct JDBC SQL operations").isNotEmpty();
    }

    @Test
    void sqlAstNegativeFixturesRejectDynamicAndUnscopedStatements() {
        assertThat(tenantPredicate("SELECT * FROM vendor_roles WHERE id=?")).isFalse();
        assertThat(tenantPredicate("SELECT * FROM vendor_roles WHERE tenant_id=? AND id=?")).isTrue();
        assertThat(tenantInsert("INSERT INTO vendor_roles(id,role_key) VALUES(?,?)")).isFalse();
        assertThat(tenantInsert("INSERT INTO vendor_roles(tenant_id,role_key) VALUES(?,?)")).isTrue();
        assertThat(hasTrustedTenantArgument("request.getTenantId()")).isFalse();
        assertThat(tenantPlaceholderIndex("SELECT * FROM vendor_roles WHERE id=? AND tenant_id=?")).isEqualTo(1);
        assertThat(tenantPlaceholderIndex("INSERT INTO vendor_roles(id,tenant_id,role_key) VALUES(?,?,?)")).isEqualTo(1);
        assertThat(hasTrustedTenantArgument("authorization.current().tenantId()")).isTrue();
    }
}
