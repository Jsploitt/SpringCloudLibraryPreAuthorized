package com.micro.userservice.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserNameChangedEvent {
    private String id;
    private String firstName;
    private String lastName;

    // Constructors, Getters, Setters
}
