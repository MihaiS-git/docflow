package com.brutecx.docflow_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DocflowRestApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(DocflowRestApiApplication.class, args);
	}

}
