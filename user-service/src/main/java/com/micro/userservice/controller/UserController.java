package com.micro.userservice.controller;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micro.userservice.models.*;
import com.micro.userservice.models.requests.AuthRequest;
import com.micro.userservice.models.requests.ChangeNameReq;
import com.micro.userservice.models.requests.ChangeStatusReq;
import com.micro.userservice.models.requests.SignupRequest;
import com.micro.userservice.service.JwtService;
import com.micro.userservice.service.UserService;


import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {


    private UserService userService;

    private JwtService jwtService;

    private AuthenticationManager authenticationManager;

    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    public UserController(UserService userService, JwtService jwtService, AuthenticationManager authenticationManager, KafkaTemplate<String, String> kafkaTemplate) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.kafkaTemplate = kafkaTemplate;
    }

    @GetMapping("/user/{id}")
    public ResponseEntity<?> getUserById(@PathVariable String id) {
        try {
            User user = userService.getUserById(id);
            return ResponseEntity.ok(user);
        } catch (UsernameNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found with ID: " + id));
        }
    }
    @GetMapping("/status/{id}")
    public ResponseEntity<?> getUserStatus(@PathVariable String id) {
        try {
            User user = userService.getUserById(id);
            return ResponseEntity.ok(Map.of("status", user.getStatus()));
        } catch (UsernameNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found with ID: " + id));
        }
    }


    @PostMapping("/login")
    public ResponseEntity<?> authenticateAndGetToken(@RequestBody AuthRequest authRequest) {
        User user = (User) userService.loadUserByUsername(authRequest.getUsername());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(authRequest.getUsername(), authRequest.getPassword())
        );
        if (authentication.isAuthenticated()) {
            String id = userService.getUserId(user);
            return ResponseEntity.ok(Map.of("message", jwtService.generateToken(authRequest.getUsername(),user.getAuthorities(),id)));
        } else {
            throw new UsernameNotFoundException("Invalid user request!");
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@RequestBody SignupRequest request) {
        try {
            User user = new User(
                    request.getUsername(),
                    request.getEmail(),
                    request.getFirstName(),
                    request.getLastName(),
                    request.getPassword(),
                    "ROLE_USER",
                    Status.INACTIVE
            );
            String message = userService.addUser(user);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", message));
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", ex.getMessage()));
        }
    }


    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/change-status")
    public ResponseEntity<?> changeStatus(@RequestBody ChangeStatusReq changeStatusReq) {
        try {
            String uuid = changeStatusReq.getId();
            User user = userService.getUserById(uuid);
            user.setStatus(changeStatusReq.getStatus());
            userService.updateUser(user);
            return ResponseEntity.ok(Map.of("message", "User status updated successfully."));
        } catch (UsernameNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found with ID: " + changeStatusReq.getId()));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/change-name")
    public ResponseEntity<?> changeName(@RequestBody ChangeNameReq changeNameReq) {
        try {
            String uuid = changeNameReq.getId();
            User user = userService.getUserById(uuid);
            user.setFirstName(changeNameReq.getFirstName());
            user.setLastName(changeNameReq.getLastName());
            userService.updateUser(user);
            // Send a message to Kafka topic
            UserNameChangedEvent event = new UserNameChangedEvent(
                    changeNameReq.getId(),
                    changeNameReq.getFirstName(),
                    changeNameReq.getLastName()
            );

            ObjectMapper mapper = new ObjectMapper();
            try {
                String json = mapper.writeValueAsString(event);
                kafkaTemplate.send("user-name-changes", json);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize event", e);
            }

            return ResponseEntity.ok(Map.of("message", "User name updated successfully."));
        } catch (UsernameNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found with ID: " + changeNameReq.getId()));
        }
    }

}