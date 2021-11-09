package org.cloudfoundry.promregator.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

public class OAuth2XSUAAAuthenticationConfigurationTest {

	@Test
	public void testTokenServiceURLBackwardCompatibility() {
		OAuth2XSUAAAuthenticationConfiguration subject = new OAuth2XSUAAAuthenticationConfiguration();
		subject.setTokenServiceURL("https://example.org/oauth/token");
		assertThat(subject.getTokenServiceURL(), equalTo("https://example.org/oauth/token"));
	}

	@Test
	public void testTokenServiceURLBackwardCompatibilityWithPrefixInPath() {
		OAuth2XSUAAAuthenticationConfiguration subject = new OAuth2XSUAAAuthenticationConfiguration();
		subject.setTokenServiceURL("https://example.org/v1/oauth/token");
		assertThat(subject.getTokenServiceURL(), equalTo("https://example.org/v1/oauth/token"));
	}

	@Test
	public void testTokenServiceURLBackwardCompatibilityWithPort() {
		OAuth2XSUAAAuthenticationConfiguration subject = new OAuth2XSUAAAuthenticationConfiguration();
		subject.setTokenServiceURL("https://example.org:1234/oauth/token");
		assertThat(subject.getTokenServiceURL(), equalTo("https://example.org:1234/oauth/token"));
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
