package com.saumya.pos;

import java.lang.reflect.Method;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import static org.assertj.core.api.Assertions.*;

class BackgroundEntryPointInventoryTest {
    @Test void scheduledEventsAsyncConsumersAndWebhookEndpointsMatchTheReviewedInventory() throws Exception {
        var scanner=new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class,true));
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<String> scheduled=new TreeSet<>(),events=new TreeSet<>(),async=new TreeSet<>(),messages=new TreeSet<>(),webhooks=new TreeSet<>(),runners=new TreeSet<>();
        for(var bean:scanner.findCandidateComponents("com.saumya.pos")){
            Class<?> type=Class.forName(bean.getBeanClassName());
            if(ApplicationRunner.class.isAssignableFrom(type)||CommandLineRunner.class.isAssignableFrom(type))runners.add(type.getSimpleName());
            for(Method method:type.getDeclaredMethods()){
                String id=type.getSimpleName()+"#"+method.getName();
                if(AnnotatedElementUtils.hasAnnotation(method,Scheduled.class))scheduled.add(id);
                if(AnnotatedElementUtils.hasAnnotation(method,EventListener.class))events.add(id);
                if(AnnotatedElementUtils.hasAnnotation(method,Async.class))async.add(id);
                for(var annotation:method.getAnnotations()){
                    String name=annotation.annotationType().getSimpleName();
                    if(Set.of("KafkaListener","RabbitListener","JmsListener","MessageListener").contains(name))messages.add(id+":"+name);
                }
                RequestMapping mapping=AnnotatedElementUtils.findMergedAnnotation(method,RequestMapping.class);
                if(mapping!=null){
                    String route=String.join(" ",mapping.path())+" "+String.join(" ",mapping.value());
                    if(type.getSimpleName().toLowerCase(Locale.ROOT).contains("webhook")||route.toLowerCase(Locale.ROOT).contains("webhook")||route.toLowerCase(Locale.ROOT).contains("callback"))webhooks.add(id+":"+route);
                }
            }
        }
        assertThat(scheduled).containsExactly("DraftExpiryJob#scheduledCleanup");
        assertThat(events).containsExactly("CashDrawerProjection#record","CatalogAvailabilityService#changed","DraftSessionProjection#changed");
        assertThat(async).isEmpty();
        assertThat(messages).isEmpty();
        assertThat(webhooks).isEmpty();
        assertThat(runners).isEmpty();
    }
}
