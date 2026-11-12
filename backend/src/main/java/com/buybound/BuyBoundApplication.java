package com.buybound;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BuyBoundApplication {
  public static void main(String[] args) {
    SpringApplication.run(BuyBoundApplication.class, args);
  }
}
