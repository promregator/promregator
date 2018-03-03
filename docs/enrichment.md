# Enrichment performed by Promregator

Promregator automatically enriches the metrics provided by the targets with additional labels.
Moreover, it also provides additional metrics to support you.

In general, there are three cases where additional metrics/labels may appear:

* Additional labels for provided metrics by the targets
* Additional metrics measuring Promregator itself
* Additional metrics measuring the communication to the targets.


## Additional Labels for Provided Metrics by the Targets
The following labels are automatically added to metrics, which are received from targets:

* The name of the Cloud Foundry organization in which the application is running (`org_name`)
* The name of the Cloud Foundry space in which the application is running (`space_name`)
* The name of the Cloud Foundry application which is running (`app_name`)
* The instance identifier (GUID of the application plus its instance number separated by a colon) from where the data was fetched (`instance`).

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
metric_without_label{org_name="cforg",space_name="cfspace",app_name="cfapp",instance="707d58b2-00ce-435a-845c-79eff28afe8c:0"} 1.0
metric_with_label{mylabel="myvalue",org_name="cforg",space_name="cfspace",app_name="cfapp",instance="707d58b2-00ce-435a-845c-79eff28afe8c:0"} 2.0
```
By this, you may aggregate the metrics data in your Prometheus server according to your needs, allowing any drilldown you wish, even to the lowest level of a single instance.

Note that there is no label `promregator` added to these metrics.


## Additional Metrics Measuring Promregator Itself



## Additional Metrics Measuring the Communication to the Targets

