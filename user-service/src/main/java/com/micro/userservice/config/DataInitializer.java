package com.micro.userservice.config;

import com.micro.userservice.models.Status;
import com.micro.userservice.models.User;
import com.micro.userservice.repository.UserInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserInfoRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User(
                    "admin",
                    "admin@library.com",
                    "Admin",
                    "User",
                    passwordEncoder.encode("Admin123!"),
                    "ROLE_ADMIN",
                    Status.ACTIVE
            );
            userRepository.save(admin);
            System.out.println(">>> Default admin user created: admin / Admin123!");
        }
    }
}
