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
    private static boolean hasDerivedScopeParameters(MethodTree method){
        String name=method.getName().toString();
        int by=name.indexOf("By");
        if(by<0)return false;
        String predicate=name.substring(by+2);
        int order=predicate.indexOf("OrderBy");
        if(order>=0)predicate=predicate.substring(0,order);
        Set<String> parameters=new HashSet<>();
        for(VariableTree parameter:method.getParameters())parameters.addAll(parameterNames(parameter));
        String[] parts=predicate.split("And(?=[A-Z])|Or(?=[A-Z])");
        boolean scoped=false;
        for(String part:parts){
            String property=part.replaceFirst("(IsNotNull|NotNull|IsNull|NotNull|IsTrue|True|IsFalse|False|IgnoreCase|StartingWith|EndingWith|Containing|Contains|Like|NotLike|GreaterThanEqual|GreaterThan|LessThanEqual|LessThan|Between|In|NotIn|IsNot|Not|Is)$","");
            if(property.isEmpty())continue;
            if(property.startsWith("Id")&&property.length()>2)property=property.substring(2);
            String normalized=Character.toLowerCase(property.charAt(0))+property.substring(1);
            if(!scopeParameterName(normalized))continue;
            scoped=true;
            if(!derivedParameterMatches(normalized,parameters))return false;
        }
        return scoped;
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
                            String sql=queryValue(annotation);
                            assertThat(sql).as("@Query SQL must be statically declared for %s",operation).isNotNull();
                            boolean scoped=queryScoped(sql);
                            if(!scoped){
                                assertThat(globals).as("unscoped @Query requires a reviewed global/resource exception: %s",operation).containsKey(operation);
                            }else{
                                assertThat(hasBoundScopeParameter(sql,method))
                                    .as("scoped @Query must bind the exact named/positional tenant/customer/owner parameter: %s",operation).isTrue();
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
        boolean scoped=false;
        for(String part:predicate.split("And(?=[A-Z])|Or(?=[A-Z])")){
            String property=part.replaceFirst("(IsNotNull|NotNull|IsNull|IsTrue|True|IsFalse|False|IgnoreCase|StartingWith|EndingWith|Containing|Contains|Like|NotLike|GreaterThanEqual|GreaterThan|LessThanEqual|LessThan|Between|In|NotIn|IsNot|Not|Is)$","");
            if(property.isEmpty())continue;
            if(property.startsWith("Id")&&property.length()>2)property=property.substring(2);
            String normalized=Character.toLowerCase(property.charAt(0))+property.substring(1);
            if(!scopeParameterName(normalized))continue;scoped=true;
            if(!derivedParameterMatches(normalized,parameters))return false;
        }
        return scoped;
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
        assertThat(derivedScopeBindingFixture("findByTenantIdOrOwnerId",Set.of("tenant","owner"))).isTrue();
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
}
