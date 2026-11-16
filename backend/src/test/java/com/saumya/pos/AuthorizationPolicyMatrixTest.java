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
        MethodInvocation invocation=invocation(type,method,"CASH_IN");
        expression=resolveDynamicAuthority(expression,invocation);

        if(expression.contains("permitAll()")){
            assertThat(evaluate(expression,invocation,Set.of(),false))
                .as("explicit public endpoint %s#%s",type.getSimpleName(),method.getName()).isTrue();
            return;
        }

        Set<String> permissions=new LinkedHashSet<>();
        Matcher authorityCalls=Pattern.compile("has(?:Any)?Authority\\(([^)]*)\\)").matcher(expression);
        while(authorityCalls.find()){
            Matcher literal=Pattern.compile("['\\\"]([A-Z][A-Z0-9_]+)['\\\"]").matcher(authorityCalls.group(1));
            while(literal.find())permissions.add(literal.group(1));
        }

        assertThat(evaluate(expression,invocation,Set.of(),false))
            .as("anonymous denial for protected endpoint %s#%s",type.getSimpleName(),method.getName()).isFalse();

        if(permissions.isEmpty()){
            assertThat(evaluate(expression,invocation,Set.of(),true))
                .as("authenticated access policy for %s#%s",type.getSimpleName(),method.getName()).isTrue();
            return;
        }

        assertThat(permissions).as("permission references in %s#%s",type.getSimpleName(),method.getName()).isNotEmpty();
        assertThat(permissions.size()).as("policy complexity for %s#%s",type.getSimpleName(),method.getName()).isLessThanOrEqualTo(12);

        assertThat(evaluate(expression,invocation,permissions,true))
            .as("all declared grants provide a sufficient policy for %s#%s (%s)",type.getSimpleName(),method.getName(),expression).isTrue();

        List<Set<String>> minimalGrantSets=new ArrayList<>();
        int combinations=1<<permissions.size();
        List<String> orderedPermissions=new ArrayList<>(permissions);
        for(int mask=1;mask<combinations;mask++){
            Set<String> candidate=new LinkedHashSet<>();
            for(int bit=0;bit<orderedPermissions.size();bit++)if((mask&(1<<bit))!=0)candidate.add(orderedPermissions.get(bit));
            if(!evaluate(expression,invocation,candidate,true))continue;
            boolean minimal=true;
            for(String permission:candidate){
                Set<String> withoutOne=new LinkedHashSet<>(candidate);
                withoutOne.remove(permission);
                if(evaluate(expression,invocation,withoutOne,true)){minimal=false;break;}
            }
            if(minimal)minimalGrantSets.add(Set.copyOf(candidate));
        }

        assertThat(minimalGrantSets)
            .as("at least one minimal sufficient role grant set for %s#%s (%s)",type.getSimpleName(),method.getName(),expression)
            .isNotEmpty();
        for(String permission:permissions){
            assertThat(minimalGrantSets.stream().anyMatch(grants->grants.contains(permission)))
                .as("permission %s participates in an allowed grant path for %s#%s",permission,type.getSimpleName(),method.getName())
                .isTrue();
        }
    }

    private String resolveDynamicAuthority(String expression,MethodInvocation invocation){
        String dynamic="hasAuthority('POS_' + #request.type().name())";
        if(!expression.contains(dynamic))return expression;
        Object request=null;
        for(Object argument:invocation.getArguments())if(argument!=null&&argument.getClass().isRecord()){
            try{argument.getClass().getDeclaredMethod("type");request=argument;break;}catch(NoSuchMethodException ignored){}
        }
        if(request==null)throw new AssertionError("Dynamic authorization expression requires a request record with type()");
        try{
            Object type=request.getClass().getDeclaredMethod("type").invoke(request);
            if(!(type instanceof Enum<?> movementType))throw new AssertionError("Request type must be an enum for dynamic permission evaluation");
            String permission="POS_"+movementType.name();
            return expression.replace(dynamic,"hasAuthority('"+permission+"')");
        }catch(ReflectiveOperationException error){throw new AssertionError("Cannot resolve dynamic request permission",error);}
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
            return Boolean.TRUE.equals(handler.getExpressionParser().parseExpression(resolveDynamicAuthority(expression,invocation)).getValue(context,Boolean.class));
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
