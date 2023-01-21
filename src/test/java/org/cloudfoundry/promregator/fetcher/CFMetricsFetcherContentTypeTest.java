package org.cloudfoundry.promregator.fetcher;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.UUID;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.exporter.common.TextFormat;

class CFMetricsFetcherContentTypeTest {

	private static final String DUMMY_METRICS_LIST = "# HELP dummy This is a dummy metric\n"+
			"# TYPE dummy counter\n"+
			"dummy 42 1395066363000";
	private static final byte[] DUMMY_METRICS_LIST_BYTE_ARRAY = DUMMY_METRICS_LIST.getBytes(Charset.defaultCharset());
	
	private class TestableCFMetricsFetcher extends CFMetricsFetcher {

		private CloseableHttpClient mockedCloseableHttpClient;
		
		public TestableCFMetricsFetcher(String endpointUrl, String instanceId, CFMetricsFetcherConfig config,
				boolean withInternalRouting) {
			super(endpointUrl, instanceId, config, withInternalRouting);
			
			this.mockedCloseableHttpClient = Mockito.mock(CloseableHttpClient.class);
			
			super.setLocalHttpClient(this.mockedCloseableHttpClient);
		}

		/**
		 * @return the mockedCloseableHttpClient
		 */
		public CloseableHttpClient getMockedCloseableHttpClient() {
			return mockedCloseableHttpClient;
		}
		
	}
	
	@Test
	void testCompatibleDualAcceptHeaderSet() throws Exception {
		
		CFMetricsFetcherConfig config = Mockito.mock(CFMetricsFetcherConfig.class);
		Mockito.when(config.getPromregatorInstanceIdentifier()).thenReturn(UUID.randomUUID());
		
		MetricsFetcherMetrics mfm = Mockito.mock(MetricsFetcherMetrics.class);
		Mockito.when(config.getMetricsFetcherMetrics()).thenReturn(mfm);
		
		final TestableCFMetricsFetcher subject = new TestableCFMetricsFetcher("dummy", "dummy", config, false);
		
		CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
		Mockito.when(response.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("http", 1, 1), 200, "OK"));
		HttpEntity httpEntity = Mockito.mock(HttpEntity.class);
		Mockito.when(httpEntity.getContentLength()).thenReturn(0L);
		Mockito.when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(new byte[0]));
		Mockito.when(response.getEntity()).thenReturn(httpEntity);
		
		Mockito.when(subject.getMockedCloseableHttpClient().execute(Mockito.any())).thenReturn(response);
		
		subject.call();
		
		ArgumentCaptor<HttpGet> httpGetCaptor = ArgumentCaptor.forClass(HttpGet.class);
		Mockito.verify(subject.getMockedCloseableHttpClient()).execute(httpGetCaptor.capture());
		HttpGet httpGet = httpGetCaptor.getValue();
		Header acceptHeader = httpGet.getFirstHeader(HttpHeaders.ACCEPT);
		
		Assertions.assertEquals("application/openmetrics-text; version=1.0.0; charset=utf-8, text/plain; version=0.0.4; charset=utf-8;q=0.9", acceptHeader.getValue());
	}
	
	@Test
	void testInvalidContentTypeProvided() throws Exception {
		
		CFMetricsFetcherConfig config = Mockito.mock(CFMetricsFetcherConfig.class);
		Mockito.when(config.getPromregatorInstanceIdentifier()).thenReturn(UUID.randomUUID());
		
		MetricsFetcherMetrics mfm = Mockito.mock(MetricsFetcherMetrics.class);
		Mockito.when(config.getMetricsFetcherMetrics()).thenReturn(mfm);
		
		final TestableCFMetricsFetcher subject = new TestableCFMetricsFetcher("dummy", "dummy", config, false);
		
		CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
		Mockito.when(response.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("http", 1, 1), 200, "OK"));
		
		// that's the trick
		Header contentTypeHeader = Mockito.mock(Header.class);
		Mockito.when(contentTypeHeader.getValue()).thenReturn(ContentType.APPLICATION_JSON.getMimeType() /* anything invalid */);
		Mockito.when(response.getFirstHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(contentTypeHeader);
		
		HttpEntity httpEntity = Mockito.mock(HttpEntity.class);
		Mockito.when(httpEntity.getContentLength()).thenReturn(0L);
		Mockito.when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(new byte[0]));
		Mockito.when(response.getEntity()).thenReturn(httpEntity);
		
		Mockito.when(subject.getMockedCloseableHttpClient().execute(Mockito.any())).thenReturn(response);
		
		HashMap<String, MetricFamilySamples> result = subject.call();
		
		Assertions.assertNull(result);
	}
	
	@Test
	void testText004StillWorks() throws Exception {
		
		CFMetricsFetcherConfig config = Mockito.mock(CFMetricsFetcherConfig.class);
		Mockito.when(config.getPromregatorInstanceIdentifier()).thenReturn(UUID.randomUUID());
		
		MetricsFetcherMetrics mfm = Mockito.mock(MetricsFetcherMetrics.class);
		Mockito.when(config.getMetricsFetcherMetrics()).thenReturn(mfm);
		
		final TestableCFMetricsFetcher subject = new TestableCFMetricsFetcher("dummy", "dummy", config, false);
		
		CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
		Mockito.when(response.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("http", 1, 1), 200, "OK"));
		
		// that's the trick
		Header contentTypeHeader = Mockito.mock(Header.class);
		Mockito.when(contentTypeHeader.getValue()).thenReturn(TextFormat.CONTENT_TYPE_004);
		Mockito.when(response.getFirstHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(contentTypeHeader);
		
		HttpEntity httpEntity = Mockito.mock(HttpEntity.class);
		Mockito.when(httpEntity.getContentLength()).thenReturn((long) DUMMY_METRICS_LIST_BYTE_ARRAY.length);
		Mockito.when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(DUMMY_METRICS_LIST_BYTE_ARRAY));
		Mockito.when(response.getEntity()).thenReturn(httpEntity);
		
		Mockito.when(subject.getMockedCloseableHttpClient().execute(Mockito.any())).thenReturn(response);
		
		HashMap<String, MetricFamilySamples> result = subject.call();
		
		// just prove that the parser was called
		Assertions.assertEquals(1, result.size());
		Assertions.assertNotNull(result.get("dummy"));
	}
	
	@Test
	void testOpenMetrics100WorksAsWell() throws Exception {
		
		CFMetricsFetcherConfig config = Mockito.mock(CFMetricsFetcherConfig.class);
		Mockito.when(config.getPromregatorInstanceIdentifier()).thenReturn(UUID.randomUUID());
		
		MetricsFetcherMetrics mfm = Mockito.mock(MetricsFetcherMetrics.class);
		Mockito.when(config.getMetricsFetcherMetrics()).thenReturn(mfm);
		
		final TestableCFMetricsFetcher subject = new TestableCFMetricsFetcher("dummy", "dummy", config, false);
		
		CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
		Mockito.when(response.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("http", 1, 1), 200, "OK"));
		
		// that's the trick
		Header contentTypeHeader = Mockito.mock(Header.class);
		Mockito.when(contentTypeHeader.getValue()).thenReturn(TextFormat.CONTENT_TYPE_OPENMETRICS_100);
		Mockito.when(response.getFirstHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(contentTypeHeader);
		
		HttpEntity httpEntity = Mockito.mock(HttpEntity.class);
		Mockito.when(httpEntity.getContentLength()).thenReturn((long) DUMMY_METRICS_LIST_BYTE_ARRAY.length);
		Mockito.when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(DUMMY_METRICS_LIST_BYTE_ARRAY));
		Mockito.when(response.getEntity()).thenReturn(httpEntity);
		
		Mockito.when(subject.getMockedCloseableHttpClient().execute(Mockito.any())).thenReturn(response);
		
		HashMap<String, MetricFamilySamples> result = subject.call();
		
		// just prove that the parser was called
		Assertions.assertEquals(1, result.size());
		Assertions.assertNotNull(result.get("dummy"));
	}

}
