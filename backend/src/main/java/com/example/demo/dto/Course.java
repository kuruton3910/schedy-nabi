package com.example.demo.dto;

/**
 * ScrapingServiceが時間割の生データを保持するためのDTO。
 * recordを使用することで、コンストラクタやgetterが自動で定義されます。
 */
public record Course(
    String day,
    String period,
    String name,
    String location
) {}