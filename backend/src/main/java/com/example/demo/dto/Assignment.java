package com.example.demo.dto;

public record Assignment(
    String courseName,
    String category,
    String title,
    String deadline,
    String url
) {}