package org.cloudfoundry.promregator.cfaccessor;

import java.util.LinkedList;
import java.util.concurrent.TimeoutException;

import org.cloudfoundry.client.v2.organizations.ListOrganizationsRequest;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.organizations.OrganizationResource;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

public class ReactiveCFPaginatedRequestFetcherTest {

	private InternalMetrics internalMetricsMocked = Mockito.mock(InternalMetrics.class);
	
	private static PaginatedRequestGeneratorFunction<ListOrganizationsRequest> requestGenerator;
	private static PaginatedResponseGeneratorFunction<OrganizationResource, ListOrganizationsResponse> responseGenerator;
	
	static {
		requestGenerator = (orderDirection, resultsPerPage, pageNumber) -> {
			return ListOrganizationsRequest.builder()
				.orderDirection(orderDirection)
				.resultsPerPage(resultsPerPage)
				.page(pageNumber)
				.build();
		};
		responseGenerator = (data, numberOfPages) -> {
			return ListOrganizationsResponse.builder()
				.resources(data)
				.totalPages(numberOfPages)
				.totalResults(data.size())
				.build();
		};
	}
	
	@Test
	public void testEmptyResponse() {
		ReactiveCFPaginatedRequestFetcher subject = new ReactiveCFPaginatedRequestFetcher(this.internalMetricsMocked);
		
		Mono<ListOrganizationsResponse> subjectResponseMono = subject.performGenericPagedRetrieval("unittest", "unittest", "nokey", requestGenerator, request -> {
			ListOrganizationsResponse response = ListOrganizationsResponse.builder()
					.resources(new LinkedList<>())
					.totalPages(1)
					.totalResults(0)
					.build();
			
			return Mono.just(response);
		}, 100, responseGenerator);
		
		ListOrganizationsResponse subjectResponse = subjectResponseMono.block();
		Assert.assertTrue (1 == subjectResponse.getTotalPages());
		Assert.assertTrue (0 == subjectResponse.getTotalResults());
		Assert.assertNotNull (subjectResponse.getResources());
		Assert.assertTrue (0 == subjectResponse.getResources().size());
	}
	
	@Test
	public void testWithContentOnePage() {
		ReactiveCFPaginatedRequestFetcher subject = new ReactiveCFPaginatedRequestFetcher(this.internalMetricsMocked);
		
		Mono<ListOrganizationsResponse> subjectResponseMono = subject.performGenericPagedRetrieval("unittest", "unittest", "nokey", requestGenerator, request -> {
			LinkedList<OrganizationResource> list = new LinkedList<>();
			list.add(OrganizationResource.builder().build());
			list.add(OrganizationResource.builder().build());
			
			ListOrganizationsResponse response = ListOrganizationsResponse.builder()
					.resources(list)
					.totalPages(1)
					.totalResults(2)
					.build();
			
			return Mono.just(response);
		}, 100, responseGenerator);
		
		ListOrganizationsResponse subjectResponse = subjectResponseMono.block();
		Assert.assertTrue (1 == subjectResponse.getTotalPages());
		Assert.assertTrue (2 == subjectResponse.getTotalResults());
		Assert.assertNotNull (subjectResponse.getResources());
		Assert.assertTrue (2 == subjectResponse.getResources().size());
	}
	
	@Test
	public void testWithContentTwoPages() {
		ReactiveCFPaginatedRequestFetcher subject = new ReactiveCFPaginatedRequestFetcher(this.internalMetricsMocked);
		
		Mono<ListOrganizationsResponse> subjectResponseMono = subject.performGenericPagedRetrieval("unittest", "unittest", "nokey", requestGenerator, request -> {
			LinkedList<OrganizationResource> list = new LinkedList<>();
			
			for (int i = 0;i<request.getResultsPerPage(); i++) {
				list.add(OrganizationResource.builder().build());
			}
			
			ListOrganizationsResponse response = ListOrganizationsResponse.builder()
					.resources(list)
					.totalPages(2)
					.totalResults(2 * request.getResultsPerPage())
					.build();
			
			return Mono.just(response);
		}, 100, responseGenerator);
		
		ListOrganizationsResponse subjectResponse = subjectResponseMono.block();
		Assert.assertTrue (2 == subjectResponse.getTotalPages());
		Assert.assertTrue (2*100 == subjectResponse.getTotalResults());
		Assert.assertNotNull (subjectResponse.getResources());
		Assert.assertTrue (2*100 == subjectResponse.getResources().size());
	}
	
	@Test
	public void testWithContentTwoPagesSecondWithoutItems() {
		ReactiveCFPaginatedRequestFetcher subject = new ReactiveCFPaginatedRequestFetcher(this.internalMetricsMocked);
		
		Mono<ListOrganizationsResponse> subjectResponseMono = subject.performGenericPagedRetrieval("unittest", "unittest", "nokey", requestGenerator, request -> {
			LinkedList<OrganizationResource> list = new LinkedList<>();
			
			if (request.getPage() == 1) {
				for (int i = 0;i<request.getResultsPerPage(); i++) {
					list.add(OrganizationResource.builder().build());
				}
			}
			
			ListOrganizationsResponse response = ListOrganizationsResponse.builder()
					.resources(list)
					.totalPages(2)
					.totalResults( (2 - request.getPage()) * request.getResultsPerPage()) // that's tricky&ugly, but correct: It would be the answer, if suddenly the items on the 2nd page vanished
					.build();
			
			return Mono.just(response);
		}, 100, responseGenerator);
		
		ListOrganizationsResponse subjectResponse = subjectResponseMono.block();
		Assert.assertTrue (2 == subjectResponse.getTotalPages());
		Assert.assertTrue (1*100 == subjectResponse.getTotalResults());
		Assert.assertNotNull (subjectResponse.getResources());
		Assert.assertTrue (1*100 == subjectResponse.getResources().size());
	}
	
	@Test
	public void testWithContentTimeoutOnFirstPage() {
		ReactiveCFPaginatedRequestFetcher subject = new ReactiveCFPaginatedRequestFetcher(this.internalMetricsMocked);
		
		Mono<ListOrganizationsResponse> subjectResponseMono = subject.performGenericPagedRetrieval("unittest", "unittest", "nokey", requestGenerator, request -> {
			return Mono.error(new TimeoutException());
		}, 100, responseGenerator);
		
		ListOrganizationsResponse fallback = ListOrganizationsResponse.builder().build();
		
		ListOrganizationsResponse subjectResponse = subjectResponseMono.doOnError(e -> {
			Assert.assertTrue(Exceptions.unwrap(e) instanceof TimeoutException);
		}).onErrorReturn(fallback).block();
		Assert.assertEquals(fallback, subjectResponse);
	}
	
	@Test
	public void testWithContentTimeoutOnSecondPage() {
		ReactiveCFPaginatedRequestFetcher subject = new ReactiveCFPaginatedRequestFetcher(this.internalMetricsMocked);
		
		Mono<ListOrganizationsResponse> subjectResponseMono = subject.performGenericPagedRetrieval("unittest", "unittest", "nokey", requestGenerator, request -> {
			if (request.getPage() == 2) {
				return Mono.error(new TimeoutException());
			}
			
			LinkedList<OrganizationResource> list = new LinkedList<>();
			
			if (request.getPage() == 1) {
				for (int i = 0;i<request.getResultsPerPage(); i++) {
					list.add(OrganizationResource.builder().build());
				}
			}
			
			ListOrganizationsResponse response = ListOrganizationsResponse.builder()
					.resources(list)
					.totalPages(2)
					.totalResults(2 * request.getResultsPerPage()) 
					.build();
			
			return Mono.just(response);
		}, 100, responseGenerator);
		
		ListOrganizationsResponse fallback = ListOrganizationsResponse.builder().build();
		
		ListOrganizationsResponse subjectResponse = subjectResponseMono.doOnError(e -> {
			Assert.assertTrue(Exceptions.unwrap(e) instanceof TimeoutException);
		}).onErrorReturn(fallback).block();
		Assert.assertEquals(fallback, subjectResponse);
	}

	@Test
	public void testWithContentGeneralException() {
		ReactiveCFPaginatedRequestFetcher subject = new ReactiveCFPaginatedRequestFetcher(this.internalMetricsMocked);
		
		Mono<ListOrganizationsResponse> subjectResponseMono = subject.performGenericPagedRetrieval("unittest", "unittest", "nokey", requestGenerator, request -> {
			return Mono.error(new Exception());
		}, 100, responseGenerator);
		
		ListOrganizationsResponse fallback = ListOrganizationsResponse.builder().build();
		
		ListOrganizationsResponse subjectResponse = subjectResponseMono.doOnError(e -> {
			Assert.assertTrue(Exceptions.unwrap(e) instanceof Exception);
		}).onErrorReturn(fallback).block();
		Assert.assertEquals(fallback, subjectResponse);
	}
}
