package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.jdbc.DataSourceBuilder;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    private static String clean(String s, String def) {
        if (s == null) return def;
        // trim + 外側の " を削除（Renderの自動引用対策）
        String t = s.trim();
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
            t = t.substring(1, t.length() - 1);
        }
        return t;
    }

    @Bean
    public DataSource dataSource() {
        String host = clean(System.getenv("DATABASE_HOST"), "localhost");
        String port = clean(System.getenv("DATABASE_PORT"), "5432");
        String db   = clean(System.getenv("DATABASE_NAME"), "postgres");
        String user = clean(System.getenv("DATABASE_USERNAME"), "");
        String pass = clean(System.getenv("DATABASE_PASSWORD"), "");
        String ssl  = clean(System.getenv("DATABASE_SSLMODE"), "require"); // default=require

        // 組み立て（sslmodeは必ず設定）
        String url = String.format("jdbc:postgresql://%s:%s/%s?sslmode=%s", host, port, db, ssl);

        return DataSourceBuilder.create()
                .url(url)
                .username(user)
                .password(pass)
                .driverClassName("org.postgresql.Driver")
                .build();
    }
}
