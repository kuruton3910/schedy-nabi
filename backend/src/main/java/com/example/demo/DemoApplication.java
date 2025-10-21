package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean; // importを追加
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // importを追加
import org.springframework.security.crypto.password.PasswordEncoder; // importを追加

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    // ↓↓↓ この部分をクラスの最後などに追加する ↓↓↓
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}