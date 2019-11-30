package org.cloudfoundry.promregator.cfaccessor;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.Level;

import org.apache.log4j.Logger;
import org.cloudfoundry.client.v2.OrderDirection;
import org.cloudfoundry.client.v2.PaginatedRequest;
import org.cloudfoundry.client.v2.PaginatedResponse;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;

import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ReactiveCFPaginatedRequestFetcher {
	private static final Logger log = Logger.getLogger(ReactiveCFPaginatedRequestFetcher.class);

	private static final int MAX_SUPPORTED_RESULTS_PER_PAGE = 100;
	private static final int RESULTS_PER_PAGE = MAX_SUPPORTED_RESULTS_PER_PAGE;

	private InternalMetrics internalMetrics;

	public ReactiveCFPaginatedRequestFetcher(InternalMetrics internalMetrics) {
		super();
		this.internalMetrics = internalMetrics;
	}

	/**
	 * performs standard (raw) retrieval from the CF Cloud Controller of a single
	 * page
	 * 
	 * @param retrievalTypeName
	 *            the name of type of the request which is being made; used for
	 *            identification in internalMetrics
	 * @param logName
	 *            the name of the logger category, which shall be used for logging
	 *            this Reactor operation
	 * @param key
	 *            the key for which the request is being made (e.g. orgId,
	 *            orgId|spaceName, ...)
	 * @param requestData
	 *            an object which is being used as input parameter for the request
	 * @param requestFunction
	 *            a function which calls the CF API operation, which is being made,
	 *            <code>requestData</code> is used as input parameter for this
	 *            function.
	 * @param timeoutInMS
	 *            the timeout value in milliseconds for a single data request to the
	 *            CF Cloud Controller
	 * @return a Mono on the response provided by the CF Cloud Controller
	 */
	@SuppressWarnings("lgtm[java/sync-on-boxed-types]")
	/*
	 * Reasoning: The string used as foundation for the lock is made very unique.
	 * The risk of another thread using exactly the very same value of the string for a different purpose
	 * is considered acceptable.
	 * Moreover, the performance impact of the lock being engaged improperly is considered small,
	 * as the duration of this method is assumed to be within milliseconds.
	 * The effort of implementing a key to lock-object mapping (as alternative) is expected to be rather high
	 * and would imply the risk of memory leaks. 
	 */
	public <P, R> Mono<P> performGenericRetrieval(String retrievalTypeName, String logName, String key, R requestData,
			Function<R, Mono<P>> requestFunction, int timeoutInMS) {
		final String lock = (this.getClass().getCanonicalName()+"|"+retrievalTypeName+"|"+key).intern();
		
		synchronized (lock) {
			Mono<P> result = null;

			ReactiveTimer reactiveTimer = new ReactiveTimer(this.internalMetrics, retrievalTypeName);

			result = Mono.just(requestData)
					// start the timer
					.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
						tuple.getT2().start();
						return tuple.getT1();
					}).flatMap(requestFunction).timeout(Duration.ofMillis(timeoutInMS)).retry(2)
					.doOnError(throwable -> {
						Throwable unwrappedThrowable = Exceptions.unwrap(throwable);
						if (unwrappedThrowable instanceof TimeoutException) {
							log.error(String.format(
									"Async retrieval of %s with key %s caused a timeout after %dms even though we tried three times",
									logName, key, timeoutInMS));
						} else {
							log.error(String.format("Async retrieval of %s with key %s raised a reactor error", logName,
									key), unwrappedThrowable);
						}
					})
					// stop the timer
					.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
						tuple.getT2().stop();
						return tuple.getT1();
					}).log(log.getName() + "." + logName, Level.FINE).cache();

			return result;
		}
	}

	/**
	 * performs a retrieval from the CF Cloud Controller fetching all pages
	 * available.
	 * 
	 * @param retrievalTypeName
	 *            the name of type of the request which is being made; used for
	 *            identification in internalMetrics
	 * @param logName
	 *            the name of the logger category, which shall be used for logging
	 *            this Reactor operation
	 * @param key
	 *            the key for which the request is being made (e.g. orgId,
	 *            orgId|spaceName, ...)
	 * @param requestGenerator
	 *            a request generator function, which permits creating request
	 *            objects instance for a given set of page parameters (e.g. for
	 *            which page, using which page size, ...)
	 * @param requestFunction
	 *            a function which calls the CF API operation, which is being made.
	 * @param timeoutInMS
	 *            the timeout value in milliseconds for a single data request to the
	 *            CF Cloud Controller
	 * @param responseGenerator
	 *            a response generator function, which permits creating a response
	 *            object, which contains the collected resources of all pages
	 *            retrieved.
	 * @return a Mono on the response provided by the CF Cloud Controller
	 */
	public <S, P extends PaginatedResponse<?>, R extends PaginatedRequest> Mono<P> performGenericPagedRetrieval(
			String retrievalTypeName, String logName, String key, PaginatedRequestGeneratorFunction<R> requestGenerator,
			Function<R, Mono<P>> requestFunction, int timeoutInMS,
			PaginatedResponseGeneratorFunction<S, P> responseGenerator) {

		final String pageRetrievalType = retrievalTypeName + "_singlePage";

		ReactiveTimer reactiveTimer = new ReactiveTimer(this.internalMetrics, retrievalTypeName);

		Mono<P> firstPage = Mono.just(reactiveTimer).doOnNext(timer -> {
			timer.start();
		}).flatMap(dummy -> {
			return this.performGenericRetrieval(pageRetrievalType, logName, key,
					requestGenerator.apply(OrderDirection.ASCENDING, RESULTS_PER_PAGE, 1), requestFunction,
					timeoutInMS);
		});

		Flux<R> requestFlux = firstPage.map(page -> page.getTotalPages() - 1)
				.flatMapMany(pagesCount -> Flux.range(2, pagesCount))
				.map(pageNumber -> requestGenerator.apply(OrderDirection.ASCENDING, RESULTS_PER_PAGE, pageNumber));

		Mono<List<P>> subsequentPagesList = requestFlux.flatMap(req -> {
			return this.performGenericRetrieval(pageRetrievalType, logName, key, req, requestFunction, timeoutInMS);
		}).collectList();
		/*
		 * Word on error handling: We can't judge here what will be the consequence, if
		 * the first page could be retrieved properly, but retrieving some some later
		 * page fails. The implication would depend on the consumer (whether incomplete
		 * data was ok or not). So the safe answer here is to raise an error for the
		 * entire request. That, however, is already in place with the error handling in
		 * performGenericRetrieval: the stream is already in state "error" and thus will
		 * not emit any item.
		 */

		return Mono.zip(firstPage, subsequentPagesList, Mono.just(reactiveTimer)).map(tuple -> {
			P first = tuple.getT1();
			List<P> subsequent = tuple.getT2();

			List<S> ret = new LinkedList<>();

			ret.addAll((List<? extends S>) first.getResources());
			for (P listResponse : subsequent) {
				ret.addAll((List<? extends S>) listResponse.getResources());
			}

			P retObject = responseGenerator.apply(ret, subsequent.size() + 1);

			tuple.getT3().stop();

			return retObject;
		});
	}
}
