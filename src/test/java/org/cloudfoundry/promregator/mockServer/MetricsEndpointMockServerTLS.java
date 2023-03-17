package org.cloudfoundry.promregator.mockServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.junit.jupiter.api.Assertions;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

@SuppressWarnings("restriction")
public class MetricsEndpointMockServerTLS {
	private HttpsServer server;

	protected int port = 9003;

	private DefaultMetricsEndpointHttpHandler myHandler;

	public MetricsEndpointMockServerTLS() {
	}

	public void start() throws IOException {
		InetSocketAddress bindAddress = new InetSocketAddress("127.0.0.1", this.port);
		this.server = HttpsServer.create(bindAddress, 0);
		this.server.setHttpsConfigurator(new HttpsConfigurator(this.createSslContext()) {
			@Override
			public void configure(HttpsParameters params) {
				SSLContext c = getSSLContext();
				params.setSSLParameters(c.getDefaultSSLParameters());
			}
		});

		this.server.createContext("/metrics", this.getMetricsEndpointHandler());

		this.server.start();
	}

	private SSLContext createSslContext() {
		final char[] passphrase = "unittest".toCharArray();

		KeyStore ks = null;
		try {
			ks = KeyStore.getInstance("JKS");
		} catch (KeyStoreException e) {
			Assertions.fail("Unable to set up JKS Keystore");
		}
		try {
			ks.load(MetricsEndpointMockServerTLS.class.getResourceAsStream("selfsigned.jks"), passphrase);
		} catch (NoSuchAlgorithmException | CertificateException | IOException e) {
			Assertions.fail("Unable to load selfsigned keystore");
		}

		KeyManagerFactory kmf = null;
		try {
			kmf = KeyManagerFactory.getInstance("SunX509");
		} catch (NoSuchAlgorithmException e) {
			Assertions.fail("Unable to retrieve KeyManagerFactory");
		}
		try {
			kmf.init(ks, passphrase);
		} catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException e) {
			Assertions.fail("Unable to initialize KeyManagerFactory");
		}

		TrustManagerFactory tmf = null;
		try {
			tmf = TrustManagerFactory.getInstance("SunX509");
		} catch (NoSuchAlgorithmException e) {
			Assertions.fail("Unable to retrieve TrustManagerFactory");
		}
		
		try {
			tmf.init(ks);
		} catch (KeyStoreException e) {
			Assertions.fail("Unable to initialize TrustManagerFactory");
		}
		
		SSLContext ssl = null;
		try {
			ssl = SSLContext.getInstance("TLS");
		} catch (NoSuchAlgorithmException e) {
			Assertions.fail("Unable to retrieve SSLContext");
		}
		try {
			ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		} catch (KeyManagementException e) {
			Assertions.fail("Unable to initialize SSLContext");
		}
		
		return ssl;
	}

	public DefaultMetricsEndpointHttpHandler getMetricsEndpointHandler() {
		if (this.myHandler != null) {
			return myHandler;
		}

		this.myHandler = new DefaultMetricsEndpointHttpHandler();
		return this.myHandler;
	}

	public void stop() {
		this.server.stop(1);
	}
}
