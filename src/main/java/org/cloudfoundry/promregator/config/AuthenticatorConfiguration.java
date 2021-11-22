package org.cloudfoundry.promregator.config;

import org.cloudfoundry.promregator.lite.config.BasicAuthenticationConfiguration;

public class AuthenticatorConfiguration {
	private String type;
	
	private BasicAuthenticationConfiguration basic;

	private OAuth2XSUAABasicAuthenticationConfiguration oauth2xsuaaBasic;

	private OAuth2XSUAACertificateAuthenticationConfiguration oauth2xsuaaCertificate;

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public BasicAuthenticationConfiguration getBasic() {
		return basic;
	}

	public void setBasic(BasicAuthenticationConfiguration basic) {
		this.basic = basic;
	}

	public OAuth2XSUAABasicAuthenticationConfiguration getOauth2xsuaaBasic() {
		return oauth2xsuaaBasic;
	}

	public void setOauth2xsuaaBasic(OAuth2XSUAABasicAuthenticationConfiguration oauth2xsuaaBasic) {
		this.oauth2xsuaaBasic = oauth2xsuaaBasic;
	}

	public OAuth2XSUAACertificateAuthenticationConfiguration getOauth2xsuaaCertificate() {
		return oauth2xsuaaCertificate;
	}

	public void setOauth2xsuaaCertificate(OAuth2XSUAACertificateAuthenticationConfiguration oauth2xsuaaCertificate) {
		this.oauth2xsuaaCertificate = oauth2xsuaaCertificate;
	}
}
