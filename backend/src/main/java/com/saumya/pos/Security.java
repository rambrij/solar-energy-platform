package com.saumya.pos;

import com.saumya.pos.identity.AuthenticationService;
import com.saumya.pos.identity.JwtService;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration @EnableMethodSecurity
class Security {
    @Bean PasswordEncoder passwords(){return new BCryptPasswordEncoder();}
    @Bean SecurityFilterChain chain(HttpSecurity http,JwtService jwt,AuthenticationService identities,
            com.saumya.pos.shared.ApiProblemWriter problemWriter)throws Exception{
        return http.authorizeHttpRequests(a->a
                .requestMatchers("/actuator/prometheus").hasAuthority("PLATFORM_ANALYTICS_VIEW")
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/v3/api-docs","/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/health","/actuator/info").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/v1/marketplace/**","/api/v1/auth/csrf").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/v1/marketplace/vendors/*/enquiries").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/v1/auth/login","/api/v1/auth/register","/api/v1/auth/refresh","/api/v1/auth/logout","/api/v1/auth/email-verification/verify","/api/v1/auth/email-verification/resend","/api/v1/auth/invitations/accept","/api/v1/auth/platform-mfa/enrollment/start","/api/v1/auth/platform-mfa/enrollment/complete").permitAll()
                .anyRequest().authenticated())
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(o->o.jwt(j->j.decoder(jwt.decoder()).jwtAuthenticationConverter(identities::authenticate)))
            .exceptionHandling(ex->ex
                .authenticationEntryPoint((request,response,failure)->problemWriter.write(request,response,
                    org.springframework.http.HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","Unauthorized","Authentication is required."))
                .accessDeniedHandler((request,response,failure)->problemWriter.write(request,response,
                    org.springframework.http.HttpStatus.FORBIDDEN,"FORBIDDEN","Forbidden","The authenticated actor is not permitted to perform this operation.")))
            .csrf(c->c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .build();
    }
}
