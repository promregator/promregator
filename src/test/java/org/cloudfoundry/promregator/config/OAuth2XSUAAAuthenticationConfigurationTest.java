package org.cloudfoundry.promregator.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

public class OAuth2XSUAAAuthenticationConfigurationTest {

	@Test
	public void testTokenServiceURLBackwardCompatibility() {

		/*
		 * We can still receive an old url containing the path via 'tokenServiceUrl'.
		 * But the corresponding new property 'url' does not contain the path. The url
		 * is expected without the path by token-client.
		 */ 

		OAuth2XSUAAAuthenticationConfiguration subject = new OAuth2XSUAAAuthenticationConfiguration();

		subject.setTokenServiceURL("https://example.org/oauth/token");

		assertThat(subject.getUrl(), equalTo("https://example.org"));
	}

	@Test
	public void testTokenServiceURLBackwardCompatibilityWithPrefixInPath() {

		OAuth2XSUAAAuthenticationConfiguration subject = new OAuth2XSUAAAuthenticationConfiguration();

		subject.setTokenServiceURL("https://example.org/v1/oauth/token");

		assertThat(subject.getUrl(), equalTo("https://example.org/v1"));
	}

	@Test
	public void testTokenServiceURLBackwardCompatibilityWithPort() {

		OAuth2XSUAAAuthenticationConfiguration subject = new OAuth2XSUAAAuthenticationConfiguration();

		subject.setTokenServiceURL("https://example.org:1234/oauth/token");

		assertThat(subject.getUrl(), equalTo("https://example.org:1234"));
	}

	@Test
	public void testScopesWithBlanksAsSeparator() {
		OAuth2XSUAAAuthenticationConfiguration subject = new OAuth2XSUAAAuthenticationConfiguration();
		subject.setScopes("a b c");
		assertThat(subject.getScopes(), containsInAnyOrder("a", "b", "c"));
	}

	@Test
	public void testScopesWithCommaAsSepartor() {
		OAuth2XSUAAAuthenticationConfiguration subject = new OAuth2XSUAAAuthenticationConfiguration();
		subject.setScopes("a, b  , c,d, e");
		assertThat(subject.getScopes(), containsInAnyOrder("a", "b", "c", "d", "e"));
	}

	@Test
	public void testScopesWithMixedSepartors() {
		OAuth2XSUAAAuthenticationConfiguration subject = new OAuth2XSUAAAuthenticationConfiguration();
		subject.setScopes("a b, c");
		assertThat(subject.getScopes(), containsInAnyOrder("a", "b", "c"));
	}

}
