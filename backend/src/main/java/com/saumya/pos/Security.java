package com.saumya.pos;

import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration @EnableMethodSecurity
class Security {
    @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService users(JdbcTemplate db) {
        return name -> db.query("SELECT * FROM employees WHERE username=?", (rs,n) -> {
            if(!db.queryForList("SELECT id FROM users WHERE username=?",name).isEmpty())
                throw new UsernameNotFoundException("Ambiguous identity; contact administrator");
            var grants = db.queryForList("SELECT permission FROM employee_permissions WHERE username=?", String.class,name);
            return User.withUsername(name).password(rs.getString("password_hash"))
                .disabled(!rs.getBoolean("enabled")).authorities(grants.toArray(String[]::new)).build();
        }, name).stream().findFirst().orElseGet(() -> db.query(
            "SELECT u.* FROM users u JOIN customer_profiles c ON c.user_id=u.id WHERE u.username=?",
            (r,n)->User.withUsername(r.getString("username")).password(r.getString("password_hash"))
                .disabled(!r.getBoolean("enabled")).authorities("CUSTOMER").build(),name)
            .stream().findFirst().orElseThrow(()->new UsernameNotFoundException("Invalid login")));
    }
    @Bean SecurityFilterChain chain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a.requestMatchers(org.springframework.http.HttpMethod.GET,"/api/marketplace/**").permitAll().anyRequest().authenticated())
            .httpBasic(b -> {}).csrf(c -> c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())).build();
    }
}
