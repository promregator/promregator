package org.cloudfoundry.promregator.fetcher;

import java.util.HashMap;
import java.util.concurrent.Callable;

import io.prometheus.client.Collector.MetricFamilySamples;

/**
 * A MetricsFetcher is some interface which implements a Callable, which returns
 * a HashMap which maps Metric identifiers (e.g. Strings) to MetricFamilySamples.
 * There is no additional requirement besides that it needs to return such a map.
 * The approach of how the data is being scraped (via HTTP, or via some other technical
 * means) is not defined.
 *
 */
public interface MetricsFetcher extends Callable<HashMap<String, MetricFamilySamples>>{

}
