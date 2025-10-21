package com.example.demo.dto;

public record NextClassCard(
    String courseName, String day, String period, String location,
    String startDateTime, String endDateTime, String untilStart
) {}