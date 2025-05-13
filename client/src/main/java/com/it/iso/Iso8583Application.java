package com.it.iso;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Main application class
 */
@SpringBootApplication
@ComponentScan("com.it.iso")
public class Iso8583Application {
    public static void main(String[] args) {
        SpringApplication.run(Iso8583Application.class, args);
    }
}