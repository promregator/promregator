package org.cloudfoundry.promregator.mockServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import org.junit.Assert;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class DefaultMetricsEndpointHttpHandler implements HttpHandler {
	private Headers headers;
	
	private String response = "";
	
	private int delayInMillis = 0;
	
	@Override
	public void handle(HttpExchange he) throws IOException {
		URI requestedUri = he.getRequestURI();
		this.headers = he.getRequestHeaders();
		
		Assert.assertEquals("/metrics", requestedUri.getPath());
		
		if (this.delayInMillis > 0) {
			try {
				Thread.sleep(this.delayInMillis);
			} catch (InterruptedException e) {
				Assert.fail("Unexpected interruption of mocking environment");
				Thread.currentThread().interrupt();
			}
		}
		
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

	/**
	 * @return the delayInMillis
	 */
	public int getDelayInMillis() {
		return delayInMillis;
	}

	/**
	 * @param delayInMillis the delayInMillis to set
	 */
	public void setDelayInMillis(int delayInMillis) {
		this.delayInMillis = delayInMillis;
	}
}
