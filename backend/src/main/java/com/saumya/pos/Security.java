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
            var grants = db.queryForList("SELECT permission FROM employee_permissions WHERE username=?", String.class,name);
            return User.withUsername(name).password(rs.getString("password_hash"))
                .disabled(!rs.getBoolean("enabled")).authorities(grants.toArray(String[]::new)).build();
        }, name).stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Invalid login"));
    }
    @Bean SecurityFilterChain chain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
            .httpBasic(b -> {}).csrf(c -> c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())).build();
    }
}
