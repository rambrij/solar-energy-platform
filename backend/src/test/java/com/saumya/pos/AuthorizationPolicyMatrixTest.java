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
        MethodInvocation invocation=invocation(type,method);
        Set<String> mandatory=new LinkedHashSet<>();
        Matcher mandatoryMatcher=Pattern.compile("hasAuthority\\('([A-Z][A-Z0-9_]+)'\\)").matcher(expression);
        while(mandatoryMatcher.find())mandatory.add(mandatoryMatcher.group(1));
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
        EvaluationContext context=handler.createEvaluationContext(()->authentication,invocation);
        return Boolean.TRUE.equals(handler.getExpressionParser().parseExpression(expression).getValue(context,Boolean.class));
    }

    private MethodInvocation invocation(Class<?> type,Method method){
        return new MethodInvocation(){
            @Override public Method getMethod(){return method;}
            @Override public Object[] getArguments(){return new Object[0];}
            @Override public Object getThis(){return AuthorizationPolicyMatrixTest.this;}
            @Override public java.lang.reflect.AccessibleObject getStaticPart(){return method;}
            @Override public Object proceed(){return null;}
        };
    }
}
