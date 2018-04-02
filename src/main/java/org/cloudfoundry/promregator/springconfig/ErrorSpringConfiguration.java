package org.cloudfoundry.promregator.springconfig;

import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.ErrorPageRegistrar;
import org.springframework.boot.web.server.ErrorPageRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;

public class ErrorSpringConfiguration {
	@Bean
	public ErrorPageRegistrar errorPageRegistrar() {
		return new CustomErrorPageRegistrar();
	}

	private static class CustomErrorPageRegistrar implements ErrorPageRegistrar {

		// Register your error pages and url paths.
		@Override
		public void registerErrorPages(ErrorPageRegistry registry) {
			registry.addErrorPages(new ErrorPage(HttpStatus.UNAUTHORIZED, "/errors/401.html"));
			registry.addErrorPages(new ErrorPage(HttpStatus.INTERNAL_SERVER_ERROR, "/errors/500.html"));
		}

	}
}
