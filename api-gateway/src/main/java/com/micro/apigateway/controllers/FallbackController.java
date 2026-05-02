package com.micro.apigateway.controllers;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
//general exception handler for all unhandled exceptions
public class FallbackController implements ErrorController {

    @RequestMapping("/**")
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> fallback() {
        return ResponseEntity.status(404).body("The requested resource is not available. Please check the URL.");
    }
}
