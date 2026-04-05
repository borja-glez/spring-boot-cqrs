package com.borjaglez.cqrs.example.multiservice.order.controller;

import jakarta.validation.ConstraintViolationException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<String> handleValidation(ConstraintViolationException ex) {
    return ResponseEntity.badRequest().body(ex.getMessage());
  }
}
