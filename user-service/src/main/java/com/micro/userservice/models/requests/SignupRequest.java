package com.micro.userservice.models.requests;


import com.micro.userservice.models.Status;
import lombok.Data;

@Data
public class SignupRequest {
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String password;
}
