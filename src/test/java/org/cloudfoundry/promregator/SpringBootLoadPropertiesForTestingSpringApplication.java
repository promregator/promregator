package org.cloudfoundry.promregator;

import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("springBootLoadPropertiesForEncryptionTesting")
public class SpringBootLoadPropertiesForTestingSpringApplication {
	@Value("${dummy.value:false}")
	private boolean dummyValue;

	@Value("${secret.value:''}")
	private String secretValue;
	
	@Bean
	public Object anything() {
		Assertions.assertTrue(dummyValue);
		return new Object();
	}
	
	public void check() {
		Assertions.assertTrue(dummyValue);
	}

	public String getSecretValue() {
		return secretValue;
	}
}
