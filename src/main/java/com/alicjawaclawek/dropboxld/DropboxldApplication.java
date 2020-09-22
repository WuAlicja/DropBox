package com.alicjawaclawek.dropboxld;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;
//classpath odpowiada za sciezke resource

@SpringBootApplication
public class DropboxldApplication {

    public static void main(String[] args) {
        SpringApplication.run(DropboxldApplication.class, args);
    }

}
