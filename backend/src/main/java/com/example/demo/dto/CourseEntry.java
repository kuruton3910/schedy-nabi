package com.example.demo.dto;

public record CourseEntry(
    String id, String day, String period, String name, String location,
    String startTime, String endTime, String source
) {}