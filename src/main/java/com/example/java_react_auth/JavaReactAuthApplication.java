package com.example.java_react_auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.github.cdimascio.dotenv.Dotenv;

// testing
@SpringBootApplication
public class JavaReactAuthApplication {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        dotenv.entries().forEach(entry
                -> System.setProperty(entry.getKey(), entry.getValue())
        );

        SpringApplication.run(JavaReactAuthApplication.class, args);
    }

}
