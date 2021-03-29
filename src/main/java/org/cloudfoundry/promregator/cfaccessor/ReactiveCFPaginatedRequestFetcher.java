package org.cloudfoundry.promregator.cfaccessor;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.Level;

import org.cloudfoundry.client.v2.OrderDirection;
import org.cloudfoundry.client.v2.PaginatedRequest;
import org.cloudfoundry.client.v2.PaginatedResponse;
import org.cloudfoundry.promregator.ExitCodes;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.RateLimiter;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

class ReactiveCFPaginatedRequestFetcher {
	private static final Logger log = LoggerFactory.getLogger(ReactiveCFPaginatedRequestFetcher.class);

	private static final int MAX_SUPPORTED_RESULTS_PER_PAGE = 100;
	private static final int RESULTS_PER_PAGE = MAX_SUPPORTED_RESULTS_PER_PAGE;

	private InternalMetrics internalMetrics;

	private final RateLimiter cfccRateLimiter;

	private final Duration initialBackoffDelay;

	public ReactiveCFPaginatedRequestFetcher(InternalMetrics internalMetrics, double requestRateLimit, Duration backoffDelay) {
		super();
		this.internalMetrics = internalMetrics;
		this.initialBackoffDelay = backoffDelay;

		if (requestRateLimit <= 0.0f) {
			this.cfccRateLimiter = RateLimiter.create(Double.POSITIVE_INFINITY);
		}
		else {
			this.cfccRateLimiter = RateLimiter.create(requestRateLimit);
		}
	}

	/**
	 * Returns an empty Mono, which is only resolved after the configured rate limit could be acquired.
	 *
	 * @param requestType
	 * 	the RequestType for which the rate limiting shall be acquired (mainly for statistical purpose only)
	 *
	 * @return an empty Mono
	 */
	private Mono<Object> rateLimitingMono(RequestType requestType) {
		return Mono.fromCallable(() -> {

			if (this.internalMetrics != null) {
				this.internalMetrics.increaseRateLimitQueueSize();
			}

			double waitTime = cfccRateLimiter.acquire(1);

			if (waitTime > 0.001) {
				log.debug(String.format("Rate Limiting has throttled request of %s for %.3f seconds", requestType.getLoggerSuffix(), waitTime));
			}

			if (this.internalMetrics != null) {
				this.internalMetrics.decreaseRateLimitQueueSize();
				this.internalMetrics.observeRateLimiterDuration(requestType.getMetricName(), waitTime);
			}

			return new Object();
		}).subscribeOn(Schedulers.boundedElastic())
				   .flatMap(x -> Mono.empty());
	}


	/**
	 * performs standard (raw) retrieval from the CF Cloud Controller of a single page
	 *
	 * @param requestType
	 * 	the type information of the request which is being made
	 * @param key
	 * 	the key for which the request is being made (e.g. orgId, orgId|spaceName, ...)
	 * @param requestData
	 * 	an object which is being used as input parameter for the request
	 * @param requestFunction
	 * 	a function which calls the CF API operation, which is being made,
	 * 	<code>requestData</code> is used as input parameter for this
	 * 	function.
	 * @param timeoutInMS
	 * 	the timeout value in milliseconds for a single data request to the CF Cloud Controller
	 *
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
	public <P, R> Mono<P> performGenericRetrieval(RequestType requestType, String key, R requestData,
												  Function<R, Mono<P>> requestFunction, int timeoutInMS) {
		final String retrievalTypeName = requestType.getMetricName();
		final String logName = requestType.getLoggerSuffix();

		final String lock = (this.getClass().getCanonicalName() + "|" + retrievalTypeName + "|" + key).intern();

		synchronized (lock) {
			Mono<P> result = null;

			ReactiveTimer reactiveTimer = new ReactiveTimer(this.internalMetrics, retrievalTypeName);

			final Mono<P> enrichedRequestFunction = requestFunction.apply(requestData)
																   .timeout(Duration.ofMillis(timeoutInMS));
			/*
			 * Note 1: Applying (i.e. calling) the function "requestFunction" here
			 * does not trigger the request to be sent to the CFCC.
			 * Instead, it just will create the corresponding Flux/Mono, which does
			 * not have any subscriber yet.
			 *
			 * Note 2: There is a major difference between the coding modeled
			 *
			 * requestFunction.apply(requestData).timeout(...)
			 *
			 * and
			 *
			 * someMono.flatMap(value -> requestFunction).timeout(...)
			 *
			 * The major point here is that the first variant applies the timeout
			 * only to the stream returned by requestFunction.apply(...), whilst
			 * the second variant applies it to
			 * 1. someMono,
			 * 2. the flatMap function
			 * 3. the return value of the requestFunction
			 *
			 * The difference there is that in the second variant counting
			 * for the timeout starts already when someMono is subscribed to.
			 * In the second variant, someMono is not considered.
			 *
			 * In this case here, the difference may be huge: The first variant
			 * puts a timeout on each request (which is what we want). The
			 * second variant means that timeout would be counting from the
			 * first subscription happening - which is wrong especially in case
			 * of retry attempts.
			 */

			result = this.rateLimitingMono(requestType).then(Mono.just(reactiveTimer))
						 // start the timer
						 .flatMap(timer -> {
							 timer.start();
							 return Mono.just(0 /* any value will just do; will be ignored */); // Cannot use Mono.empty() here!
						 }).flatMap(nothing -> enrichedRequestFunction)
						 .retryWhen(Retry.backoff(2, this.initialBackoffDelay))
						 /*
						  * Note: Don't push the retry attempts above into enrichedRequestFunction!
						  * It would change the semantics of the metric behind the timer.
						  * see also https://github.com/promregator/promregator/pull/174/files#r392031592
						  */
						 .doOnError(throwable -> {
							 Throwable unwrappedThrowable = Exceptions.unwrap(throwable);
							 if (unwrappedThrowable instanceof TimeoutException) {
								 log.error(String.format(
									 "Async retrieval of %s with key %s caused a timeout after %dms even though we tried three times",
									 logName, key, timeoutInMS));
							 }
							 else if (unwrappedThrowable instanceof OutOfMemoryError) {
								 // This may be an direct memory or a heap error!
								 // Using String.format and/or log.error here is a bad idea - it takes memory!

								 if (System.getenv("VCAP_APPLICATION") != null) {
									 // we assume that we are running on a Cloud Foundry container
									 this.triggerOutOfMemoryRestart();
								 }

							 }
							 else {
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

	@SuppressFBWarnings(value = "DM_EXIT", justification = "Restart of JVM is done intentionally here!")
	private void triggerOutOfMemoryRestart() {
		System.err.println("Out of Memory situation detected on talking to the Cloud Foundry Controller; restarting application");
		System.exit(ExitCodes.CF_ACCESSOR_OUT_OF_MEMORY);
	}

	/**
	 * performs a retrieval from the CF Cloud Controller fetching all pages available.
	 *
	 * @param requestType
	 * 	the type information of the request which is being made
	 * @param key
	 * 	the key for which the request is being made (e.g. orgId, orgId|spaceName, ...)
	 * @param requestGenerator
	 * 	a request generator function, which permits creating request objects instance for a given set of page parameters (e.g. for which page, using
	 * 	which page size, ...)
	 * @param requestFunction
	 * 	a function which calls the CF API operation, which is being made.
	 * @param timeoutInMS
	 * 	the timeout value in milliseconds for a single data request to the CF Cloud Controller
	 * @param responseGenerator
	 * 	a response generator function, which permits creating a response object, which contains the collected resources of all pages retrieved.
	 *
	 * @return a Mono on the response provided by the CF Cloud Controller
	 */
	public <S, P extends PaginatedResponse<?>, R extends PaginatedRequest> Mono<P> performGenericPagedRetrieval(
		RequestType requestType, String key, PaginatedRequestGeneratorFunction<R> requestGenerator,
		Function<R, Mono<P>> requestFunction, int timeoutInMS,
		PaginatedResponseGeneratorFunction<S, P> responseGenerator) {

		final String pageRetrievalType = requestType.getMetricName() + "_singlePage";

		ReactiveTimer reactiveTimer = new ReactiveTimer(this.internalMetrics, pageRetrievalType);

		Mono<P> firstPage = Mono.just(reactiveTimer).doOnNext(ReactiveTimer::start).flatMap(dummy ->
																								this.performGenericRetrieval(requestType, key, requestGenerator
																									.
																										apply(OrderDirection.ASCENDING, RESULTS_PER_PAGE, 1), requestFunction, timeoutInMS));

		Flux<R> requestFlux = firstPage.map(page -> page.getTotalPages() - 1)
									   .flatMapMany(pagesCount -> Flux.range(2, pagesCount))
									   .map(pageNumber -> requestGenerator.apply(OrderDirection.ASCENDING, RESULTS_PER_PAGE, pageNumber));

		Mono<List<P>> subsequentPagesList = requestFlux.flatMap(req ->
																	this.performGenericRetrieval(requestType, key, req, requestFunction, timeoutInMS))
													   .collectList();

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

	/**
	 * performs a retrieval from the CF Cloud Controller fetching all pages available.
	 *
	 * @param requestType
	 * 	the type information of the request which is being made
	 * @param key
	 * 	the key for which the request is being made (e.g. orgId, orgId|spaceName, ...)
	 * @param requestGenerator
	 * 	a request generator function, which permits creating request objects instance for a given set of page parameters (e.g. for which page, using
	 * 	which page size, ...)
	 * @param requestFunction
	 * 	a function which calls the CF API operation, which is being made.
	 * @param timeoutInMS
	 * 	the timeout value in milliseconds for a single data request to the CF Cloud Controller
	 * @param responseGenerator
	 * 	a response generator function, which permits creating a response object, which contains the collected resources of all pages retrieved.
	 *
	 * @return a Mono on the response provided by the CF Cloud Controller
	 */
	public <S, P extends org.cloudfoundry.client.v3.PaginatedResponse<?>, R extends org.cloudfoundry.client.v3.PaginatedRequest> Mono<P> performGenericPagedRetrievalV3(
		RequestType requestType, String key, PaginatedRequestGeneratorFunctionV3<R> requestGenerator,
		Function<R, Mono<P>> requestFunction, int timeoutInMS,
		PaginatedResponseGeneratorFunctionV3<S, P> responseGenerator) {

		final String pageRetrievalType = requestType.getMetricName() + "_singlePage";

		ReactiveTimer reactiveTimer = new ReactiveTimer(this.internalMetrics, pageRetrievalType);

		Mono<P> firstPage = Mono.just(reactiveTimer).doOnNext(ReactiveTimer::start).flatMap(dummy ->
																								this.performGenericRetrieval(requestType, key, requestGenerator
																									.
																										apply(RESULTS_PER_PAGE, 1), requestFunction, timeoutInMS));

		Flux<R> requestFlux = firstPage.map(page -> page.getPagination().getTotalPages() - 1)
									   .flatMapMany(pagesCount -> Flux.range(2, pagesCount))
									   .map(pageNumber -> requestGenerator.apply(RESULTS_PER_PAGE, pageNumber));

		Mono<List<P>> subsequentPagesList = requestFlux.flatMap(req ->
																	this.performGenericRetrieval(requestType, key, req, requestFunction, timeoutInMS))
													   .collectList();

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
