package org.cloudfoundry.promregator.mockServer;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;

public class AuthenticationMockServer {
	private HttpServer server;
	
	protected int port = 9001;
	
	private DefaultOAuthHttpHandler myHandler;
	
	public AuthenticationMockServer() {
	}
	
	public void start() throws IOException {
		InetSocketAddress bindAddress = new InetSocketAddress("127.0.0.1", this.port);
		this.server = HttpServer.create(bindAddress, 0);
		
		this.server.createContext("/oauth/token", this.getOauthTokenHandler());
		
		this.server.start();
	}
	
	public DefaultOAuthHttpHandler getOauthTokenHandler() {
		if (this.myHandler != null) {
			return this.myHandler;
		}
		
		this.myHandler = new DefaultOAuthHttpHandler();
		return this.myHandler;
	}
	
	public void stop() {
		this.server.stop(1);
	}
}
