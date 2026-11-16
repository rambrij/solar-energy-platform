package com.saumya.pos;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Keep server-generated JDBC timestamps and JPA's UTC timestamp mapping on one clock. */
@Configuration
public class DatabaseConfiguration {
    @Bean
    static BeanPostProcessor utcDatabaseSessions(){
        return new BeanPostProcessor(){
            @Override public Object postProcessBeforeInitialization(Object bean,String name){
                if(bean instanceof HikariDataSource pool && pool.getJdbcUrl()!=null){
                    if(pool.getJdbcUrl().startsWith("jdbc:mysql:"))pool.setConnectionInitSql("SET time_zone = '+00:00'");
                    else if(pool.getJdbcUrl().startsWith("jdbc:h2:"))pool.setConnectionInitSql("SET TIME ZONE 'UTC'");
                }
                return bean;
            }
        };
    }
}
