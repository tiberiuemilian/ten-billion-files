package com.example.tenbillionfiles;

import com.example.tenbillionfiles.config.StorageConfigurations;
import com.example.tenbillionfiles.config.SwaggerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        StorageConfigurations.class,
        SwaggerConfig.class
})
public class TenBillionFilesApplication {

    public static void main(String[] args) {
        SpringApplication.run(TenBillionFilesApplication.class, args);
    }

}

