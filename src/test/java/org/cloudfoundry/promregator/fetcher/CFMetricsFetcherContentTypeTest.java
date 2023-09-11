package org.cloudfoundry.promregator.fetcher;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.UUID;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.http.entity.ContentType;
import org.cloudfoundry.promregator.textformat004.ParserCompareUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpHeaders;

import io.prometheus.client.exporter.common.TextFormat;

class CFMetricsFetcherContentTypeTest {

	private static final String DUMMY_METRICS_LIST = "# HELP dummy This is a dummy metric\n"+
			"# TYPE dummy counter\n"+
			"dummy 42 1395066363000";
	private static final byte[] DUMMY_METRICS_LIST_BYTE_ARRAY = DUMMY_METRICS_LIST.getBytes(Charset.defaultCharset());
	
	@Test
	void testCompatibleDualAcceptHeaderSet() throws Exception {
		
		CFMetricsFetcherConfig config = Mockito.mock(CFMetricsFetcherConfig.class);
		Mockito.when(config.getPromregatorInstanceIdentifier()).thenReturn(UUID.randomUUID());
		
		MetricsFetcherMetrics mfm = Mockito.mock(MetricsFetcherMetrics.class);
		Mockito.when(config.getMetricsFetcherMetrics()).thenReturn(mfm);
		
		CloseableHttpClient closeableHttpClientMock = Mockito.mock(CloseableHttpClient.class);
		
		CFMetricsFetcherConnManager cfMetricsFetcherConnManagerMock = Mockito.mock(CFMetricsFetcherConnManager.class);
		Mockito.when(cfMetricsFetcherConnManagerMock.getHttpclient()).thenReturn(closeableHttpClientMock);
		
		Mockito.when(config.getCfMetricsFetcherConnManager()).thenReturn(cfMetricsFetcherConnManagerMock);
		
		final CFMetricsFetcher subject = new CFMetricsFetcher("dummy", "dummy", config, false);
		
		CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
		Mockito.when(response.getCode()).thenReturn(200);
		HttpEntity httpEntity = Mockito.mock(HttpEntity.class);
		Mockito.when(httpEntity.getContentLength()).thenReturn(0L);
		Mockito.when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(new byte[0]));
		Mockito.when(response.getEntity()).thenReturn(httpEntity);
		
		Mockito.when(closeableHttpClientMock.execute((HttpGet) Mockito.any(HttpGet.class), (HttpClientResponseHandler<? extends Object>) Mockito.any(HttpClientResponseHandler.class))).thenAnswer(new Answer<Object>() {

			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable {
				HttpClientResponseHandler<Object> handler = invocation.getArgument(1, HttpClientResponseHandler.class);
				return handler.handleResponse(response);
			}
		});
		
		subject.call();
		
		ArgumentCaptor<HttpGet> httpGetCaptor = ArgumentCaptor.forClass(HttpGet.class);
		ArgumentCaptor<HttpClientResponseHandler> httpClientResponseHandlerCaptuor = ArgumentCaptor.forClass(HttpClientResponseHandler.class);
		Mockito.verify(closeableHttpClientMock).execute(httpGetCaptor.capture(), httpClientResponseHandlerCaptuor.capture());
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
		
		CloseableHttpClient closeableHttpClientMock = Mockito.mock(CloseableHttpClient.class);
		
		CFMetricsFetcherConnManager cfMetricsFetcherConnManagerMock = Mockito.mock(CFMetricsFetcherConnManager.class);
		Mockito.when(cfMetricsFetcherConnManagerMock.getHttpclient()).thenReturn(closeableHttpClientMock);
		
		Mockito.when(config.getCfMetricsFetcherConnManager()).thenReturn(cfMetricsFetcherConnManagerMock);
		
		final CFMetricsFetcher subject = new CFMetricsFetcher("dummy", "dummy", config, false);
		
		CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
		Mockito.when(response.getCode()).thenReturn(200);
		
		// that's the trick
		Header contentTypeHeader = Mockito.mock(Header.class);
		Mockito.when(contentTypeHeader.getValue()).thenReturn(ContentType.APPLICATION_JSON.getMimeType() /* anything invalid */);
		Mockito.when(response.getFirstHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(contentTypeHeader);
		
		HttpEntity httpEntity = Mockito.mock(HttpEntity.class);
		Mockito.when(httpEntity.getContentLength()).thenReturn(0L);
		Mockito.when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(new byte[0]));
		Mockito.when(response.getEntity()).thenReturn(httpEntity);
		
		Mockito.when(closeableHttpClientMock.execute((HttpGet) Mockito.any(HttpGet.class), (HttpClientResponseHandler<? extends Object>) Mockito.any(HttpClientResponseHandler.class))).thenAnswer(new Answer<Object>() {

			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable {
				HttpClientResponseHandler<Object> handler = invocation.getArgument(1, HttpClientResponseHandler.class);
				return handler.handleResponse(response);
			}
		});
		
		FetchResult result = subject.call();
		
		Assertions.assertNull(result);
	}
	
	@Test
	void testText004StillWorks() throws Exception {
		
		CFMetricsFetcherConfig config = Mockito.mock(CFMetricsFetcherConfig.class);
		Mockito.when(config.getPromregatorInstanceIdentifier()).thenReturn(UUID.randomUUID());
		
		MetricsFetcherMetrics mfm = Mockito.mock(MetricsFetcherMetrics.class);
		Mockito.when(config.getMetricsFetcherMetrics()).thenReturn(mfm);
		
		CloseableHttpClient closeableHttpClientMock = Mockito.mock(CloseableHttpClient.class);
		
		CFMetricsFetcherConnManager cfMetricsFetcherConnManagerMock = Mockito.mock(CFMetricsFetcherConnManager.class);
		Mockito.when(cfMetricsFetcherConnManagerMock.getHttpclient()).thenReturn(closeableHttpClientMock);
		
		Mockito.when(config.getCfMetricsFetcherConnManager()).thenReturn(cfMetricsFetcherConnManagerMock);
		
		final CFMetricsFetcher subject = new CFMetricsFetcher("dummy", "dummy", config, false);
		
		CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
		Mockito.when(response.getCode()).thenReturn(200);
		
		// that's the trick
		Header contentTypeHeader = Mockito.mock(Header.class);
		Mockito.when(contentTypeHeader.getValue()).thenReturn(TextFormat.CONTENT_TYPE_004);
		Mockito.when(response.getFirstHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(contentTypeHeader);
		
		HttpEntity httpEntity = Mockito.mock(HttpEntity.class);
		Mockito.when(httpEntity.getContentLength()).thenReturn((long) DUMMY_METRICS_LIST_BYTE_ARRAY.length);
		Mockito.when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(DUMMY_METRICS_LIST_BYTE_ARRAY));
		Mockito.when(response.getEntity()).thenReturn(httpEntity);
		
		Mockito.when(closeableHttpClientMock.execute((HttpGet) Mockito.any(HttpGet.class), (HttpClientResponseHandler<? extends Object>) Mockito.any(HttpClientResponseHandler.class))).thenAnswer(new Answer<Object>() {

			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable {
				HttpClientResponseHandler<Object> handler = invocation.getArgument(1, HttpClientResponseHandler.class);
				return handler.handleResponse(response);
			}
		});
		
		FetchResult result = subject.call();
		
		Assertions.assertNotNull(result);
		Assertions.assertEquals(TextFormat.CONTENT_TYPE_004, result.contentType());
		ParserCompareUtils.compareFetchResult(result, DUMMY_METRICS_LIST);
	}
	
	@Test
	void testOpenMetrics100WorksAsWell() throws Exception {
		
		CFMetricsFetcherConfig config = Mockito.mock(CFMetricsFetcherConfig.class);
		Mockito.when(config.getPromregatorInstanceIdentifier()).thenReturn(UUID.randomUUID());
		
		MetricsFetcherMetrics mfm = Mockito.mock(MetricsFetcherMetrics.class);
		Mockito.when(config.getMetricsFetcherMetrics()).thenReturn(mfm);
		
		CloseableHttpClient closeableHttpClientMock = Mockito.mock(CloseableHttpClient.class);
		
		CFMetricsFetcherConnManager cfMetricsFetcherConnManagerMock = Mockito.mock(CFMetricsFetcherConnManager.class);
		Mockito.when(cfMetricsFetcherConnManagerMock.getHttpclient()).thenReturn(closeableHttpClientMock);
		
		Mockito.when(config.getCfMetricsFetcherConnManager()).thenReturn(cfMetricsFetcherConnManagerMock);

		final CFMetricsFetcher subject = new CFMetricsFetcher("dummy", "dummy", config, false);
		
		CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
		Mockito.when(response.getCode()).thenReturn(200);
		
		// that's the trick
		Header contentTypeHeader = Mockito.mock(Header.class);
		Mockito.when(contentTypeHeader.getValue()).thenReturn(TextFormat.CONTENT_TYPE_OPENMETRICS_100);
		Mockito.when(response.getFirstHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(contentTypeHeader);
		
		HttpEntity httpEntity = Mockito.mock(HttpEntity.class);
		Mockito.when(httpEntity.getContentLength()).thenReturn((long) DUMMY_METRICS_LIST_BYTE_ARRAY.length);
		Mockito.when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(DUMMY_METRICS_LIST_BYTE_ARRAY));
		Mockito.when(response.getEntity()).thenReturn(httpEntity);
		
		Mockito.when(closeableHttpClientMock.execute((HttpGet) Mockito.any(HttpGet.class), (HttpClientResponseHandler<? extends Object>) Mockito.any(HttpClientResponseHandler.class))).thenAnswer(new Answer<Object>() {

			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable {
				HttpClientResponseHandler<Object> handler = invocation.getArgument(1, HttpClientResponseHandler.class);
				return handler.handleResponse(response);
			}
		});
		
		FetchResult result = subject.call();
		
		Assertions.assertNotNull(result);
		Assertions.assertEquals(TextFormat.CONTENT_TYPE_OPENMETRICS_100, result.contentType());
		ParserCompareUtils.compareFetchResult(result, DUMMY_METRICS_LIST);
	}

	@Test
	void testDoNotMatchWithWrongVersionFormatOpenMetrics100() throws Exception {
		
		CFMetricsFetcherConfig config = Mockito.mock(CFMetricsFetcherConfig.class);
		Mockito.when(config.getPromregatorInstanceIdentifier()).thenReturn(UUID.randomUUID());
		
		MetricsFetcherMetrics mfm = Mockito.mock(MetricsFetcherMetrics.class);
		Mockito.when(config.getMetricsFetcherMetrics()).thenReturn(mfm);
		
		CloseableHttpClient closeableHttpClientMock = Mockito.mock(CloseableHttpClient.class);
		
		CFMetricsFetcherConnManager cfMetricsFetcherConnManagerMock = Mockito.mock(CFMetricsFetcherConnManager.class);
		Mockito.when(cfMetricsFetcherConnManagerMock.getHttpclient()).thenReturn(closeableHttpClientMock);
		
		Mockito.when(config.getCfMetricsFetcherConnManager()).thenReturn(cfMetricsFetcherConnManagerMock);
		
		final CFMetricsFetcher subject = new CFMetricsFetcher("dummy", "dummy", config, false);
		
		CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
		Mockito.when(response.getCode()).thenReturn(200);
		
		// that's the trick
		Header contentTypeHeader = Mockito.mock(Header.class);
		Mockito.when(contentTypeHeader.getValue()).thenReturn("application/openmetrics-text; version=1x0y0; charset=utf-8");
		Mockito.when(response.getFirstHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(contentTypeHeader);
		
		HttpEntity httpEntity = Mockito.mock(HttpEntity.class);
		Mockito.when(httpEntity.getContentLength()).thenReturn((long) DUMMY_METRICS_LIST_BYTE_ARRAY.length);
		Mockito.when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(DUMMY_METRICS_LIST_BYTE_ARRAY));
		Mockito.when(response.getEntity()).thenReturn(httpEntity);
		
		Mockito.when(closeableHttpClientMock.execute((HttpGet) Mockito.any(HttpGet.class), (HttpClientResponseHandler<? extends Object>) Mockito.any(HttpClientResponseHandler.class))).thenAnswer(new Answer<Object>() {

			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable {
				HttpClientResponseHandler<Object> handler = invocation.getArgument(1, HttpClientResponseHandler.class);
				return handler.handleResponse(response);
			}
		});
		
		FetchResult result = subject.call();
		
		Assertions.assertNull(result);
	}
	
	@Test
	void testDoNotMatchWithWrongVersionFormatText004() throws Exception {
		
		CFMetricsFetcherConfig config = Mockito.mock(CFMetricsFetcherConfig.class);
		Mockito.when(config.getPromregatorInstanceIdentifier()).thenReturn(UUID.randomUUID());
		
		MetricsFetcherMetrics mfm = Mockito.mock(MetricsFetcherMetrics.class);
		Mockito.when(config.getMetricsFetcherMetrics()).thenReturn(mfm);
		
		CloseableHttpClient closeableHttpClientMock = Mockito.mock(CloseableHttpClient.class);
		
		CFMetricsFetcherConnManager cfMetricsFetcherConnManagerMock = Mockito.mock(CFMetricsFetcherConnManager.class);
		Mockito.when(cfMetricsFetcherConnManagerMock.getHttpclient()).thenReturn(closeableHttpClientMock);
		
		Mockito.when(config.getCfMetricsFetcherConnManager()).thenReturn(cfMetricsFetcherConnManagerMock);
		
		final CFMetricsFetcher subject = new CFMetricsFetcher("dummy", "dummy", config, false);
		
		CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
		Mockito.when(response.getCode()).thenReturn(200);
		
		// that's the trick
		Header contentTypeHeader = Mockito.mock(Header.class);
		Mockito.when(contentTypeHeader.getValue()).thenReturn("text/plain; version=0x0y4; charset=utf-8");
		Mockito.when(response.getFirstHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(contentTypeHeader);
		
		HttpEntity httpEntity = Mockito.mock(HttpEntity.class);
		Mockito.when(httpEntity.getContentLength()).thenReturn((long) DUMMY_METRICS_LIST_BYTE_ARRAY.length);
		Mockito.when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(DUMMY_METRICS_LIST_BYTE_ARRAY));
		Mockito.when(response.getEntity()).thenReturn(httpEntity);
		
		Mockito.when(closeableHttpClientMock.execute((HttpGet) Mockito.any(HttpGet.class), (HttpClientResponseHandler<? extends Object>) Mockito.any(HttpClientResponseHandler.class))).thenAnswer(new Answer<Object>() {

			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable {
				HttpClientResponseHandler<Object> handler = invocation.getArgument(1, HttpClientResponseHandler.class);
				return handler.handleResponse(response);
			}
		});
		
		FetchResult result = subject.call();
		
		Assertions.assertNull(result);
	}
	
	@Test
	void testOpenMetricsWithWrongFormatIdentifier() throws Exception {
		
		CFMetricsFetcherConfig config = Mockito.mock(CFMetricsFetcherConfig.class);
		Mockito.when(config.getPromregatorInstanceIdentifier()).thenReturn(UUID.randomUUID());
		
		MetricsFetcherMetrics mfm = Mockito.mock(MetricsFetcherMetrics.class);
		Mockito.when(config.getMetricsFetcherMetrics()).thenReturn(mfm);
		
		CloseableHttpClient closeableHttpClientMock = Mockito.mock(CloseableHttpClient.class);
		
		CFMetricsFetcherConnManager cfMetricsFetcherConnManagerMock = Mockito.mock(CFMetricsFetcherConnManager.class);
		Mockito.when(cfMetricsFetcherConnManagerMock.getHttpclient()).thenReturn(closeableHttpClientMock);
		
		Mockito.when(config.getCfMetricsFetcherConnManager()).thenReturn(cfMetricsFetcherConnManagerMock);
		
		final CFMetricsFetcher subject = new CFMetricsFetcher("dummy", "dummy", config, false);
		
		CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
		Mockito.when(response.getCode()).thenReturn(200);
		
		// that's the trick
		Header contentTypeHeader = Mockito.mock(Header.class);
		Mockito.when(contentTypeHeader.getValue()).thenReturn("application/openmetrics-text; version=0.0.42; charset=utf-8");
		Mockito.when(response.getFirstHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(contentTypeHeader);
		
		HttpEntity httpEntity = Mockito.mock(HttpEntity.class);
		Mockito.when(httpEntity.getContentLength()).thenReturn((long) DUMMY_METRICS_LIST_BYTE_ARRAY.length);
		Mockito.when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(DUMMY_METRICS_LIST_BYTE_ARRAY));
		Mockito.when(response.getEntity()).thenReturn(httpEntity);
		
		Mockito.when(closeableHttpClientMock.execute((HttpGet) Mockito.any(HttpGet.class), (HttpClientResponseHandler<? extends Object>) Mockito.any(HttpClientResponseHandler.class))).thenAnswer(new Answer<Object>() {

			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable {
				HttpClientResponseHandler<Object> handler = invocation.getArgument(1, HttpClientResponseHandler.class);
				return handler.handleResponse(response);
			}
		});
		
		FetchResult result = subject.call();
		
		Assertions.assertNotNull(result);
		Assertions.assertEquals(TextFormat.CONTENT_TYPE_OPENMETRICS_100, result.contentType());
		ParserCompareUtils.compareFetchResult(result, DUMMY_METRICS_LIST);
	}
	
}
