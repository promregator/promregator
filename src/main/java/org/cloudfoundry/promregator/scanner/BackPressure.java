package org.cloudfoundry.promregator.scanner;

import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class BackPressure {
    private static final Logger log = LoggerFactory.getLogger(BackPressure.class);
    private final AtomicReference<Instant> startTime = new AtomicReference<>(Instant.now());
    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final Integer maxRequests;
    private final AtomicInteger requestSkipped = new AtomicInteger(0);

    public BackPressure(Integer maxRequests) {
        this.maxRequests = maxRequests;
    }

    public Duration runDuration(){
        return Duration.between(startTime.get(), Instant.now());
    }

    public int incrementRequests() {
        return totalRequests.incrementAndGet();
    }

    public <T> Mono<T> request(Supplier<Mono<T>> monoRequest) {
        int totalRequest = incrementRequests();
        if(totalRequest < maxRequests){
            return monoRequest.get();
        } else {
            requestSkipped.incrementAndGet();
            log.info("Skipping mono total: {} max: {}", totalRequest, maxRequests);
            return Mono.empty();
        }
    }

    public int getRequestsSkipped(){
        return requestSkipped.get();
    }

    public void reset() {
        totalRequests.set(0);
        startTime.set(Instant.now());
    }

    @Override
    public String toString() {
        return "BackPressure{" +
                "startTime=" + startTime +
                ", totalRequests=" + totalRequests +
                ", maxRequests=" + maxRequests +
                ", requestSkipped=" + requestSkipped +
                '}';
    }
}
