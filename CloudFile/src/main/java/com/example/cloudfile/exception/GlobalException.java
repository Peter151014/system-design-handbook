package com.example.cloudfile.exception;

import com.example.cloudfile.dto.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalException {

    @ExceptionHandler(UserNotFound.class)
    public ResponseEntity<Response> handleUserRequest(UserNotFound ex){
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new Response(ex.getMessage()));
    }

    @ExceptionHandler(UserAlreadyExists.class)
    public ResponseEntity<Response> handleUserAlreadyExists(UserAlreadyExists ex){
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new Response(ex.getMessage()));
    }
}
