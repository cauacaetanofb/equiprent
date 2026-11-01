package com.equiprent.security;

import com.equiprent.user.UserRepository;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository users, CurrentUser currentUser) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/login", "/css/**", "/vendor/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/catalogo", "/catalogo/*", "/catalogo/*/continuar").permitAll()
                        .requestMatchers(HttpMethod.GET, "/cadastro").anonymous()
                        .requestMatchers(HttpMethod.POST, "/cadastro").anonymous()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/equipamentos", "/equipamentos/**").authenticated()
                        .requestMatchers("/reservas", "/reservas/**").hasRole("CLIENTE")
                        .requestMatchers("/conta").authenticated()
                        .anyRequest().denyAll())
                .formLogin(form -> form.loginPage("/login").usernameParameter("email")
                        .successHandler(new CatalogLoginSuccessHandler()).failureUrl("/login?error").permitAll())
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll())
                .addFilterAfter(new ActiveUserFilter(users), AnonymousAuthenticationFilter.class)
                .addFilterAfter(new CompanyAccessFilter(currentUser), ActiveUserFilter.class)
                .build();
    }
}
