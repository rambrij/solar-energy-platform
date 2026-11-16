package com.saumya.pos;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.*;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.expression.EvaluationContext;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.*;

class AuthorizationPolicyMatrixTest {
    private final DefaultMethodSecurityExpressionHandler handler=new DefaultMethodSecurityExpressionHandler();

    @Test void everyMappedEndpointPassesItsPositiveRoleCasesAndRejectsMissingMandatoryGrants() throws Exception {
        var scanner=new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        int endpoints=0;
        for(var bean:scanner.findCandidateComponents("com.saumya.pos")){
            Class<?> type=Class.forName(bean.getBeanClassName());
            for(Method method:type.getDeclaredMethods()){
                if(!AnnotatedElementUtils.hasAnnotation(method,RequestMapping.class))continue;
                endpoints++;
                PreAuthorize methodPolicy=AnnotatedElementUtils.findMergedAnnotation(method,PreAuthorize.class);
                PreAuthorize classPolicy=AnnotatedElementUtils.findMergedAnnotation(type,PreAuthorize.class);
                PreAuthorize policy=methodPolicy!=null?methodPolicy:classPolicy;
                assertThat(policy).as("authorization policy for %s#%s",type.getSimpleName(),method.getName()).isNotNull();
                verifyPolicy(type,method,policy.value());
            }
        }
        assertThat(endpoints).isGreaterThan(50);
    }

    private void verifyPolicy(Class<?> type,Method method,String expression) {
        if(expression.contains("#r.method()")){
            verifyTenderConditional(type,method,expression);
            return;
        }
        MethodInvocation invocation=invocation(type,method,"x");
        Set<String> mandatory=new LinkedHashSet<>();
        int branchIndex=expression.indexOf("?");
        String mandatoryExpression=branchIndex<0?expression:expression.substring(0,branchIndex);
        Matcher mandatoryMatcher=Pattern.compile("hasAuthority\\('([A-Z][A-Z0-9_]+)'\\)").matcher(mandatoryExpression);
        while(mandatoryMatcher.find())mandatory.add(mandatoryMatcher.group(1));
        Matcher simpleOr=Pattern.compile("^\\s*hasAuthority\\('([A-Z][A-Z0-9_]+)'\\)\\s+or\\s+hasAuthority\\('([A-Z][A-Z0-9_]+)'\\)\\s*$").matcher(expression);
        if(simpleOr.matches()){
            mandatory.clear();
            alternatives.add(new LinkedHashSet<>(List.of(simpleOr.group(1),simpleOr.group(2))));
        }
        if(expression.contains("#request.type().name()")){
            for(Object argument:invocation.getArguments()){
                if(argument==null)continue;
                try{
                    Object value=argument.getClass().getMethod("type").invoke(argument);
                    if(value instanceof Enum<?> movement){mandatory.add("POS_"+movement.name());break;}
                }catch(ReflectiveOperationException ignored){ /* not the dynamic-permission request parameter */ }
            }
        }
        List<Set<String>> alternatives=new ArrayList<>();
        Matcher anyMatcher=Pattern.compile("hasAnyAuthority\\(([^)]*)\\)").matcher(expression);
        while(anyMatcher.find()){
            Set<String> choices=new LinkedHashSet<>();
            Matcher choice=Pattern.compile("['\"]([A-Z][A-Z0-9_]+)['\"]").matcher(anyMatcher.group(1));
            while(choice.find())choices.add(choice.group(1));
            assertThat(choices).as("hasAnyAuthority alternatives in %s#%s",type.getSimpleName(),method.getName()).isNotEmpty();
            alternatives.add(choices);
        }
        Set<String> sufficient=new LinkedHashSet<>(mandatory);
        alternatives.forEach(sufficient::addAll);

        if(expression.contains("permitAll()")){
            assertThat(evaluate(expression,invocation,Set.of(),false))
                .as("explicit public endpoint %s#%s",type.getSimpleName(),method.getName()).isTrue();
            return;
        }
        assertThat(evaluate(expression,invocation,sufficient,true))
            .as("sufficient role grants for %s#%s (%s)",type.getSimpleName(),method.getName(),expression).isTrue();
        assertThat(evaluate(expression,invocation,Set.of(),false))
            .as("anonymous denial for protected endpoint %s#%s",type.getSimpleName(),method.getName()).isFalse();
        for(String required:mandatory){
            Set<String> insufficient=new LinkedHashSet<>(sufficient);insufficient.remove(required);
            assertThat(evaluate(expression,invocation,insufficient,true))
                .as("mandatory grant %s is required by %s#%s",required,type.getSimpleName(),method.getName()).isFalse();
        }
        for(Set<String> choices:alternatives){
            for(String choice:choices){
                Set<String> candidate=new LinkedHashSet<>(mandatory);candidate.add(choice);
                for(Set<String> other:alternatives)if(other!=choices)candidate.addAll(other);
                assertThat(evaluate(expression,invocation,candidate,true))
                    .as("alternative grant %s permits %s#%s",choice,type.getSimpleName(),method.getName()).isTrue();
            }
        }
    }

    private boolean evaluate(String expression,MethodInvocation invocation,Set<String> authorities,boolean authenticated){
        Authentication authentication;
        if(authenticated){
            authentication=UsernamePasswordAuthenticationToken.authenticated("role-matrix",null,
                authorities.stream().map(SimpleGrantedAuthority::new).toList());
        }else{
            authentication=new AnonymousAuthenticationToken("role-matrix","anonymous",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        }
        try {
            EvaluationContext context=handler.createEvaluationContext(()->authentication,invocation);
            return Boolean.TRUE.equals(handler.getExpressionParser().parseExpression(expression).getValue(context,Boolean.class));
        } catch(RuntimeException error) {
            throw new AssertionError("Could not evaluate endpoint authorization expression: "+expression,error);
        }
    }

    private void verifyTenderConditional(Class<?> type,Method method,String expression){
        Matcher mandatoryMatcher=Pattern.compile("hasAuthority\\('([A-Z][A-Z0-9_]+)'\\)").matcher(expression.substring(0,expression.indexOf("?")));
        Set<String> mandatory=new LinkedHashSet<>();while(mandatoryMatcher.find())mandatory.add(mandatoryMatcher.group(1));
        Matcher branches=Pattern.compile("hasAuthority\\('([A-Z][A-Z0-9_]+)'\\)").matcher(expression.substring(expression.indexOf("?")+1));
        List<String> branchPermissions=new ArrayList<>();while(branches.find())branchPermissions.add(branches.group(1));
        assertThat(branchPermissions).hasSize(2);
        List<String> methods=List.of("CASH","UPI");
        for(int i=0;i<methods.size();i++){
            String branch=branchPermissions.get(i);Set<String> grants=new LinkedHashSet<>(mandatory);grants.add(branch);
            MethodInvocation invocation=invocation(type,method,methods.get(i));
            assertThat(evaluate(expression,invocation,grants,true))
                .as("%s tender permission grants %s#%s",methods.get(i),type.getSimpleName(),method.getName()).isTrue();
            grants.remove(branch);
            assertThat(evaluate(expression,invocation,grants,true))
                .as("%s tender permission is mandatory for %s#%s",branch,type.getSimpleName(),method.getName()).isFalse();
            for(String required:mandatory){
                Set<String> insufficient=new LinkedHashSet<>(mandatory);insufficient.remove(required);insufficient.add(branch);
                assertThat(evaluate(expression,invocation,insufficient,true))
                    .as("mandatory permission %s is required for %s#%s",required,type.getSimpleName(),method.getName()).isFalse();
            }
        }
        assertThat(evaluate(expression,invocation(type,method,"CASH"),Set.of(),false)).isFalse();
    }

    private MethodInvocation invocation(Class<?> type,Method method,String tenderMethod){
        Object[] args=Arrays.stream(method.getParameterTypes()).map(t->defaultValue(t,tenderMethod)).toArray();
        return new MethodInvocation(){
            @Override public Method getMethod(){return method;}
            @Override public Object[] getArguments(){return args;}
            @Override public Object getThis(){return AuthorizationPolicyMatrixTest.this;}
            @Override public java.lang.reflect.AccessibleObject getStaticPart(){return method;}
            @Override public Object proceed(){return null;}
        };
    }
    private Object defaultValue(Class<?> type,String tenderMethod){
        if(type==String.class)return "x";
        if(type.isEnum())return type.getEnumConstants().length==0?null:type.getEnumConstants()[0];
        if(type==Long.class||type==long.class)return 1L;
        if(type==Integer.class||type==int.class)return 1;
        if(type==Boolean.class||type==boolean.class)return false;
        if(type==java.math.BigDecimal.class)return java.math.BigDecimal.ONE;
        if(List.class.isAssignableFrom(type)||Collection.class.isAssignableFrom(type))return List.of();
        if(type.isArray())return java.lang.reflect.Array.newInstance(type.getComponentType(),0);
        if(type.isRecord()){
            try{
                var components=type.getRecordComponents();
                Class<?>[] parameterTypes=Arrays.stream(components).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new);
                Object[] values=new Object[components.length];
                for(int i=0;i<components.length;i++)
                    values[i]=components[i].getName().equals("method")?tenderMethod:defaultValue(components[i].getType(),tenderMethod);
                var constructor=type.getDeclaredConstructor(parameterTypes);constructor.setAccessible(true);
                return constructor.newInstance(values);
            }catch(Exception e){throw new IllegalStateException("Cannot create policy probe record "+type.getName(),e);}
        }
        return null;
    }
}
