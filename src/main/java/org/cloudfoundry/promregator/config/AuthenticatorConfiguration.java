package org.cloudfoundry.promregator.config;

public class AuthenticatorConfiguration {
	private String type;
	
	private BasicAuthenticationConfiguration basic;

	private OAuth2XSUAAAuthenticationConfiguration oauth2xsuaa;

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

	public OAuth2XSUAAAuthenticationConfiguration getOauth2xsuaa() {
		return oauth2xsuaa;
	}

	public void setOauth2xsuaa(OAuth2XSUAAAuthenticationConfiguration oauth2xsuaa) {
		this.oauth2xsuaa = oauth2xsuaa;
	}

	public OAuth2XSUAACertificateAuthenticationConfiguration getOauth2xsuaaCertificate() {
		return oauth2xsuaaCertificate;
	}

	public void setOauth2xsuaaCertificate(OAuth2XSUAACertificateAuthenticationConfiguration oauth2xsuaaCertificate) {
		this.oauth2xsuaaCertificate = oauth2xsuaaCertificate;
	}
}
