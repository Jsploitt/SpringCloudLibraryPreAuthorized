package com.micro.userservice.service;

import com.micro.userservice.models.User;
import com.micro.userservice.repository.UserInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
public class UserService implements UserDetailsService {

    private final UserInfoRepository userRepository;
    private final PasswordEncoder encoder;

    @Autowired
    public UserService(UserInfoRepository repository, PasswordEncoder encoder) {
        this.userRepository = repository;
        this.encoder = encoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<Object> userOptional = userRepository.findByUsername(username);
        if (userOptional.isPresent()) {
            return (UserDetails) userOptional.get();
        } else {
            throw new UsernameNotFoundException("User not found");
        }
    }

    // Add any additional methods for registering or managing users
    public String addUser(User user) {
        // Encrypt password before saving
        user.setPassword(encoder.encode(user.getPassword()));
        System.out.println("In add user ");
        System.out.println(userRepository.save(user));

        return "User added successfully!";
    }

    public User getUserById(String id) {
        return (User) userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + id));
    }


    public void updateUser(User user) {
        Optional<Object> existingUserOptional = userRepository.findById(user.getId());
        if (existingUserOptional.isPresent()) {
            User existingUser = (User) existingUserOptional.get();
            // Update fields as necessary
            existingUser.setUsername(user.getUsername());
            existingUser.setEmail(user.getEmail());
            existingUser.setFirstName(user.getFirstName());
            existingUser.setLastName(user.getLastName());
            existingUser.setRoles(user.getRoles());
            existingUser.setStatus(user.getStatus());
            // Encrypt password if it has changed
            if (!existingUser.getPassword().equals(user.getPassword())) {
                existingUser.setPassword(encoder.encode(user.getPassword()));
            }
            userRepository.save(existingUser);
        } else {
            throw new UsernameNotFoundException("User not found with ID: " + user.getId());
        }
    }

    public String getUserId(User user) {
        Optional<Object> userOptional = userRepository.findByUsername(user.getUsername());
        if (userOptional.isPresent()) {
            User existingUser = (User) userOptional.get();
            return existingUser.getId();
        } else {
            throw new UsernameNotFoundException("User not found with username: " + user.getUsername());
        }
    }
}