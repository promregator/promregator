package org.cloudfoundry.promregator.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;


public class BackPressureOps<T> implements Function<Mono<List<T>>, Mono<List<T>>> {
    private static Logger LOG = LoggerFactory.getLogger(BackPressureOps.class);
    private final int maxRetries;
    private final int maxRequests;

    public BackPressureOps(int maxRetries, int maxRequests) {
        this.maxRetries = maxRetries;
        this.maxRequests = maxRequests;
    }

    @Override
    public Mono<List<T>> apply(Mono<List<T>> flux) {
        return flux.flatMap(results -> Mono.subscriberContext().map(ctx -> {
            BackPressure bp = ctx.get("backpressure");
            int skips = bp.getRequestsSkipped();
            LOG.info("Got results: " + results.size() + " backpressue: " + skips);
            if (skips > 0) {
                bp.reset();
                throw new IncompleteResultException(skips, results);
            }

            return results;
        }))
                .retryBackoff(maxRetries, Duration.ofSeconds(1), Duration.ofSeconds(10))
                .onErrorResume(t -> {
                    Throwable rootCause = t;
                    if(t.getCause() instanceof IncompleteResultException) {
                        rootCause = t.getCause();
                    }

                    if (rootCause instanceof IncompleteResultException) {
                        @SuppressWarnings("unchecked") Mono<List<T>> result = Mono.just(((IncompleteResultException) rootCause).getResults());
                        return result;
                    } else {
                        return Mono.error(t);
                    }
                })
                .subscriberContext(Context.of("backpressure", new BackPressure(maxRequests)));
    }

}
