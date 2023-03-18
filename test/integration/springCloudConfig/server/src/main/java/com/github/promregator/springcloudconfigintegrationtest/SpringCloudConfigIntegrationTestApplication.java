package com.github.promregator.springcloudconfigintegrationtest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/*
 * For documentation see also https://docs.spring.io/spring-cloud-config/docs/current/reference/html/
 */
@SpringBootApplication
@EnableConfigServer
public class SpringCloudConfigIntegrationTestApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringCloudConfigIntegrationTestApplication.class, args);
	}

}
