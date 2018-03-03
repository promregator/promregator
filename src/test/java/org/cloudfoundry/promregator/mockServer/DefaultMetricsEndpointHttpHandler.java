package org.cloudfoundry.promregator.mockServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class DefaultMetricsEndpointHttpHandler implements HttpHandler {
	private Headers headers;
	
	private String response = "";
	
	@Override
	public void handle(HttpExchange he) throws IOException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		URI requestedUri = he.getRequestURI();
		String query = requestedUri.getRawQuery();
		this.headers = he.getRequestHeaders();
		
		Assert.assertEquals("/metrics", requestedUri.getPath());
		
		// send response
		he.sendResponseHeaders(200, this.response.length());
		
		OutputStream os = he.getResponseBody();
		os.write(response.getBytes());
		os.flush();
	}

	public Headers getHeaders() {
		return headers;
	}

	public void setHeaders(Headers headers) {
		this.headers = headers;
	}

	public String getResponse() {
		return response;
	}

	public void setResponse(String response) {
		this.response = response;
	}

}
