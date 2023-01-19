package org.cloudfoundry.promregator.auth;

import org.cloudfoundry.promregator.lite.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.springconfig.AuthenticatorSpringConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@EnableConfigurationProperties(PromregatorConfiguration.class)
@SpringBootApplication
@Import({AuthenticatorSpringConfiguration.class})
public class AuthenticatorControllerSpringApplication {


}
