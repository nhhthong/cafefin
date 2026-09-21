package com.cafefin.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the cafefin-api Spring Boot application.
 *
 * {@code @SpringBootApplication} is shorthand for three annotations stacked
 * together:
 *   - {@code @Configuration}: this class can declare Spring beans.
 *   - {@code @EnableAutoConfiguration}: let Spring Boot wire up beans based
 *     on what's on the classpath (e.g. finding spring-boot-starter-web means
 *     "configure an embedded Tomcat and Spring MVC automatically").
 *   - {@code @ComponentScan}: scan this package and sub-packages (auth,
 *     account, ledger, ...) for classes annotated {@code @Component},
 *     {@code @Service}, {@code @RestController}, etc., and register them.
 *
 * Running {@code main} starts an embedded web server; there is no separate
 * app-server deployment step.
 */
@SpringBootApplication
public class CafefinApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CafefinApiApplication.class, args);
    }
}
