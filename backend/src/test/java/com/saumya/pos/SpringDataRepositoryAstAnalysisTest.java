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
        return java.util.regex.Pattern.compile("(?is)(?:\\b[a-z_][a-z0-9_]*\\.)?(?:tenantId|tenant_id|tenant|customerId|customer_id|username|tokenHash|token_hash|hash|family|subscriptionId|invoiceId|planId)\\s*=\\s*(?::[a-z][a-zA-Z0-9_]*|\\?\\d*)").matcher(sql).find();
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
    private static boolean namedScopeBindingsMatch(String query,Set<String> availableNames){
        java.util.regex.Matcher bindings=java.util.regex.Pattern.compile(
            "(?i)(?:\\b[a-z_][a-z0-9_]*\\.)?(?:tenantId|tenant_id|tenant|customerId|customer_id|username|tokenHash|token_hash|hash|family|subscriptionId|invoiceId|planId)\\s*=\\s*:([a-z][a-zA-Z0-9_]*)")
            .matcher(query);
        boolean found=false;
        while(bindings.find()){
            found=true;
            if(!availableNames.contains(bindings.group(1)))return false;
        }
        return found;
    }
    private static boolean hasBoundScopeParameter(String query,MethodTree method){
        Set<String> availableNames=new HashSet<>();
        for(VariableTree parameter:method.getParameters())availableNames.addAll(parameterNames(parameter));
        java.util.regex.Matcher positional=java.util.regex.Pattern.compile(
            "(?i)(?:\\b[a-z_][a-z0-9_]*\\.)?(?:tenantId|tenant_id|tenant|customerId|customer_id|username|tokenHash|token_hash|hash|family|subscriptionId|invoiceId|planId)\\s*=\\s*\\?(\\d*)")
            .matcher(query);
        boolean hasNumbered=false;
        while(positional.find()){
            String indexText=positional.group(1);
            if(indexText.isEmpty())continue;
            hasNumbered=true;
            int index=Integer.parseInt(indexText)-1;
            if(index<0||index>=method.getParameters().size())return false;
            VariableTree parameter=method.getParameters().get(index);
            if(parameterNames(parameter).stream().noneMatch(name->name.matches("(?i).*(tenant|customer|owner|username|token|hash|family|subscription|invoice|plan).*")))return false;
        }
        if(query.contains(":"))return namedScopeBindingsMatch(query,availableNames);
        if(hasNumbered)return true;
        return hasScopeParameter(method);
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
                            assertThat(hasScopeParameter(method))
                                .as("derived repository method scope token must have a corresponding scope parameter: %s",operation).isTrue();
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
