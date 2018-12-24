package org.cloudfoundry.promregator.fetcher;

import java.nio.charset.Charset;

import org.junit.Test;

import reactor.ipc.netty.http.client.HttpClient;

public class NettyFetcherProbe {

	@Test
	public void test() throws InterruptedException {
		

		for (int i = 0;i<3;i++) {
			HttpClient.create()
				.get("https://promregator-client1.eu-gb.mybluemix.net/metrics", request -> {
					request.header("X-CF-APP-INSTANCE", "262ec022-8366-4c49-ac13-f50b35a78154:0");
					return request;
				})
				.flatMap(resp -> resp.receive().aggregate().asString(Charset.forName("UTF-8")))
				.doOnNext(resp -> {
					System.out.println("xxx\n");
				})
				.subscribe();
		}
		
		Thread.sleep(1000);
	}
	
}
