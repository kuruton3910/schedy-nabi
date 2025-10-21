package com.example.demo.dto;

import java.util.List;

public record SyncResult(
    String username, 
    String syncedAt, 
    List<CourseEntry> timetable,
    List<AssignmentEntry> assignments, 
    NextClassCard nextClass
) {}