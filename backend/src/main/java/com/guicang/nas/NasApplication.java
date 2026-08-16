package com.guicang.nas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** GuiCang（归藏）家庭 NAS 管理系统后端入口。 */
@SpringBootApplication
@EnableScheduling
public class NasApplication {

  public static void main(String[] args) {
    SpringApplication.run(NasApplication.class, args);
  }
}
