package org.cloudfoundry.promregator;

import org.junit.Assert;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("springBootLoadPropertiesForTesting")
public class SpringBootLoadPropertiesForTestingSpringApplication {
	@Value("${dummy.value:false}")
	private boolean dummyValue;

	@Value("${secret.value:''}")
	private String secretValue;
	
	@Bean
	public Object anything() {
		Assert.assertTrue(dummyValue);
		return new Object();
	}
	
	public void check() {
		Assert.assertTrue(dummyValue);
	}

	public String getSecretValue() {
		return secretValue;
	}
}
