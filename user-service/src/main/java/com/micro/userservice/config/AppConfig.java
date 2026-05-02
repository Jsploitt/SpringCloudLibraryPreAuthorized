package com.micro.userservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfig {
    /*
     * Password encoder bean (uses BCrypt hashing)
     * Critical for secure password storage
     */

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public NewTopic userNameChangesTopic() {
        return TopicBuilder.name("user-name-changes")
                .partitions(1)
                .replicas(1)
                .build();
    }




}
