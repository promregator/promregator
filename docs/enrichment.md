# Enrichment performed by Promregator

Promregator may enrich the scraping results for you. It does so by providing additional metrics.

In general, there are two cases where additional metrics may appear:

* Additional metrics measuring the communication to the targets.
* Additional metrics measuring Promregator itself.



## Additional Metrics Measuring the Communication to the Targets

Promregator monitors the connectivity to the targets. The result of the monitoring also made available to
the caller via Prometheus metrics. For this, the following metrics are exposed:

* `promregator_request_latency`: a [Prometheus histogram](https://prometheus.io/docs/practices/histograms/), 
  which returns the latency which was necessary to retrieve the metrics from the target.
  Note that this metric is disabled by default due to high data volume. You may enable it with [configuration option](./config.md) `promregator.metrics.requestLatency`.
* `promregator_request_size`: a [Prometheus histogram](https://prometheus.io/docs/practices/histograms/), which returns the size of the scraping document, which was sent from the target to Promregator.
* `promregator_up`: a [Prometheus Gauge](https://prometheus.io/docs/concepts/metric_types/) which indicates whether an instance was reachable or not (similar to the [gauge provided for Prometheus' own monitoring](https://prometheus.io/docs/concepts/jobs_instances/)).
* `promregator_request_failure`: a [Prometheus Gauge](https://prometheus.io/docs/concepts/metric_types/) which indicates the number of requests sent to the target, which have failed.
* `promregator_scrape_duration_seconds`: a [Prometheus Gauge](https://prometheus.io/docs/concepts/metric_types/) which indicates how long scraping of the current request target took. This metric is dependent on the chosen target and thus provides the labels `org_name`, `space_name`, `app_name`, `cf_instance_number` and `cf_instance_id` (as stated below).


Here is an example how such metric samples may look like:
```
# HELP promregator_up Indicator, whether the target of promregator is available
# TYPE promregator_up gauge
promregator_up 1.0

# HELP promregator_request_latency The latency, which the targets of the promregator produce
# TYPE promregator_request_latency histogram
promregator_request_latency_bucket{le="0.005"} 0.0
promregator_request_latency_bucket{le="0.01"} 0.0
promregator_request_latency_bucket{le="0.025"} 0.0
promregator_request_latency_bucket{le="0.05"} 0.0
promregator_request_latency_bucket{le="0.075"} 0.0
promregator_request_latency_bucket{le="0.1"} 0.0
promregator_request_latency_bucket{le="0.25"} 1.0
promregator_request_latency_bucket{le="0.5"} 1.0
promregator_request_latency_bucket{le="0.75"} 1.0
promregator_request_latency_bucket{le="1.0"} 1.0
promregator_request_latency_bucket{le="2.5"} 1.0
promregator_request_latency_bucket{le="5.0"} 1.0
promregator_request_latency_bucket{le="7.5"} 1.0
promregator_request_latency_bucket{le="10.0"} 1.0
promregator_request_latency_bucket{le="+Inf"} 1.0
promregator_request_latency_count 1.0
promregator_request_latency_sum 0.21851916
```


## Additional Metrics Measuring Promregator Itself

The metrics provided by Promregator also supports you in monitoring Promregator itself. 
Due to this, additional metrics, such as the [Hotspot's (Java Virtual Machine) metrics](https://github.com/prometheus/client_java) are also exposed to the caller.

Note that, as these metrics also are originated in Promregator, they are always prefixed with `promregator_`. 

See here some examples, how this may look like:
```
# HELP promregator_jvm_memory_pool_bytes_max Max bytes of a given JVM memory pool.
# TYPE promregator_jvm_memory_pool_bytes_max gauge
promregator_jvm_memory_pool_bytes_max{pool="Code Cache"} 2.5165824E8
promregator_jvm_memory_pool_bytes_max{pool="Metaspace"} -1.0
promregator_jvm_memory_pool_bytes_max{pool="Compressed Class Space"} 1.073741824E9
promregator_jvm_memory_pool_bytes_max{pool="PS Eden Space"} 1.358430208E9
```

By default, only the metrics of the the Java Hotspot are exposed like this. Using the [configuration option `promregator.metrics.internal`](./config.md) further internal metrics may be enabled.

