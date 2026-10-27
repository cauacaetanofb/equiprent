package com.equiprent.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class ApplicationSecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("pbkdf2@SpringSecurity_v5_8", Map.of(
                "pbkdf2@SpringSecurity_v5_8", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }

    @Bean
    Clock businessClock() {
        return Clock.system(ZoneId.of("America/Sao_Paulo"));
    }
}
