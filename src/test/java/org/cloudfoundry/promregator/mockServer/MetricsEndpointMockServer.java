package org.cloudfoundry.promregator.mockServer;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.*;

public class MetricsEndpointMockServer {
	private HttpServer server;
	
	protected int port = 9002;
	
	private DefaultMetricsEndpointHttpHandler myHandler;
	
	public MetricsEndpointMockServer() {
	}
	
	public void start() throws IOException {
		InetSocketAddress bindAddress = new InetSocketAddress("127.0.0.1", this.port);
		this.server = HttpServer.create(bindAddress, 0);
		
		this.server.createContext("/metrics", this.getMetricsEndpointHandler());
		
		this.server.start();
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
