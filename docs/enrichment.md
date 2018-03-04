# Enrichment performed by Promregator

Promregator automatically enriches the metrics provided by the targets with additional labels.
Moreover, it also provides additional metrics to support you.

In general, there are three cases where additional metrics/labels may appear:

* Additional labels for provided metrics by the targets.
* Additional metrics measuring the communication to the targets.
* Additional metrics measuring Promregator itself.


## Additional Labels for Provided Metrics by the Targets
The following labels are automatically added to metrics, which are received from targets:

* The name of the Cloud Foundry organization in which the application is running (`org_name`)
* The name of the Cloud Foundry space in which the application is running (`space_name`)
* The name of the Cloud Foundry application which is running (`app_name`)
* The instance identifier (GUID of the application plus its instance number separated by a colon) from where the data was fetched (`cf_instance_id`).
* The instance number from where the data was fetched (`cf_instance_number`).

For you this means that assuming that a target, 

* which is running in CF org `cforg`, 
* CF space `cfspace` 
* as application called `cfapp`,
* having GUID `707d58b2-00ce-435a-845c-79eff28afe8c`
* and running as the first instance

exposing the following two metric samples
```
metric_without_label 1.0
metric_with_label{mylabel="myvalue"} 2.0
```
Promregator returns the following metrics (amongst other) to the caller:
```
metric_without_label{org_name="cforg",space_name="cfspace",app_name="cfapp",cf_instance_id="707d58b2-00ce-435a-845c-79eff28afe8c:0",cf_instance_number="0"} 1.0
metric_with_label{mylabel="myvalue",org_name="cforg",space_name="cfspace",app_name="cfapp",cf_instance_id="707d58b2-00ce-435a-845c-79eff28afe8c:0",cf_instance_number="0"} 2.0
```
By this, you may aggregate the metrics data in your Prometheus server according to your needs, allowing any drilldown you wish, even to the lowest level of a single instance.


## Additional Metrics Measuring the Communication to the Targets

Promregator also monitors the connectivity to the targets. The result of the monitoring also made available to
the caller via Prometheus metrics. For this, the following metrics are exposed:

* `promregator_request_latency`: a [Prometheus histogram](https://prometheus.io/docs/practices/histograms/), 
  which returns the latency which was necessary to retrieve the metrics from the target.
* `promregator_up`: a [Prometheus Gauge](https://prometheus.io/docs/concepts/metric_types/) which indicates whether an instance was reachable or not (similar to the [gauge provided for Prometheus' own monitoring](https://prometheus.io/docs/concepts/jobs_instances/)).
* `promregator_request_failure`: a [Prometheus Gauge](https://prometheus.io/docs/concepts/metric_types/) which indicates the number of requests sent to the target, which have failed.

Note that additionally to the labels `org_name`, `space_name`, `app_name`, `cf_instance_number` and `cf_instance_id` (which tells you the target, which the metric is referring to), the metric name is prefixed with `promregator_` indicating that the value of the metric sample was created by Promregator itself and is not originated by any of the targets.

Here is an example how such metric samples may look like:
```
# HELP promregator_up Indicator, whether the target of promregator is available
# TYPE promregator_up gauge
promregator_up{org_name="cforg",space_name="dev",app_name="testapp2",cf_instance_id="9897cda1-2673-4d75-adf2-3132eea90873:0",cf_instance_number="0",} 1.0
promregator_up{org_name="cforg",space_name="dev",app_name="testapp",cf_instance_id="262ec022-8366-4c49-ac13-f50b35a78154:0",cf_instance_number="0",} 0.0

# HELP promregator_request_latency The latency, which the targets of the promregator produce
# TYPE promregator_request_latency histogram
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp",cf_instance_id="262ec022-8366-4c49-ac13-f50b35a78154:0",le="0.005",cf_instance_number="0"} 0.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp",cf_instance_id="262ec022-8366-4c49-ac13-f50b35a78154:0",le="0.01",cf_instance_number="0"} 0.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp",cf_instance_id="262ec022-8366-4c49-ac13-f50b35a78154:0",le="0.025",cf_instance_number="0"} 0.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp",cf_instance_id="262ec022-8366-4c49-ac13-f50b35a78154:0",le="0.05",cf_instance_number="0"} 0.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp",cf_instance_id="262ec022-8366-4c49-ac13-f50b35a78154:0",le="0.075",cf_instance_number="0"} 0.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp",cf_instance_id="262ec022-8366-4c49-ac13-f50b35a78154:0",le="0.1",cf_instance_number="0"} 0.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp",cf_instance_id="262ec022-8366-4c49-ac13-f50b35a78154:0",le="0.25",cf_instance_number="0"} 1.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp",cf_instance_id="262ec022-8366-4c49-ac13-f50b35a78154:0",le="0.5",cf_instance_number="0"} 1.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp",cf_instance_id="262ec022-8366-4c49-ac13-f50b35a78154:0",le="0.75",cf_instance_number="0"} 1.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp",cf_instance_id="262ec022-8366-4c49-ac13-f50b35a78154:0",le="1.0",cf_instance_number="0"} 1.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp",cf_instance_id="262ec022-8366-4c49-ac13-f50b35a78154:0",le="2.5",cf_instance_number="0"} 1.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp",cf_instance_id="262ec022-8366-4c49-ac13-f50b35a78154:0",le="5.0",cf_instance_number="0"} 1.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp",cf_instance_id="262ec022-8366-4c49-ac13-f50b35a78154:0",le="7.5",cf_instance_number="0"} 1.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp",cf_instance_id="262ec022-8366-4c49-ac13-f50b35a78154:0",le="10.0",cf_instance_number="0"} 1.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp",cf_instance_id="262ec022-8366-4c49-ac13-f50b35a78154:0",le="+Inf",cf_instance_number="0"} 1.0
promregator_request_latency_count{org_name="cforg",space_name="dev",app_name="testapp",cf_instance_id="262ec022-8366-4c49-ac13-f50b35a78154:0",cf_instance_number="0"} 1.0
promregator_request_latency_sum{org_name="cforg",space_name="dev",app_name="testapp",cf_instance_id="262ec022-8366-4c49-ac13-f50b35a78154:0",cf_instance_number="0"} 0.21851916
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp2",cf_instance_id="9897cda1-2673-4d75-adf2-3132eea90873:0",le="0.005",cf_instance_number="0"} 0.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp2",cf_instance_id="9897cda1-2673-4d75-adf2-3132eea90873:0",le="0.01",cf_instance_number="0"} 0.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp2",cf_instance_id="9897cda1-2673-4d75-adf2-3132eea90873:0",le="0.025",cf_instance_number="0"} 0.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp2",cf_instance_id="9897cda1-2673-4d75-adf2-3132eea90873:0",le="0.05",cf_instance_number="0"} 0.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp2",cf_instance_id="9897cda1-2673-4d75-adf2-3132eea90873:0",le="0.075",cf_instance_number="0"} 0.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp2",cf_instance_id="9897cda1-2673-4d75-adf2-3132eea90873:0",le="0.1",cf_instance_number="0"} 0.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp2",cf_instance_id="9897cda1-2673-4d75-adf2-3132eea90873:0",le="0.25",cf_instance_number="0"} 1.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp2",cf_instance_id="9897cda1-2673-4d75-adf2-3132eea90873:0",le="0.5",cf_instance_number="0"} 1.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp2",cf_instance_id="9897cda1-2673-4d75-adf2-3132eea90873:0",le="0.75",cf_instance_number="0"} 1.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp2",cf_instance_id="9897cda1-2673-4d75-adf2-3132eea90873:0",le="1.0",cf_instance_number="0"} 1.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp2",cf_instance_id="9897cda1-2673-4d75-adf2-3132eea90873:0",le="2.5",cf_instance_number="0"} 1.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp2",cf_instance_id="9897cda1-2673-4d75-adf2-3132eea90873:0",le="5.0",cf_instance_number="0"} 1.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp2",cf_instance_id="9897cda1-2673-4d75-adf2-3132eea90873:0",le="7.5",cf_instance_number="0"} 1.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp2",cf_instance_id="9897cda1-2673-4d75-adf2-3132eea90873:0",le="10.0",cf_instance_number="0"} 1.0
promregator_request_latency_bucket{org_name="cforg",space_name="dev",app_name="testapp2",cf_instance_id="9897cda1-2673-4d75-adf2-3132eea90873:0",le="+Inf",cf_instance_number="0"} 1.0
promregator_request_latency_count{org_name="cforg",space_name="dev",app_name="testapp2",cf_instance_id="9897cda1-2673-4d75-adf2-3132eea90873:0",cf_instance_number="0"} 1.0
promregator_request_latency_sum{org_name="cforg",space_name="dev",app_name="testapp2",cf_instance_id="9897cda1-2673-4d75-adf2-3132eea90873:0",cf_instance_number="0"} 0.218502344
```


## Additional Metrics Measuring Promregator Itself

The metrics provided by Promregator also supports you in monitoring Promregator itself. 
Due to this, additional metrics, such as the [Hotspot's (Java Virtual Machine) metrics](https://github.com/prometheus/client_java) are also exposed to the caller.

Note that, as these metrics also are originated in Promregator, they are always prefixed with `promregator_`. 

See here some examples, how this may look like:
```
# HELP promregator_jvm_memory_pool_bytes_max Max bytes of a given JVM memory pool.
# TYPE promregator_jvm_memory_pool_bytes_max gauge
promregator_jvm_memory_pool_bytes_max{pool="Code Cache",} 2.5165824E8
promregator_jvm_memory_pool_bytes_max{pool="Metaspace",} -1.0
promregator_jvm_memory_pool_bytes_max{pool="Compressed Class Space",} 1.073741824E9
promregator_jvm_memory_pool_bytes_max{pool="PS Eden Space",} 1.358430208E9
```

By default, only the metrics of the the Java Hotspot are exposed like this. Using the [configuration option `promregator.metrics.internal`](./config.md) further internal metrics may be enabled.

