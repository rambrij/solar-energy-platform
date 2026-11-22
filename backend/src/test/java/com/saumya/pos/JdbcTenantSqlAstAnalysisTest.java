package com.saumya.pos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
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
    private static String maskSqlLiteralsAndComments(String sql) {
        StringBuilder masked=new StringBuilder(sql.length());
        char quote=0;boolean lineComment=false,blockComment=false;
        for(int i=0;i<sql.length();i++){
            char ch=sql.charAt(i),next=i+1<sql.length()?sql.charAt(i+1):0;
            if(lineComment){masked.append(ch==10||ch==13?ch:' ');if(ch==10||ch==13)lineComment=false;continue;}
            if(blockComment){masked.append(ch==10||ch==13?ch:' ');if(ch=='*'&&next=='/'){masked.append(' ');i++;blockComment=false;}continue;}
            if(quote!=0){
                masked.append(ch==10||ch==13?ch:' ');
                if(ch==quote){
                    if(i+1<sql.length()&&sql.charAt(i+1)==quote){masked.append(' ');i++;}
                    else quote=0;
                }else if(ch==92&&i+1<sql.length()){masked.append(' ');i++;}
                continue;
            }
            if(ch=='-'&&next=='-'){masked.append("  ");i++;lineComment=true;continue;}
            if(ch=='/'&&next=='*'){masked.append("  ");i++;blockComment=true;continue;}
            if(ch==39||ch==34||ch==96){quote=ch;masked.append(' ');continue;}
            masked.append(ch);
        }
        return masked.toString();
    }
    private static int countSqlPlaceholders(String sql,int before) {
        String masked=maskSqlLiteralsAndComments(sql.substring(0,Math.min(before,sql.length())));
        return (int)masked.chars().filter(ch->ch=='?').count();
    }
    private static boolean tenantPredicate(String sql) {
        String searchable=maskSqlLiteralsAndComments(sql);
        return java.util.regex.Pattern.compile("(?is)(?:where|and|on)\\s+(?:[a-z_][a-z0-9_]*\\.)?tenant_id\\s*=\\s*\\?")
            .matcher(searchable).find();
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
        String searchable=maskSqlLiteralsAndComments(sql);
        var match=java.util.regex.Pattern.compile("(?is)(?:where|and|on)\\s+(?:[a-z_][a-z0-9_]*\\.)?tenant_id\\s*=\\s*\\?").matcher(searchable);
        if(match.find())return countSqlPlaceholders(sql,match.end()-1);
        var insert=java.util.regex.Pattern.compile("(?is)insert\\s+into\\s+[a-z_][a-z0-9_]*\\s*\\(([^)]*)\\)\\s*values\\s*\\(([^)]*)\\)").matcher(searchable);
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
    private static boolean clientOrigin(String expression,Set<String> tainted) {
        if(java.util.regex.Pattern.compile("(?i)\\b(?:request|body|payload|dto|filter|criteria|query|params|form|input)\\s*\\.\\s*(?:get)?tenantId\\b").matcher(expression).find())return true;
        for(String variable:tainted)if(java.util.regex.Pattern.compile("\\b"+java.util.regex.Pattern.quote(variable)+"\\b").matcher(expression).find())return true;
        return false;
    }
    private static Set<String> jdbcReceiverNames(String source){
        Set<String> names=new TreeSet<>();
        var matcher=Pattern.compile("\\b(?:JdbcTemplate|NamedParameterJdbcTemplate|JdbcOperations)\\s+([A-Za-z_$][\\w$]*)").matcher(source);
        while(matcher.find())names.add(matcher.group(1));
        return names;
    }
    private static boolean hasTrustedTenantArgument(String expression) {
        if(clientOrigin(expression,Set.of()))return false;
        return java.util.regex.Pattern.compile("(?i)(?:\\b(?:auth|authorization|ctx|c|actor|scope|s|event|m|order)\\s*\\.|\\.tenant\\s*\\(|\\.tenantId\\s*\\()")
            .matcher(expression).find();
    }
    private static boolean hasTrustedTenantArgument(String expression,String className,String methodName,JsonNode classPolicy) {
        if(hasTrustedTenantArgument(expression))return true;
        if(clientOrigin(expression,Set.of())||classPolicy==null)return false;
        var bare=java.util.regex.Pattern.compile("^\\s*([a-zA-Z_$][\\w$]*)\\s*$").matcher(expression);
        if(!bare.matches())return false;
        String parameter=bare.group(1);
        JsonNode declared=classPolicy.path("trustedTenantParameters").path(methodName);
        if(!declared.isArray())return false;
        for(JsonNode value:declared)if(value.asText().equals(parameter))return true;
        return false;
    }
    private static int matchingJavaBrace(String source,int open){
        int depth=0;char quote=0;boolean escaped=false,lineComment=false,blockComment=false,textBlock=false;
        for(int i=open;i<source.length();i++){
            if(textBlock){
                if(i+2<source.length()&&source.startsWith("\"\"\"",i)){textBlock=false;i+=2;}
                continue;
            }
            char ch=source.charAt(i),next=i+1<source.length()?source.charAt(i+1):0;
            if(lineComment){if(ch=='\n'||ch=='\r')lineComment=false;continue;}
            if(blockComment){if(ch=='*'&&next=='/'){blockComment=false;i++;}continue;}
            if(quote!=0){
                if(escaped){escaped=false;continue;}
                if(ch=='\\'){escaped=true;continue;}
                if(ch==quote)quote=0;
                continue;
            }
            if(ch=='/'&&next=='/'){lineComment=true;i++;continue;}
            if(ch=='/'&&next=='*'){blockComment=true;i++;continue;}
            if(ch=='"'&&i+2<source.length()&&source.startsWith("\"\"\"",i)){textBlock=true;i+=2;continue;}
            if(ch=='"'||ch=='\''){quote=ch;continue;}
            if(ch=='{')depth++;
            else if(ch=='}'&&--depth==0)return i;
        }
        return -1;
    }
    private static List<String> methodBodies(String source,String methodName){
        List<String> bodies=new ArrayList<>();
        var declaration=Pattern.compile("(?s)\\b"+Pattern.quote(methodName)+"\\s*\\([^;{}]*\\)\\s*\\{").matcher(source);
        while(declaration.find()){
            int open=source.indexOf('{',declaration.start()),close=matchingJavaBrace(source,open);
            if(close<0)throw new AssertionError("Unclosed method body for "+methodName);
            bodies.add(source.substring(open+1,close));
            declaration.region(close+1,source.length());
        }
        return bodies;
    }
    private static String methodBody(String source,String methodName){
        List<String> bodies=methodBodies(source,methodName);
        if(bodies.isEmpty())throw new AssertionError("Cannot locate method body for "+methodName);
        return bodies.getFirst();
    }
    private static List<List<String>> invocationArguments(String body,String methodName){
        List<List<String>> result=new ArrayList<>();
        var calls=Pattern.compile("\\b"+Pattern.quote(methodName)+"\\s*\\(").matcher(body);
        while(calls.find()){
            int open=body.indexOf('(',calls.start()),depth=0;char quote=0;boolean escaped=false;
            for(int i=open;i<body.length();i++){
                char ch=body.charAt(i);
                if(quote!=0){if(escaped){escaped=false;continue;}if(ch=='\\'){escaped=true;continue;}if(ch==quote)quote=0;continue;}
                if(ch=='"'||ch=='\''){quote=ch;continue;}
                if(ch=='(')depth++;
                else if(ch==')'&&--depth==0){result.add(splitArguments(body.substring(open+1,i)));break;}
            }
        }
        return result;
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
        Map<String,Set<String>> observedMethods=new TreeMap<>();
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
                Set<String> jdbcReceivers=jdbcReceiverNames(fileSource);
                new TreePathScanner<Void,Void>() {
                    String methodName="";
                    Set<String> taintedTenantVariables=new HashSet<>();
                    @Override public Void visitMethod(MethodTree node,Void unused) {
                        String previous=methodName;Set<String> previousTainted=taintedTenantVariables;
                        methodName=node.getName().toString();taintedTenantVariables=new HashSet<>();
                        try{return super.visitMethod(node,unused);}finally{methodName=previous;taintedTenantVariables=previousTainted;}
                    }
                    @Override public Void visitVariable(VariableTree node,Void unused) {
                        if(node.getInitializer()!=null&&clientOrigin(node.getInitializer().toString(),taintedTenantVariables))
                            taintedTenantVariables.add(node.getName().toString());
                        return super.visitVariable(node,unused);
                    }
                    @Override public Void visitAssignment(AssignmentTree node,Void unused) {
                        if(node.getVariable() instanceof IdentifierTree id&&clientOrigin(node.getExpression().toString(),taintedTenantVariables))
                            taintedTenantVariables.add(id.getName().toString());
                        return super.visitAssignment(node,unused);
                    }
                    @Override public Void visitMethodInvocation(MethodInvocationTree node,Void unused) {
                        String call=node.getMethodSelect().toString();
                        String name=call.substring(call.lastIndexOf('.')+1);
                        List<String> sqlMethods=List.of("query","queryForList","queryForObject","queryForMap","queryForRowSet","update","batchUpdate","execute");
                        if(name.equals("one") && className.equals("com.saumya.pos.retail.PosService")) {
                            observedMethods.computeIfAbsent(className,k->new TreeSet<>()).add(methodName);
                            assertThat(node.getArguments()).as("PosService.one call has SQL and tenant arguments: %s",node).isNotEmpty();
                            String sql=literal(node.getArguments().getFirst());
                            assertThat(sql).as("every PosService.one call must pass a static SQL literal: %s",node).isNotNull();
                            int tenantIndex=tenantPlaceholderIndex(sql);
                            assertThat(tenantIndex).as("PosService.one SQL must expose a tenant placeholder before binding arguments: %s",node).isGreaterThanOrEqualTo(0);
                            String tenantArgument=trustedParameterAt(node.getArguments(),1,tenantIndex,methodName,false);
                            assertThat(tenantArgument).as("PosService.one tenant placeholder %s must bind to the corresponding argument: %s",tenantIndex,node).isNotNull();
                            assertThat(clientOrigin(tenantArgument,taintedTenantVariables)).as("forged tenant alias cannot flow into the exact PosService.one tenant placeholder: %s",node).isFalse();
                            assertThat(hasTrustedTenantArgument(tenantArgument)).as("PosService.one tenant placeholder must receive trusted actor/membership/event scope, not merely another trusted-looking argument: %s",node).isTrue();
                        }
                        if(sqlMethods.contains(name) && call.contains(".")) {
                            String receiver=call.substring(0,call.lastIndexOf('.'));
                            String receiverName=receiver.replaceFirst("^this\\.","");
                            if(!receiver.matches("(?:this\\.)?(?:db|jdbc|namedJdbc|jdbcTemplate|template)")
                                &&!jdbcReceivers.contains(receiverName))return super.visitMethodInvocation(node,unused);
                            assertThat(classPolicy).as("JDBC SQL consumer must have a trusted-source policy: %s",className).isNotNull();
                            observedSources.add(className);
                            observedMethods.computeIfAbsent(className,k->new TreeSet<>()).add(methodName);
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
                                assertThat(clientOrigin(tenantArgument,taintedTenantVariables))
                                    .as("forged tenant value or transitive alias cannot bind a SQL tenant placeholder: %s",node).isFalse();
                                assertThat(hasTrustedTenantArgument(tenantArgument,className,methodName,classPolicy))
                                    .as("tenant placeholder must bind to authenticated context, trusted domain event or a parameter with an explicit caller-flow proof: %s",node).isTrue();
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
        for(JsonNode item:registry.path("trustedTenantParameterSources")){
            String source=item.path("source").asText();
            Set<String> declared=new TreeSet<>();
            item.path("methods").forEach(value->declared.add(value.asText()));
            assertThat(declared).as("JDBC provenance methods must be explicit, not wildcard, for %s",source).doesNotContain("*");
            assertThat(observedMethods.getOrDefault(source,Set.of()))
                .as("method-level JDBC provenance inventory must be exhaustive for %s",source)
                .containsExactlyInAnyOrderElementsOf(declared);
        }
        observedMethods.forEach((source,methods)->System.out.println("P2-04_JDBC_METHODS|"+source+"|"+String.join(",",methods)));
    }

    @Test
    void delegatedPosServiceSqlHelperMustForwardTheValidatedArgumentsUnchanged() throws Exception {
        Path service=sourceRoot().resolve("com/saumya/pos/retail/PosService.java");
        String source=Files.readString(service);
        assertThat(source).contains("private Map<String,Object> one(String sql,Object...args)")
            .contains("return db.queryForList(sql,args).stream().findFirst()");
        assertThat(Pattern.compile("(?s)private Map<String,Object> one\\(String sql,Object\\.\\.\\.args\\)\\s*\\{[^}]*db\\.queryForList\\(sql,args\\)").matcher(source).find())
            .as("the delegated SQL helper must forward its exact validated SQL and argument array").isTrue();
        assertThat(Pattern.compile("\\bone\\s*\\(sql\\s*,").matcher(source).find())
            .as("the helper cannot be called with a runtime SQL variable at an unreviewed call site").isFalse();
    }

    @Test
    void tenantSqlAnalysisIgnoresQuestionMarksAndPredicatesInsideLiteralsAndComments() {
        String query="SELECT * FROM rows WHERE note='tenant_id=?' AND status=? /* tenant_id=? */ AND tenant_id=?";
        assertThat(tenantPredicate(query)).isTrue();
        assertThat(tenantPlaceholderIndex(query)).isEqualTo(1);
        assertThat(tenantPredicate("SELECT * FROM rows WHERE note='tenant_id=?'")).isFalse();
        assertThat(tenantPredicate("SELECT * FROM rows WHERE id=? -- AND tenant_id=?")).isFalse();
        assertThat(tenantPlaceholderIndex("SELECT * FROM rows WHERE id=? AND tenant_id=? /* ? */")).isEqualTo(1);
    }

    private static Set<String> qualifiedCallsiteFiles(String receiver,String methodName) throws Exception {
        Set<String> files=new TreeSet<>();
        Pattern call=Pattern.compile("\\b"+Pattern.quote(receiver)+"\\s*\\.\\s*"+Pattern.quote(methodName)+"\\s*\\(");
        try(var paths=Files.walk(sourceRoot())){
            for(Path file:paths.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".java")).toList()){
                if(call.matcher(Files.readString(file)).find())
                    files.add(sourceRoot().relativize(file).toString().replace('\\','/'));
            }
        }
        return files;
    }
    @Test
    void everyBareTrustedTenantParameterHasAnExactReviewedCallerFlow() throws Exception {
        JsonNode root=policy();
        Map<String,JsonNode> sourcePolicies=new TreeMap<>();
        root.path("trustedTenantParameterSources").forEach(item->sourcePolicies.put(item.path("source").asText(),item));
        JsonNode flows=root.path("trustedTenantParameterFlows");
        assertThat(flows.isArray()).isTrue();
        Set<String> proven=new TreeSet<>();
        for(JsonNode flow:flows){
            String callee=flow.path("callee").asText(),parameter=flow.path("parameter").asText();
            int split=callee.lastIndexOf('#');
            assertThat(split).isGreaterThan(0);
            String calleeClass=callee.substring(0,split),calleeMethod=callee.substring(split+1);
            JsonNode sourcePolicy=sourcePolicies.get(calleeClass);
            assertThat(sourcePolicy).as("trusted parameter class policy for %s",callee).isNotNull();
            JsonNode allowed=sourcePolicy.path("trustedTenantParameters").path(calleeMethod);
            assertThat(allowed.isArray()).as("bare tenant parameter declaration for %s",callee).isTrue();
            assertThat(allowed.toString()).contains("\""+parameter+"\"");
            Path calleeFile=sourceRoot().resolve(calleeClass.replace('.','/')+".java");
            assertThat(Files.isRegularFile(calleeFile)).isTrue();
            String calleeSource=Files.readString(calleeFile);
            assertThat(methodBody(calleeSource,calleeMethod)).isNotBlank();
            for(JsonNode caller:flow.path("callers")){
                String callerRef=caller.path("caller").asText();
                int callerSplit=callerRef.lastIndexOf('#');
                String callerClass=callerRef.substring(0,callerSplit),callerMethod=callerRef.substring(callerSplit+1);
                Path callerFile=sourceRoot().resolve(callerClass.replace('.','/')+".java");
                String callerSource=Files.readString(callerFile);
                List<String> bodies=methodBodies(callerSource,callerMethod);
                int argumentIndex=caller.path("argumentIndex").asInt(-1);
                String expression=caller.path("expression").asText();
                boolean exactFlow=bodies.stream().flatMap(body->invocationArguments(body,calleeMethod).stream())
                    .anyMatch(args->argumentIndex>=0&&argumentIndex<args.size()&&args.get(argumentIndex).contains(expression));
                assertThat(exactFlow)
                    .as("%s argument %s must flow unchanged from %s into %s",parameter,expression,callerRef,callee)
                    .isTrue();
                proven.add(callee+"#"+parameter);
            }
        }
        for(JsonNode source:root.path("trustedTenantParameterSources")){
            String className=source.path("source").asText();
            JsonNode declared=source.path("trustedTenantParameters");
            declared.fields().forEachRemaining(method->method.getValue().forEach(parameter->
                assertThat(proven).as("bare tenant parameter requires caller-flow evidence")
                    .contains(className+"#"+method.getKey()+"#"+parameter.asText())));
        }
        assertThat(qualifiedCallsiteFiles("memberships","provisionInitialVendorAdmin"))
            .containsExactly("com/saumya/pos/platform/PlatformAdminService.java");
        assertThat(qualifiedCallsiteFiles("branches","createInitialBranch"))
            .containsExactly("com/saumya/pos/platform/PlatformAdminService.java");
        assertThat(qualifiedCallsiteFiles("StandardRoleTemplates","lockTenant"))
            .containsExactlyInAnyOrder("com/saumya/pos/membership/MembershipService.java",
                "com/saumya/pos/membership/VendorRoleService.java");
        assertThat(qualifiedCallsiteFiles("StandardRoleTemplates","initialize"))
            .containsExactly("com/saumya/pos/membership/MembershipService.java");
        assertThat(qualifiedCallsiteFiles("StandardRoleTemplates","insert"))
            .containsExactly("com/saumya/pos/membership/VendorRoleService.java");
        assertThat(qualifiedCallsiteFiles("entitlements","permissions"))
            .containsExactly("com/saumya/pos/membership/MembershipService.java");
        assertThat(qualifiedCallsiteFiles("retail","requireOpenForCustomer"))
            .containsExactly("com/saumya/pos/checkout/CustomerAssociationService.java");
        assertThat(qualifiedCallsiteFiles("retail","allocateReceiptForCustomer"))
            .containsExactly("com/saumya/pos/checkout/PaymentCheckoutService.java");
        assertThat(qualifiedCallsiteFiles("retail","customerDraftAudit"))
            .containsExactly("com/saumya/pos/checkout/CustomerAssociationService.java");
        assertThat(qualifiedCallsiteFiles("entitlements","requireFeature")).isEmpty();
        JsonNode membershipPolicy=sourcePolicies.get("com.saumya.pos.membership.MembershipService");
        assertThat(hasTrustedTenantArgument("tenant","com.saumya.pos.membership.MembershipService","access",membershipPolicy)).isFalse();
        assertThat(hasTrustedTenantArgument("tenant","com.saumya.pos.membership.MembershipService","effectiveVendorPermissions",membershipPolicy)).isTrue();
        assertThat(hasTrustedTenantArgument("requestTenant","com.saumya.pos.membership.MembershipService","effectiveVendorPermissions",membershipPolicy)).isFalse();
    }

    @Test
    void tenantProvisioningAndEntitlementHelpersPreserveTrustedTenantProvenanceAcrossServiceBoundaries() throws Exception {
        Path platformPath=sourceRoot().resolve("com/saumya/pos/platform/PlatformAdminService.java");
        Path membershipPath=sourceRoot().resolve("com/saumya/pos/membership/MembershipService.java");
        Path branchPath=sourceRoot().resolve("com/saumya/pos/retail/BranchProvisioningServiceImpl.java");
        Path rolePath=sourceRoot().resolve("com/saumya/pos/membership/StandardRoleTemplates.java");
        Path roleServicePath=sourceRoot().resolve("com/saumya/pos/membership/VendorRoleService.java");
        Path entitlementPath=sourceRoot().resolve("com/saumya/pos/membership/TenantEntitlementPolicy.java");
        String platform=Files.readString(platformPath),membership=Files.readString(membershipPath);
        String branch=Files.readString(branchPath),roles=Files.readString(rolePath);
        String roleService=Files.readString(roleServicePath),entitlements=Files.readString(entitlementPath);

        int platformGate=platform.indexOf("require(actor,\"PLATFORM_TENANT_CREATE\")");
        int tenantCreation=platform.indexOf("var tenant=tenants.create(request)");
        int branchProvision=platform.indexOf("branches.createInitialBranch(tenant.id()");
        int membershipProvision=platform.indexOf("memberships.provisionInitialVendorAdmin(tenant.id(),branch");
        assertThat(platformGate).isGreaterThanOrEqualTo(0);
        assertThat(tenantCreation).isGreaterThan(platformGate);
        assertThat(branchProvision).isGreaterThan(tenantCreation);
        assertThat(membershipProvision).isGreaterThan(branchProvision);
        assertThat(platform.substring(tenantCreation,branchProvision)).doesNotContain("request.tenantId()");
        assertThat(platform.substring(branchProvision,membershipProvision)).contains("tenant.id()");

        int membershipGate=membership.indexOf("PlatformAuthorization.require(\"PLATFORM_TENANT_CREATE\",null)");
        int roleLock=membership.indexOf("StandardRoleTemplates.lockTenant(jdbc,tenantId)");
        int roleInitialize=membership.indexOf("StandardRoleTemplates.initialize(jdbc,tenantId,actor)");
        assertThat(membershipGate).isGreaterThanOrEqualTo(0);
        assertThat(roleLock).isGreaterThan(membershipGate);
        assertThat(roleInitialize).isGreaterThan(roleLock);
        assertThat(membership).contains("effectiveVendorPermissions(m.username(),m.tenant())")
            .contains("entitlements.permissions(m.tenant(),effective)")
            .contains("WHERE a.tenant_id=? AND a.username=?");

        assertThat(branch).contains("authorization.current()")
            .contains("actor.requirePermission(\"PLATFORM_TENANT_CREATE\")")
            .contains("new BranchConfiguration(id,tenantId,name)")
            .contains("tenantId,id,id,name+\" POS stock room\"");
        assertThat(branch.indexOf("actor.requirePermission(\"PLATFORM_TENANT_CREATE\")"))
            .isLessThan(branch.indexOf("db.update(\"INSERT INTO pos_stock_locations"));

        assertThat(roles).contains("static void lockTenant(JdbcTemplate db, long tenant)")
            .contains("static long initialize(JdbcTemplate db, long tenant, String actor)")
            .contains("static long insert(JdbcTemplate db, long tenant, String actor");
        assertThat(roles).doesNotContain("public static void lockTenant","public static long initialize","public static long insert");
        assertThat(roleService).contains("auth.current()")
            .contains("StandardRoleTemplates.lockTenant(db,actor.tenantId())")
            .contains("StandardRoleTemplates.insert(db,actor.tenantId(),actor.username()");

        assertThat(entitlements).contains("public List<String> permissions(long tenant,List<String> grants)")
            .contains("WHERE s.tenant_id=?");
        assertThat(entitlements).doesNotContain("request.getTenantId()","body.getTenantId()","filter.getTenantId()");

        try(var files=Files.walk(sourceRoot())){
            List<Path> branchCallers=files.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".java"))
                .filter(p->{try{return Pattern.compile("\\.createInitialBranch\\s*\\(").matcher(Files.readString(p)).find();}catch(Exception e){throw new RuntimeException(e);}})
                .toList();
            assertThat(branchCallers).containsExactly(platformPath);
        }
        try(var files=Files.walk(sourceRoot())){
            List<Path> membershipCallers=files.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".java"))
                .filter(p->{try{return Pattern.compile("\\.provisionInitialVendorAdmin\\s*\\(").matcher(Files.readString(p)).find();}catch(Exception e){throw new RuntimeException(e);}})
                .toList();
            assertThat(membershipCallers).containsExactly(platformPath);
        }
    }

    @Test
    void jdbcReceiverTypeDiscoveryIncludesRenamedTemplateFieldsAndParameters() {
        assertThat(jdbcReceiverNames("private final JdbcTemplate queryClient;")).contains("queryClient");
        assertThat(jdbcReceiverNames("void run(NamedParameterJdbcTemplate tenantSql){}")).contains("tenantSql");
        assertThat(jdbcReceiverNames("JdbcOperations operations;")).contains("operations");
        assertThat(jdbcReceiverNames("String queryClient;")).doesNotContain("queryClient");
    }

    @Test
    void sqlAstNegativeFixturesRejectDynamicAndUnscopedStatements() {
        assertThat(tenantPredicate("SELECT * FROM vendor_roles WHERE id=?")).isFalse();
        assertThat(tenantPredicate("SELECT * FROM vendor_roles WHERE tenant_id=? AND id=?")).isTrue();
        assertThat(tenantInsert("INSERT INTO vendor_roles(id,role_key) VALUES(?,?)")).isFalse();
        assertThat(tenantInsert("INSERT INTO vendor_roles(tenant_id,role_key) VALUES(?,?)")).isTrue();
        assertThat(hasTrustedTenantArgument("request.getTenantId()")).isFalse();
        assertThat(clientOrigin("tenantAlias",Set.of("tenantAlias"))).isTrue();
        assertThat(clientOrigin("trustedScope.tenant()",Set.of())).isFalse();
        assertThat(tenantPlaceholderIndex("SELECT * FROM vendor_roles WHERE id=? AND tenant_id=?")).isEqualTo(1);
        assertThat(tenantPlaceholderIndex("INSERT INTO vendor_roles(id,tenant_id,role_key) VALUES(?,?,?)")).isEqualTo(1);
        String trustedSql = "SELECT * FROM rows WHERE tenant_id=? AND id=?";
        int trustedIndex = tenantPlaceholderIndex(trustedSql);
        assertThat(List.of("trustedScope.tenant()", "resourceId").get(trustedIndex)).isEqualTo("trustedScope.tenant()");
        String forgedSql = "SELECT * FROM rows WHERE id=? AND tenant_id=?";
        int forgedIndex = tenantPlaceholderIndex(forgedSql);
        String forgedTenantArgument = List.of("resourceId", "request.tenantId()").get(forgedIndex);
        assertThat(forgedTenantArgument).isEqualTo("request.tenantId()");
        assertThat(clientOrigin(forgedTenantArgument, Set.of())).isTrue();
        assertThat(hasTrustedTenantArgument(forgedTenantArgument)).isFalse();
        String forgedAliasSql = "SELECT * FROM rows WHERE tenant_id=? AND id=?";
        int forgedAliasIndex = tenantPlaceholderIndex(forgedAliasSql);
        String forgedAliasArgument = List.of("tenantAlias", "resourceId").get(forgedAliasIndex);
        assertThat(clientOrigin(forgedAliasArgument, Set.of("tenantAlias"))).isTrue();
        assertThat(hasTrustedTenantArgument(forgedAliasArgument)).isFalse();
        assertThat(hasTrustedTenantArgument("authorization.current().tenantId()")).isTrue();
        assertThat(hasTrustedTenantArgument("tenant")).isFalse();
        assertThat(hasTrustedTenantArgument("tenantId")).isFalse();
        assertThat(hasTrustedTenantArgument("request.tenantId()")).isFalse();
    }
}
