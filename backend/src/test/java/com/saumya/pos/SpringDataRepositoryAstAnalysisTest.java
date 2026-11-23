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

/** AST-based Spring Data repository contract analysis for derived, JPQL and native SQL methods. */
class SpringDataRepositoryAstAnalysisTest {
    private static Path sourceRoot(){Path p=Path.of("backend/src/main/java");return Files.isDirectory(p)?p:Path.of("src/main/java");}
    private static JsonNode policy()throws Exception{
        try(InputStream in=SpringDataRepositoryAstAnalysisTest.class.getResourceAsStream("/tenant-data-access-policy.json")){
            assertThat(in).isNotNull();return new ObjectMapper().readTree(in);
        }
    }
    private static String literal(ExpressionTree tree){
        if(tree instanceof LiteralTree value&&value.getValue() instanceof String s)return s;
        if(tree instanceof BinaryTree b&&b.getKind()==Tree.Kind.PLUS){
            String l=literal(b.getLeftOperand()),r=literal(b.getRightOperand());return l==null||r==null?null:l+r;
        }
        return null;
    }
    private static boolean queryScoped(String sql){
        return java.util.regex.Pattern.compile("(?is)(?:\\b[a-z_][a-z0-9_]*\\.)?(?:tenantId|tenant_id|tenant|customerId|customer_id|ownerId|owner_id|owner|branchId|branch_id|branch|registerId|register_id|register|username|tokenHash|token_hash|hash|family|subscriptionId|invoiceId|planId)\\s*=\\s*(?::[a-z][a-zA-Z0-9_]*|\\?\\d*)").matcher(sql).find();
    }

    private static String maskQueryLiteralsAndComments(String query){
        StringBuilder masked=new StringBuilder(query.length());
        char quote=0;boolean lineComment=false,blockComment=false,escaped=false;
        for(int i=0;i<query.length();i++){
            char ch=query.charAt(i),next=i+1<query.length()?query.charAt(i+1):0;
            if(lineComment){masked.append(ch=='\n'||ch=='\r'?ch:' ');if(ch=='\n'||ch=='\r')lineComment=false;continue;}
            if(blockComment){masked.append(ch=='\n'||ch=='\r'?ch:' ');if(ch=='*'&&next=='/'){masked.append(' ');i++;blockComment=false;}continue;}
            if(quote!=0){
                masked.append(ch=='\n'||ch=='\r'?ch:' ');
                if(escaped){escaped=false;continue;}
                if(ch=='\\'){escaped=true;continue;}
                if(ch==quote){if(next==quote){masked.append(' ');i++;}else quote=0;}
                continue;
            }
            if(ch=='-'&&next=='-'){masked.append("  ");i++;lineComment=true;continue;}
            if(ch=='/'&&next=='*'){masked.append("  ");i++;blockComment=true;continue;}
            if(ch=='\''||ch=='"'||ch==((char)96)){quote=ch;masked.append(' ');continue;}
            masked.append(ch);
        }
        return masked.toString();
    }

    /**
     * A scope predicate must constrain the complete WHERE expression. An OR at the
     * WHERE's top level can bypass a tenant/owner term that appears in only one branch.
     * Parenthesized search alternatives remain valid when the scope predicate is a
     * separate top-level conjunct.
     */
    private static boolean hasTopLevelConjunctiveScope(String query){
        String masked=maskQueryLiteralsAndComments(query);
        java.util.regex.Matcher where=java.util.regex.Pattern.compile("(?i)\\bwhere\\b").matcher(masked);
        if(!where.find())return false;
        int start=where.end(),end=masked.length(),depth=0,cursor=start;
        java.util.regex.Matcher words=java.util.regex.Pattern.compile("[A-Za-z_]+").matcher(masked);
        words.region(start,masked.length());
        boolean topLevelOr=false;
        while(words.find()){
            String word=words.group().toLowerCase(Locale.ROOT);
            int pos=words.start();
            // Track every parenthesis between SQL tokens, including opening brackets
            // separated from the next keyword by whitespace.
            for(int i=cursor;i<pos;i++){
                char ch=masked.charAt(i);
                if(ch=='(')depth++;
                else if(ch==')')depth=Math.max(0,depth-1);
            }
            if(depth==0&&Set.of("group","order","having","limit","offset","fetch","returning","for").contains(word)){
                end=pos;break;
            }
            if(depth==0&&word.equals("or"))topLevelOr=true;
            cursor=words.end();
        }
        if(topLevelOr)return false;
        String whereClause=masked.substring(start,end);
        java.util.regex.Matcher scopes=java.util.regex.Pattern.compile(
            "(?i)(?:\\b[a-z_][a-z0-9_]*\\.)?(?:tenantId|tenant_id|tenant|customerId|customer_id|customer|ownerId|owner_id|owner|branchId|branch_id|branch|registerId|register_id|register|username|tokenHash|token_hash|hash|family|subscriptionId|invoiceId|planId)\\s*=\\s*(?::[a-z][a-zA-Z0-9_]*|\\?\\d*)")
            .matcher(whereClause);
        while(scopes.find()){
            int localDepth=0;
            for(int i=0;i<scopes.start();i++){
                char ch=whereClause.charAt(i);
                if(ch=='(')localDepth++;
                else if(ch==')')localDepth=Math.max(0,localDepth-1);
            }
            if(localDepth==0)return true;
        }
        return false;
    }
    private static Set<String> parameterNames(VariableTree parameter){
        Set<String> names=new HashSet<>();
        names.add(parameter.getName().toString());
        for(AnnotationTree annotation:parameter.getModifiers().getAnnotations()){
            if(!annotation.getAnnotationType().toString().endsWith("Param"))continue;
            java.util.regex.Matcher value=java.util.regex.Pattern.compile("['\"]([^'\"]+)['\"]").matcher(annotation.toString());
            if(value.find())names.add(value.group(1));
        }
        return names;
    }
    private static boolean scopeFieldMatchesParameter(String field,Set<String> parameterNames){
        String key=field.replace("_","").toLowerCase(Locale.ROOT);
        return parameterNames.stream().map(name->name.replace("_","").toLowerCase(Locale.ROOT)).anyMatch(name->{
            if(key.contains("tenant"))return name.contains("tenant");
            if(key.contains("customer"))return name.contains("customer");
            if(key.contains("branch"))return name.contains("branch");
            if(key.contains("register"))return name.contains("register");
            if(key.contains("owner"))return name.contains("owner");
            if(key.contains("username"))return name.contains("username")||name.contains("reviewer")||name.contains("cashier")||name.contains("actor");
            if(key.contains("token")||key.equals("hash"))return name.contains("token")||name.equals("hash");
            if(key.contains("family"))return name.contains("family");
            if(key.contains("subscription"))return name.contains("subscription");
            if(key.contains("invoice"))return name.contains("invoice");
            if(key.contains("plan"))return name.contains("plan");
            return false;
        });
    }
    private static boolean namedScopeBindingsMatch(String query,Set<String> availableNames){
        java.util.regex.Matcher bindings=java.util.regex.Pattern.compile(
            "(?i)(?:\\b[a-z_][a-z0-9_]*\\.)?(tenantId|tenant_id|tenant|customerId|customer_id|customer|ownerId|owner_id|owner|branchId|branch_id|branch|registerId|register_id|register|username|tokenHash|token_hash|hash|family|subscriptionId|invoiceId|planId)\\s*=\\s*:([a-z][a-zA-Z0-9_]*)")
            .matcher(query);
        boolean found=false;
        while(bindings.find()){
            found=true;
            String field=bindings.group(1),parameter=bindings.group(2);
            if(!availableNames.contains(parameter)||!scopeFieldMatchesParameter(field,Set.of(parameter)))return false;
        }
        return found;
    }
    private static boolean scopeParameterName(String name){
        return name.matches("(?i).*(tenant|customer|owner|branch|register|username|reviewer|cashier|actor|token|hash|family|subscription|invoice|plan).*");
    }
    private static int positionalParameterIndex(String query,int placeholderOffset){
        int index=0;char quote=0;boolean lineComment=false,blockComment=false;
        for(int i=0;i<placeholderOffset;i++){
            char ch=query.charAt(i),next=i+1<placeholderOffset?query.charAt(i+1):0;
            if(lineComment){if(ch=='\n'||ch=='\r')lineComment=false;continue;}
            if(blockComment){if(ch=='*'&&next=='/'){blockComment=false;i++;}continue;}
            if(quote!=0){if(ch=='\\'&&i+1<placeholderOffset){i++;continue;}if(ch==quote)quote=0;continue;}
            if(ch=='-'&&next=='-'){lineComment=true;i++;continue;}
            if(ch=='/'&&next=='*'){blockComment=true;i++;continue;}
            if(ch==39||ch==34||ch==96){quote=ch;continue;}
            if(ch=='?')index++;
        }
        return index;
    }
    private static boolean hasBoundScopeParameter(String query,MethodTree method){
        Set<String> availableNames=new HashSet<>();
        for(VariableTree parameter:method.getParameters())availableNames.addAll(parameterNames(parameter));
        java.util.regex.Matcher bindings=java.util.regex.Pattern.compile(
            "(?i)(?:\\b[a-z_][a-z0-9_]*\\.)?(tenantId|tenant_id|tenant|customerId|customer_id|customer|ownerId|owner_id|owner|branchId|branch_id|branch|registerId|register_id|register|username|tokenHash|token_hash|hash|family|subscriptionId|invoiceId|planId)\\s*=\\s*(:[a-z][a-zA-Z0-9_]*|\\?(\\d*))")
            .matcher(query);
        boolean found=false;
        int positionalIndex=0;
        while(bindings.find()){
            found=true;
            String field=bindings.group(1);
            String reference=bindings.group(2);
            if(reference.startsWith(":")){
                if(!availableNames.contains(reference.substring(1))||!scopeFieldMatchesParameter(field,Set.of(reference.substring(1))))return false;
            }else{
                String indexText=bindings.group(3);
                int index;
                if(indexText==null||indexText.isEmpty()){
                    index=positionalParameterIndex(query,bindings.start(2));
                    positionalIndex=index+1;
                }else index=Integer.parseInt(indexText)-1;
                if(index<0||index>=method.getParameters().size())return false;
                Set<String> names=parameterNames(method.getParameters().get(index));
                if(!scopeFieldMatchesParameter(field,names))return false;
            }
        }
        return found;
    }
    private static boolean derivedParameterMatches(String property,Set<String> parameters){
        if(parameters.contains(property))return true;
        return switch(property){
            case "tenantId" -> parameters.contains("tenant");
            case "customerId" -> parameters.contains("customer");
            case "branchId" -> parameters.contains("branch");
            case "registerId" -> parameters.contains("register");
            case "draftOwner","ownerId" -> parameters.contains("owner");
            case "tokenHash" -> parameters.contains("hash");
            default -> false;
        };
    }
    private static String derivedScopeKind(String property){
        String value=property.replace("_","").toLowerCase(Locale.ROOT);
        if(value.contains("tenant"))return "tenant";
        if(value.contains("customer"))return "customer";
        if(value.contains("branch"))return "branch";
        if(value.contains("register"))return "register";
        if(value.contains("owner"))return "owner";
        if(value.contains("username"))return "username";
        if(value.contains("token")||value.equals("hash"))return "token";
        if(value.contains("family"))return "family";
        if(value.contains("subscription"))return "subscription";
        if(value.contains("invoice"))return "invoice";
        if(value.contains("plan"))return "plan";
        return null;
    }
    private static boolean hasDerivedScopeParameters(MethodTree method){
        String name=method.getName().toString();
        int by=name.indexOf("By");
        if(by<0)return false;
        String predicate=name.substring(by+2);
        int order=predicate.indexOf("OrderBy");
        if(order>=0)predicate=predicate.substring(0,order);
        Set<String> parameters=new HashSet<>();
        for(VariableTree parameter:method.getParameters())parameters.addAll(parameterNames(parameter));
        String[] branches=predicate.split("Or(?=[A-Z])");
        Set<String> commonScopeKinds=null;
        for(String branch:branches){
            Set<String> branchScopeKinds=new HashSet<>();
            for(String part:branch.split("And(?=[A-Z])")){
                String property=part.replaceFirst("(IsNotNull|NotNull|IsNull|NotNull|IsTrue|True|IsFalse|False|IgnoreCase|StartingWith|EndingWith|Containing|Contains|Like|NotLike|GreaterThanEqual|GreaterThan|LessThanEqual|LessThan|Between|In|NotIn|IsNot|Not|Is)$","");
                if(property.isEmpty())continue;
                if(property.startsWith("Id")&&property.length()>2)property=property.substring(2);
                String normalized=Character.toLowerCase(property.charAt(0))+property.substring(1);
                String kind=derivedScopeKind(normalized);
                if(kind==null)continue;
                branchScopeKinds.add(kind);
                if(!derivedParameterMatches(normalized,parameters))return false;
            }
            if(branchScopeKinds.isEmpty())return false;
            if(commonScopeKinds==null)commonScopeKinds=new HashSet<>(branchScopeKinds);
            else commonScopeKinds.retainAll(branchScopeKinds);
            if(commonScopeKinds.isEmpty())return false;
        }
        return commonScopeKinds!=null&&!commonScopeKinds.isEmpty();
    }
    private static boolean hasScopeParameter(MethodTree method){
        for(VariableTree parameter:method.getParameters()){
            String name=parameter.getName().toString();
            if(name.matches("(?i).*(tenant|customer|username|tokenHash|hash|family|subscriptionId|invoiceId|planId|owner).*"))return true;
            for(AnnotationTree annotation:parameter.getModifiers().getAnnotations()){
                if(annotation.getAnnotationType().toString().endsWith("Param")&&annotation.toString().matches("(?s).*['\"](?:tenant|tenantId|customer|customerId|username|tokenHash|hash|family|subscriptionId|invoiceId|planId)['\"].*"))return true;
            }
        }
        return false;
    }
    private static boolean queryMethod(String name){
        return name.matches("(?:find|findAll|findTop\\d*|exists|count|delete|remove|read|query|lock|locked).*");
    }
    private static String queryValue(AnnotationTree annotation){
        for(ExpressionTree argument:annotation.getArguments()){
            if(argument instanceof AssignmentTree assignment&&assignment.getVariable().toString().equals("value"))
                return literal(assignment.getExpression());
            if(!(argument instanceof AssignmentTree))return literal(argument);
        }
        return null;
    }

    private static Map<String,String> queryValues(AnnotationTree annotation){
        Map<String,String> values=new LinkedHashMap<>();
        for(ExpressionTree argument:annotation.getArguments()){
            if(argument instanceof AssignmentTree assignment){
                String key=assignment.getVariable().toString();
                if(key.equals("value")||key.equals("countQuery")){
                    String value=literal(assignment.getExpression());
                    if(value!=null)values.put(key,value);
                }
            }else{
                String value=literal(argument);
                if(value!=null)values.put("value",value);
            }
        }
        return values;
    }
    private static Map<String,String> entries(JsonNode policy,String key){
        Map<String,String> result=new TreeMap<>();
        policy.path(key).forEach(item->result.put(item.path("operation").asText(),item.path("scope").asText()));
        return result;
    }

    @Test
    void everySpringDataRepositoryQueryAndDerivedMethodHasAnExplicitScopeOrReviewedGlobalKey()throws Exception{
        JsonNode root=policy();
        Map<String,String> globals=entries(root,"globalMethods"),composites=entries(root,"compositeKeyMethods");
        Set<String> observed=new TreeSet<>();
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK compiler is required for repository AST analysis").isNotNull();
        List<Path> javaFiles;
        try(var stream=Files.walk(sourceRoot())){
            javaFiles=stream.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".java")).sorted().toList();
        }
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(null,null,null)){
            JavacTask task=(JavacTask)compiler.getTask(null,manager,null,List.of("-proc:none"),null,manager.getJavaFileObjectsFromPaths(javaFiles));
            for(CompilationUnitTree unit:task.parse()){
                for(Tree declaration:unit.getTypeDecls()){
                    if(!(declaration instanceof ClassTree type)||type.getKind()!=Tree.Kind.INTERFACE||!type.getSimpleName().toString().endsWith("Repository"))continue;
                    String repository=type.getSimpleName().toString();
                    for(Tree member:type.getMembers()){
                        if(!(member instanceof MethodTree method))continue;
                        boolean declaresQuery=method.getModifiers().getAnnotations().stream()
                            .anyMatch(annotation->annotation.getAnnotationType().toString().endsWith("Query"));
                        if(!declaresQuery&&!queryMethod(method.getName().toString()))continue;
                        String operation=repository+"#"+method.getName();
                        boolean queryAnnotation=false;
                        for(AnnotationTree annotation:method.getModifiers().getAnnotations()){
                            if(!annotation.getAnnotationType().toString().endsWith("Query"))continue;
                            queryAnnotation=true;observed.add(operation);
                            Map<String,String> queries=queryValues(annotation);
                            assertThat(queries).as("@Query and declared countQuery SQL must be statically visible for %s",operation).containsKey("value");
                            for(var query:queries.entrySet()){
                                String sql=query.getValue();
                                boolean scoped=queryScoped(sql);
                                if(!scoped){
                                    assertThat(globals).as("unscoped @Query %s requires a reviewed global/resource exception: %s",query.getKey(),operation).containsKey(operation);
                                }else{
                                    assertThat(hasTopLevelConjunctiveScope(sql))
                                        .as("tenant/customer/owner scope must constrain the complete @Query %s for %s; top-level OR is unsafe",query.getKey(),operation).isTrue();
                                    assertThat(hasBoundScopeParameter(sql,method))
                                        .as("scoped @Query %s must bind the exact named/positional tenant/customer/owner parameter: %s",query.getKey(),operation).isTrue();
                                }
                            }
                        }
                        if(queryAnnotation)continue;
                        if(method.getName().toString().matches("(?i).*(Tenant|TenantId|Customer|CustomerId|Username|TokenHash|Hash|Family|SubscriptionId|InvoiceId|PlanId|Owner).*")){
                            assertThat(hasDerivedScopeParameters(method))
                                .as("every tenant/customer/owner predicate in a derived repository method must bind its matching parameter: %s",operation).isTrue();
                            continue;
                        }
                        if(composites.containsKey(operation)||globals.containsKey(operation))continue;
                        fail("Derived repository method lacks tenant/owner scope or explicit global exception: "+operation);
                    }
                }
            }
        }
        assertThat(observed).as("all declared @Query operations must be seen by the AST analyzer").isNotEmpty();
    }

    private record ScopedRepositoryMethod(String repository,String name,int parameterCount,Set<Integer> tenantIndexes) {}
    private static Set<Integer> tenantParameterIndices(MethodTree method,String query){
        Set<Integer> indices=new TreeSet<>();
        if(query!=null){
            var bindings=java.util.regex.Pattern.compile("(?i)(?:\\b[a-z_][a-z0-9_]*\\.)?(tenantId|tenant_id|tenant)\\s*=\\s*(:[a-z][a-zA-Z0-9_]*|\\?(\\d*))").matcher(query);
            while(bindings.find()){
                String field=bindings.group(1),reference=bindings.group(2);
                if(reference.startsWith(":")){
                    String name=reference.substring(1);
                    for(int i=0;i<method.getParameters().size();i++)
                        if(scopeFieldMatchesParameter(field,parameterNames(method.getParameters().get(i)))&&parameterNames(method.getParameters().get(i)).contains(name))
                            indices.add(i);
                }else{
                    String number=bindings.group(3);
                    int index=number==null||number.isEmpty()?positionalParameterIndex(query,bindings.start(2)):Integer.parseInt(number)-1;
                    if(index>=0&&index<method.getParameters().size()
                        &&scopeFieldMatchesParameter(field,parameterNames(method.getParameters().get(index))))indices.add(index);
                }
            }
            return indices;
        }
        String name=method.getName().toString();
        if(!name.matches("(?i).*(Tenant|TenantId).*"))return indices;
        for(int i=0;i<method.getParameters().size();i++){
            Set<String> names=parameterNames(method.getParameters().get(i));
            if(names.stream().anyMatch(value->value.toLowerCase(Locale.ROOT).contains("tenant")))indices.add(i);
        }
        return indices;
    }
    private static boolean trustedTenantExpression(String expression,Set<String> trustedVariables){
        String value=expression.trim();
        if(value.matches("(?i)input\\s*\\.\\s*tenantId\\s*\\(\\s*\\)")
            &&trustedVariables.contains("__platformAuthorizedTenantTarget"))return true;
        if(java.util.regex.Pattern.compile("(?i)(?:\\b(?:request|payload|body|filter|criteria|dto|command|input|form)\\s*\\.|\\bgetTenantId\\s*\\()").matcher(value).find())return false;
        if(java.util.regex.Pattern.compile("(?i)(?:\\b(?:auth|authorization)\\s*\\.\\s*current\\s*\\(\\s*\\)\\s*\\.\\s*tenantId\\s*\\(\\s*\\)|"
            +"\\b(?:ctx|context|c|actor|reviewer|scope|s|event|m|order|membership|cart|current|archived|vendor|tenant|challenge|refund)\\s*\\.\\s*(?:tenant|tenantId|id)\\s*(?:\\(\\s*\\))?|"
            +"\\bactiveVendor\\s*\\([^)]*\\)\\s*\\.\\s*id\\s*\\(\\s*\\))").matcher(value).find())return true;
        return value.matches("[A-Za-z_$][\\w$]*")&&trustedVariables.contains(value);
    }

    private record ServiceMethodRef(String className,String simpleName,MethodTree method,Map<String,String> fieldTypes,
                                    Set<String> tenantGuardMethods) {}
    private static String simpleTypeName(String type){
        if(type==null)return null;
        String value=type.replaceAll("<.*>","").trim();
        int dot=value.lastIndexOf('.');
        return dot<0?value:value.substring(dot+1);
    }
    /** Propagate trust only through explicit trusted expressions and methods whose tenant guards are verified. */
    private static Set<String> trustedServiceParameterClosure(List<CompilationUnitTree> units,Set<String> initial){
        Map<String,List<ServiceMethodRef>> methodsByClass=new HashMap<>();
        for(CompilationUnitTree unit:units)for(Tree declaration:unit.getTypeDecls()){
            if(!(declaration instanceof ClassTree type))continue;
            String packageName=unit.getPackageName()==null?"":unit.getPackageName().toString()+".";
            String className=packageName+type.getSimpleName();
            Map<String,String> fieldTypes=new HashMap<>();
            for(Tree member:type.getMembers())if(member instanceof VariableTree field)
                fieldTypes.put(field.getName().toString(),simpleTypeName(field.getType()==null?null:field.getType().toString()));
            Set<String> guards=new HashSet<>();
            for(Tree member:type.getMembers())if(member instanceof MethodTree helper&&helper.getBody()!=null){
                String body=helper.getBody().toString();
                if(body.contains("requireTenant(")
                    &&(body.contains("authorization.current()")||body.contains("auth.current()")
                        ||body.contains("context.requireTenant(")||body.contains("actor.requireTenant(")))
                    guards.add(helper.getName().toString());
            }
            for(Tree member:type.getMembers())if(member instanceof MethodTree method)
                methodsByClass.computeIfAbsent(type.getSimpleName().toString(),key->new ArrayList<>())
                    .add(new ServiceMethodRef(className,type.getSimpleName().toString(),method,fieldTypes,guards));
        }
        Set<String> trusted=new HashSet<>(initial);
        final boolean[] changed={false};
        do{
            changed[0]=false;
            for(List<ServiceMethodRef> classMethods:methodsByClass.values())for(ServiceMethodRef ref:classMethods){
                MethodTree method=ref.method();
                if(method.getBody()==null)continue;
                Set<String> variables=new HashSet<>();
                for(VariableTree parameter:method.getParameters())
                    if(trusted.contains(ref.className()+"#"+method.getName()+"#"+parameter.getName()))
                        variables.add(parameter.getName().toString());
                Map<String,String> localTypes=new HashMap<>();
                new TreeScanner<Void,Void>(){
                    @Override public Void visitVariable(VariableTree node,Void unused){
                        if(node.getType()!=null)localTypes.put(node.getName().toString(),simpleTypeName(node.getType().toString()));
                        if(node.getInitializer()!=null&&trustedTenantExpression(node.getInitializer().toString(),variables))
                            variables.add(node.getName().toString());
                        return super.visitVariable(node,unused);
                    }
                    @Override public Void visitAssignment(AssignmentTree node,Void unused){
                        if(node.getVariable() instanceof IdentifierTree id
                            &&trustedTenantExpression(node.getExpression().toString(),variables))variables.add(id.getName().toString());
                        return super.visitAssignment(node,unused);
                    }
                    @Override public Void visitMethodInvocation(MethodInvocationTree node,Void unused){
                        String invoked=node.getMethodSelect() instanceof MemberSelectTree select
                            ?select.getIdentifier().toString():node.getMethodSelect().toString();
                        if((invoked.equals("requireTenant")||ref.tenantGuardMethods().contains(invoked))
                            &&!node.getArguments().isEmpty()
                            &&node.getArguments().get(0) instanceof IdentifierTree id)variables.add(id.getName().toString());
                        if(invoked.equals("requirePlatform")||invoked.equals("requireRead")){
                            variables.add("__platformAuthorizedTenantTarget");
                            for(VariableTree parameter:method.getParameters())
                                if(parameter.getName().toString().matches("(?i).*tenant.*"))variables.add(parameter.getName().toString());
                        }
                        String targetClass=ref.simpleName();
                        if(node.getMethodSelect() instanceof MemberSelectTree select){
                            String receiver=simpleReceiver(select.getExpression().toString());
                            if(!receiver.equals("this")&&!receiver.equals(ref.simpleName())){
                                String type=localTypes.getOrDefault(receiver,ref.fieldTypes().get(receiver));
                                if(type!=null)targetClass=type;
                            }
                        }
                        for(ServiceMethodRef target:methodsByClass.getOrDefault(targetClass,List.of())){
                            if(!target.method().getName().contentEquals(invoked)
                                ||target.method().getParameters().size()!=node.getArguments().size())continue;
                            for(int i=0;i<node.getArguments().size();i++){
                                String parameter=target.method().getParameters().get(i).getName().toString();
                                if(parameter.matches("(?i).*tenant.*")
                                    &&trustedTenantExpression(node.getArguments().get(i).toString(),variables))
                                    if(trusted.add(target.className()+"#"+target.method().getName()+"#"+parameter))changed[0]=true;
                            }
                        }
                        return super.visitMethodInvocation(node,unused);
                    }
                }.scan(method.getBody(),null);
            }
        }while(changed[0]);
        return trusted;
    }
    private static String repositoryType(VariableTree variable){
        if(variable.getType()==null)return null;
        var matcher=java.util.regex.Pattern.compile("([A-Za-z_$][\\w$]*Repository)").matcher(variable.getType().toString());
        return matcher.find()?matcher.group(1):null;
    }
    private static String simpleReceiver(String receiver){
        String value=receiver.replaceFirst("^this\\.","");
        int dot=value.lastIndexOf('.');
        return dot<0?value:value.substring(dot+1);
    }

    @Test
    void everyTenantScopedSpringDataCallsiteUsesTrustedTenantProvenance() throws Exception {
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();
        List<Path> javaFiles;
        try(var stream=Files.walk(sourceRoot())){
            javaFiles=stream.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".java")).sorted().toList();
        }
        Map<String,List<ScopedRepositoryMethod>> repositoryMethods=new HashMap<>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(null,null,null)){
            JavacTask task=(JavacTask)compiler.getTask(null,manager,null,List.of("-proc:none"),null,manager.getJavaFileObjectsFromPaths(javaFiles));
            List<CompilationUnitTree> units=new ArrayList<>();
            task.parse().forEach(units::add);
            Trees trees=Trees.instance(task);
            for(CompilationUnitTree unit:units)for(Tree declaration:unit.getTypeDecls()){
                if(!(declaration instanceof ClassTree type)||type.getKind()!=Tree.Kind.INTERFACE||!type.getSimpleName().toString().endsWith("Repository"))continue;
                String repository=type.getSimpleName().toString();
                for(Tree member:type.getMembers())if(member instanceof MethodTree method){
                    String query=null;
                    for(AnnotationTree annotation:method.getModifiers().getAnnotations())
                        if(annotation.getAnnotationType().toString().endsWith("Query"))query=queryValue(annotation);
                    Set<Integer> tenantIndexes=tenantParameterIndices(method,query);
                    if(!tenantIndexes.isEmpty())
                        repositoryMethods.computeIfAbsent(repository,key->new ArrayList<>())
                            .add(new ScopedRepositoryMethod(repository,method.getName().toString(),method.getParameters().size(),tenantIndexes));
                }
            }
            JsonNode policy=policy();
            Set<String> trustedParameters=new HashSet<>();
            // Trust only method parameters explicitly documented as server-derived in the
            // tenant data-access policy. Merge the source inventory, service-level entries,
            // and individually reviewed helper flows; do not infer trust from parameter names.
            policy.path("trustedTenantParameterSources").forEach(source->{
                String sourceClass=source.path("source").asText();
                JsonNode declared=source.path("trustedTenantParameters");
                declared.fields().forEachRemaining(method->method.getValue().forEach(parameter->
                    trustedParameters.add(sourceClass+"#"+method.getKey()+"#"+parameter.asText())));
            });
            policy.path("trustedServiceTenantParameters").fields().forEachRemaining(source->{
                String sourceClass=source.getKey();
                source.getValue().path("trustedTenantParameters").fields().forEachRemaining(method->
                    method.getValue().forEach(parameter->trustedParameters.add(sourceClass+"#"+method.getKey()+"#"+parameter.asText())));
            });
            policy.path("trustedTenantParameterFlows").forEach(flow->{
                String callee=flow.path("callee").asText(),parameter=flow.path("parameter").asText();
                trustedParameters.add(callee+"#"+parameter);
            });
            trustedParameters.addAll(trustedServiceParameterClosure(units,trustedParameters));
            List<String> untrusted=new ArrayList<>();
            List<String> observedCallsites=new ArrayList<>();
            for(CompilationUnitTree unit:units)for(Tree declaration:unit.getTypeDecls()){
                if(!(declaration instanceof ClassTree type)||type.getKind()==Tree.Kind.INTERFACE&&type.getSimpleName().toString().endsWith("Repository"))continue;
                String packageName=unit.getPackageName()==null?"":unit.getPackageName().toString()+".";
                String className=packageName+type.getSimpleName();
                Map<String,String> repositoryVariables=new HashMap<>();
                new TreeScanner<Void,Void>(){
                    @Override public Void visitVariable(VariableTree node,Void unused){
                        String repoType=repositoryType(node);
                        if(repoType!=null)repositoryVariables.put(node.getName().toString(),repoType);
                        return super.visitVariable(node,unused);
                    }
                }.scan(type,null);
                Set<String> trustedTenantGuardMethods=new HashSet<>();
                for(Tree member:type.getMembers())if(member instanceof MethodTree helper&&helper.getBody()!=null){
                    String body=helper.getBody().toString();
                    if(body.contains("requireTenant(")
                        &&(body.contains("authorization.current()")||body.contains("auth.current()")
                            ||body.contains("context.requireTenant(")||body.contains("actor.requireTenant(")))
                        trustedTenantGuardMethods.add(helper.getName().toString());
                }
                new TreeScanner<Void,Void>(){
                    private String methodName;
                    private Set<String> trustedVariables=new HashSet<>();
                    private List<? extends VariableTree> currentMethodParameters=List.of();
                    @Override public Void visitMethod(MethodTree node,Void unused){
                        String previousMethod=methodName;Set<String> previousVariables=trustedVariables;
                        List<? extends VariableTree> previousParameters=currentMethodParameters;
                        methodName=node.getName().toString();currentMethodParameters=node.getParameters();trustedVariables=new HashSet<>();
                        for(VariableTree parameter:node.getParameters()){
                            String key=className+"#"+methodName+"#"+parameter.getName();
                            if(trustedParameters.contains(key))trustedVariables.add(parameter.getName().toString());
                        }
                        Void result=super.visitMethod(node,unused);
                        methodName=previousMethod;trustedVariables=previousVariables;currentMethodParameters=previousParameters;
                        return result;
                    }
                    @Override public Void visitVariable(VariableTree node,Void unused){
                        if(methodName!=null&&node.getInitializer()!=null
                            &&trustedTenantExpression(node.getInitializer().toString(),trustedVariables))
                            trustedVariables.add(node.getName().toString());
                        return super.visitVariable(node,unused);
                    }
                    @Override public Void visitAssignment(AssignmentTree node,Void unused){
                        if(methodName!=null&&node.getVariable() instanceof IdentifierTree identifier
                            &&trustedTenantExpression(node.getExpression().toString(),trustedVariables))
                            trustedVariables.add(identifier.getName().toString());
                        return super.visitAssignment(node,unused);
                    }
                    @Override public Void visitMethodInvocation(MethodInvocationTree node,Void unused){
                        String invokedName=node.getMethodSelect() instanceof MemberSelectTree selected
                            ?selected.getIdentifier().toString():node.getMethodSelect().toString();
                        if((invokedName.equals("requireTenant")||trustedTenantGuardMethods.contains(invokedName))
                            &&!node.getArguments().isEmpty()
                            &&node.getArguments().get(0) instanceof IdentifierTree tenantArgument)
                            trustedVariables.add(tenantArgument.getName().toString());
                        String invocationText=node.getMethodSelect().toString();
                        if(invokedName.equals("requirePlatform")||invokedName.equals("requireRead")
                            ||invocationText.endsWith("PlatformAuthorization.require")){
                            trustedVariables.add("__platformAuthorizedTenantTarget");
                            for(VariableTree parameter:currentMethodParameters)
                                if(parameter.getName().toString().matches("(?i).*tenant.*"))
                                    trustedVariables.add(parameter.getName().toString());
                        }
                        if(node.getMethodSelect() instanceof MemberSelectTree select){
                            String receiver=simpleReceiver(select.getExpression().toString());
                            String repository=repositoryVariables.getOrDefault(receiver,
                                receiver.endsWith("Repository")?receiver:null);
                            if(repository!=null){
                                for(ScopedRepositoryMethod method:repositoryMethods.getOrDefault(repository,List.of())){
                                    if(!method.name().equals(select.getIdentifier().toString())||method.parameterCount()!=node.getArguments().size())continue;
                                    observedCallsites.add(className+"#"+methodName+" -> "+repository+"#"+method.name());
                                    for(int index:method.tenantIndexes()){
                                        String argument=node.getArguments().get(index).toString();
                                        if(!trustedTenantExpression(argument,trustedVariables)){
                                            long position=trees.getSourcePositions().getStartPosition(unit,node);
                                            long line=unit.getLineMap().getLineNumber(position);
                                            untrusted.add(unit.getSourceFile().getName()+":"+line+" "+repository+"#"+method.name()
                                                +" tenant argument["+index+"]="+argument+" in "+className+"#"+methodName);
                                        }
                                    }
                                }
                            }
                        }
                        return super.visitMethodInvocation(node,unused);
                    }
                }.scan(type,null);
            }
            assertThat(repositoryMethods).as("the repository AST must discover tenant-scoped method signatures").isNotEmpty();
            assertThat(observedCallsites).as("the callsite AST must inspect actual Spring Data repository invocations").isNotEmpty();
            assertThat(untrusted).as("every tenant-scoped Spring Data call must receive a server-derived tenant argument; add an explicit trusted helper flow when it crosses a method boundary")
                .isEmpty();
        }
    }

    @Test
    void tenantRepositoryCallsiteProvenanceFixturesRejectForgedAndUnreviewedArguments(){
        assertThat(trustedTenantExpression("scope.tenant()",Set.of())).isTrue();
        assertThat(trustedTenantExpression("order.tenant()",Set.of())).isTrue();
        assertThat(trustedTenantExpression("request.tenantId()",Set.of())).isFalse();
        assertThat(trustedTenantExpression("payload.tenant()",Set.of())).isFalse();
        assertThat(trustedTenantExpression("tenant",Set.of())).isFalse();
        assertThat(trustedTenantExpression("tenant",Set.of("tenant"))).isTrue();
    }


    @Test
    void queryScopeMustRemainConjunctiveAcrossEveryTopLevelOrBranch(){
        assertThat(hasTopLevelConjunctiveScope(
            "select p from Product p where p.tenantId=:tenant and (lower(p.name) like :pattern or lower(p.sku) like :pattern)")).isTrue();
        assertThat(hasTopLevelConjunctiveScope(
            "select p from Product p where p.tenantId=:tenant or p.status=:status")).isFalse();
        assertThat(hasTopLevelConjunctiveScope(
            "select p from Product p where (p.tenantId=:tenant and p.status=:status) or p.published=true")).isFalse();
        assertThat(hasTopLevelConjunctiveScope(
            "select p from Product p where p.status=:status /* OR tenantId=:tenant */")).isFalse();
        assertThat(hasTopLevelConjunctiveScope(
            "select p from Product p where p.tenantId=:tenant and p.note='OR status'")).isTrue();
    }

    @Test
    void namedQueryScopeCannotBeSatisfiedByAnUnrelatedParameter(){
        assertThat(namedScopeBindingsMatch("select p from Product p where p.tenantId=:tenant",Set.of("tenant"))).isTrue();
        assertThat(namedScopeBindingsMatch("select p from Product p where p.tenantId=:tenant",Set.of("customerId"))).isFalse();
        assertThat(namedScopeBindingsMatch("select p from Product p where p.tenantId=:customer",Set.of("customer"))).isFalse();
        assertThat(namedScopeBindingsMatch("select p from Product p where p.customerId=:tenant",Set.of("tenant"))).isFalse();
        assertThat(namedScopeBindingsMatch("select p from Product p where p.branchId=:tenant",Set.of("tenant"))).isFalse();
        assertThat(namedScopeBindingsMatch("select p from Product p where p.ownerId=:owner",Set.of("owner"))).isTrue();
    }

    private static boolean derivedScopeBindingFixture(String methodName,Set<String> parameters){
        String predicate=methodName.substring(methodName.indexOf("By")+2);
        int order=predicate.indexOf("OrderBy");if(order>=0)predicate=predicate.substring(0,order);
        Set<String> commonScopeKinds=null;
        for(String branch:predicate.split("Or(?=[A-Z])")){
            Set<String> branchScopeKinds=new HashSet<>();
            for(String part:branch.split("And(?=[A-Z])")){
                String property=part.replaceFirst("(IsNotNull|NotNull|IsNull|IsTrue|True|IsFalse|False|IgnoreCase|StartingWith|EndingWith|Containing|Contains|Like|NotLike|GreaterThanEqual|GreaterThan|LessThanEqual|LessThan|Between|In|NotIn|IsNot|Not|Is)$","");
                if(property.isEmpty())continue;
                if(property.startsWith("Id")&&property.length()>2)property=property.substring(2);
                String normalized=Character.toLowerCase(property.charAt(0))+property.substring(1);
                String kind=derivedScopeKind(normalized);
                if(kind==null)continue;
                branchScopeKinds.add(kind);
                if(!derivedParameterMatches(normalized,parameters))return false;
            }
            if(branchScopeKinds.isEmpty())return false;
            if(commonScopeKinds==null)commonScopeKinds=new HashSet<>(branchScopeKinds);
            else commonScopeKinds.retainAll(branchScopeKinds);
            if(commonScopeKinds.isEmpty())return false;
        }
        return commonScopeKinds!=null&&!commonScopeKinds.isEmpty();
    }

    @Test
    void positionalPlaceholderIndexIgnoresQuestionMarksInsideSqlLiteralsAndComments(){
        String query="select p from Product p where p.note='?' and p.status=? /* ? */ and p.tenantId=? and p.branchId=?";
        assertThat(positionalParameterIndex(query,query.indexOf("p.tenantId"))).isEqualTo(1);
        assertThat(positionalParameterIndex(query,query.indexOf("p.branchId"))).isEqualTo(2);
    }

    @Test
    void positionalScopeBindingUsesItsActualSqlPlaceholderIndex(){
        String query="select p from Product p where p.status=? and p.tenantId=? and p.branchId=?";
        assertThat(positionalScopeBindingFixture(query,List.of("status","tenant","branch"))).isTrue();
        assertThat(positionalScopeBindingFixture(query,List.of("status","branch","tenant"))).isFalse();
    }

    private static boolean positionalScopeBindingFixture(String query,List<String> parameters){
        var matcher=java.util.regex.Pattern.compile("(?i)(?:\\b[a-z_][a-z0-9_]*\\.)?(tenantId|tenant_id|customerId|customer_id|ownerId|owner_id|branchId|branch_id|registerId|register_id)\\s*=\\s*\\?").matcher(query);
        boolean found=false;
        while(matcher.find()){
            found=true;
            int index=positionalParameterIndex(query,matcher.start(1));
            if(index>=parameters.size())return false;
            if(!scopeFieldMatchesParameter(matcher.group(1),Set.of(parameters.get(index))))return false;
        }
        return found;
    }

    @Test
    void derivedQueryMustBindEveryTenantPredicateToItsMatchingParameter(){
        assertThat(derivedScopeBindingFixture("findAllByTenantAndOrderOrderByCreatedAscIdAsc",Set.of("tenant","order"))).isTrue();
        assertThat(derivedScopeBindingFixture("findAllByTenantAndOrderOrderByCreatedAscIdAsc",Set.of("order","requestedTenant"))).isFalse();
        assertThat(derivedScopeBindingFixture("findByUsernameIgnoreCase",Set.of("username"))).isTrue();
        assertThat(derivedScopeBindingFixture("existsByTenantIdAndDraftOwner",Set.of("tenant","owner"))).isTrue();
        assertThat(derivedScopeBindingFixture("existsByTenantIdAndDraftOwner",Set.of("tenant","requestOwner"))).isFalse();
        assertThat(derivedScopeBindingFixture("findByTenantIdOrOwnerId",Set.of("tenant","owner"))).isFalse();
        assertThat(derivedScopeBindingFixture("findByTenantIdAndStatusOrTenantIdAndArchived",Set.of("tenant","status","archived"))).isTrue();
        assertThat(derivedScopeBindingFixture("findByTenantIdAndStatusOrStatus",Set.of("tenant","status"))).isFalse();
        assertThat(derivedScopeBindingFixture("findByTenantIdOrOwnerId",Set.of("tenant","customer"))).isFalse();
    }

    @Test
    void negativeFixturesRejectUnscopedDerivedAndNativeQueries(){
        assertThat(queryScoped("select p from Product p where p.id=:id")).isFalse();
        assertThat(queryScoped("select p from Product p where p.tenantId=:tenant and p.id=:id")).isTrue();
        assertThat(queryScoped("select c from Cart c where c.customerId=:customer and c.id=:id")).isTrue();
        assertThat(queryScoped("select * from refunds where id=?1")).isFalse();
        assertThat(queryScoped("delete from refunds where tenant_id=?1 and id=?2")).isTrue();
        assertThat(namedScopeBindingsMatch("select p from Product p where p.tenantId=:tenant",Set.of("tenant"))).isTrue();
        assertThat(namedScopeBindingsMatch("select p from Product p where p.tenantId=:tenant",Set.of("customerId"))).isFalse();
    }

    @Test
    void everyJpaQueryConstructionAndNamedQueryHasStaticTenantScopeOrReviewedGlobalException() throws Exception {
        JsonNode root=policy();
        Map<String,String> globalMethods=entries(root,"globalJpaQueryMethods");
        Map<String,String> globalNamedQueries=entries(root,"globalNamedQueries");
        Set<String> discoveredNamedQueries=new TreeSet<>();
        Set<String> observedGlobalMethods=new TreeSet<>();
        Set<String> observedGlobalNamedQueries=new TreeSet<>();
        Set<String> observedJpaCalls=new TreeSet<>();
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK compiler is required for JPA query AST analysis").isNotNull();
        List<Path> javaFiles;
        try(var stream=Files.walk(sourceRoot())){
            javaFiles=stream.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".java")).sorted().toList();
        }
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(null,null,null)){
            JavacTask task=(JavacTask)compiler.getTask(null,manager,null,List.of("-proc:none"),null,manager.getJavaFileObjectsFromPaths(javaFiles));
            for(CompilationUnitTree unit:task.parse()){
                String packageName=unit.getPackageName()==null?"":unit.getPackageName().toString()+".";
                for(Tree declaration:unit.getTypeDecls()){
                    if(!(declaration instanceof ClassTree type))continue;
                    String className=packageName+type.getSimpleName();
                    new TreePathScanner<Void,Void>(){
                        String methodName="<initializer>";
                        MethodTree currentMethod;
                        @Override public Void visitMethod(MethodTree node,Void unused){
                            String previousName=methodName;MethodTree previousMethod=currentMethod;
                            methodName=node.getName().toString();currentMethod=node;
                            try{return super.visitMethod(node,unused);}
                            finally{methodName=previousName;currentMethod=previousMethod;}
                        }
                        @Override public Void visitAnnotation(AnnotationTree annotation,Void unused){
                            String annotationName=annotation.getAnnotationType().toString();
                            if(annotationName.endsWith("NamedQuery")||annotationName.endsWith("NamedNativeQuery")){
                                String queryName=null,queryText=null;
                                for(ExpressionTree argument:annotation.getArguments()){
                                    if(!(argument instanceof AssignmentTree assignment))continue;
                                    String key=assignment.getVariable().toString();
                                    if(key.equals("name"))queryName=literal(assignment.getExpression());
                                    if(key.equals("query"))queryText=literal(assignment.getExpression());
                                }
                                assertThat(queryName).as("JPA named query name must be a static literal in %s: %s",className,annotation).isNotBlank();
                                assertThat(queryText).as("JPA named query SQL/JPQL must be a static literal in %s#%s: %s",className,methodName,annotation).isNotNull();
                                String key=className+"#"+queryName;
                                discoveredNamedQueries.add(key);
                                if(queryScoped(queryText)){
                                    assertThat(hasTopLevelConjunctiveScope(queryText))
                                        .as("JPA named query %s must keep tenant/customer/owner scope conjunctive",key).isTrue();
                                }else{
                                    assertThat(globalNamedQueries).as("unscoped JPA named query requires an explicit reviewed global exception: %s",key).containsKey(key);
                                    observedGlobalNamedQueries.add(key);
                                }
                            }
                            return super.visitAnnotation(annotation,unused);
                        }
                        @Override public Void visitMethodInvocation(MethodInvocationTree node,Void unused){
                            String invoked=node.getMethodSelect() instanceof MemberSelectTree select
                                ?select.getIdentifier().toString():node.getMethodSelect().toString();
                            if(Set.of("createQuery","createNativeQuery").contains(invoked)){
                                assertThat(node.getArguments()).as("JPA query construction has a query argument: %s",node).isNotEmpty();
                                String query=literal(node.getArguments().getFirst());
                                assertThat(query).as("JPA query construction must use a static literal/constant expression, not runtime SQL concatenation: %s",node).isNotNull();
                                String operation=className+"#"+methodName;
                                observedJpaCalls.add(operation);
                                if(queryScoped(query)){
                                    assertThat(hasTopLevelConjunctiveScope(query))
                                        .as("JPA query %s must keep tenant/customer/owner scope conjunctive",operation).isTrue();
                                    assertThat(currentMethod).as("scoped JPA query must be constructed in a method with explicit bind parameters: %s",operation).isNotNull();
                                    assertThat(hasBoundScopeParameter(query,currentMethod))
                                        .as("scoped JPA query must bind the matching tenant/customer/owner parameter: %s",operation).isTrue();
                                }else{
                                    assertThat(globalMethods).as("unscoped JPA query requires an explicit reviewed global exception: %s",operation).containsKey(operation);
                                    observedGlobalMethods.add(operation);
                                }
                            }
                            if(invoked.equals("createNamedQuery")){
                                assertThat(node.getArguments()).as("JPA named query call has a name argument: %s",node).isNotEmpty();
                                String name=literal(node.getArguments().getFirst());
                                assertThat(name).as("JPA named query name must be a static literal: %s",node).isNotNull();
                                assertThat(discoveredNamedQueries.stream().anyMatch(key->key.endsWith("#"+name)))
                                    .as("JPA named query call must resolve to a declared @NamedQuery/@NamedNativeQuery: %s",name).isTrue();
                            }
                            return super.visitMethodInvocation(node,unused);
                        }
                    }.scan(unit,null);
                }
            }
        }
        assertThat(discoveredNamedQueries).as("all discovered named queries are analyzed").doesNotContainNull();
        assertThat(observedGlobalMethods).containsExactlyInAnyOrderElementsOf(globalMethods.keySet());
        assertThat(observedGlobalNamedQueries).containsExactlyInAnyOrderElementsOf(globalNamedQueries.keySet());
    }

}
