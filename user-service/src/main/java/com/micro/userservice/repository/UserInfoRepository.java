package com.micro.userservice.repository;


import com.micro.userservice.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserInfoRepository extends JpaRepository<User, Integer> {

    Optional<Object> findByUsername(String username);
    Optional<Object> findById(String id);

}