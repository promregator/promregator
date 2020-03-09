package org.cloudfoundry.promregator.scanner;

import java.util.List;

public class IncompleteResultException extends RuntimeException {
    private final int failedRequests;
    private final List results;

    public IncompleteResultException(int failedRequests, List results) {

        super("Failed to collect complete result. Requests not completed: "+failedRequests);
        this.failedRequests = failedRequests;
        this.results = results;
    }

    public int getFailedRequests() {
        return failedRequests;
    }


    public List getResults() {
        return results;
    }
}
