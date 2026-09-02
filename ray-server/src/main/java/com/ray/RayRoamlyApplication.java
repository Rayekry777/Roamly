package com.ray;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.ray.mapper")
@SpringBootApplication
public class RayRoamlyApplication {

    public static void main(String[] args) {
        SpringApplication.run(RayRoamlyApplication.class, args);
    }
}
