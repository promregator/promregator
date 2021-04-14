# Annotation-Driven Configuration

Starting with version 0.9.0 of Promregator, annotation-driven configuration of targets is supported if the underlying Cloud Foundry platform supports the CAPI V3 interface. Note that in these days the latter should not be a challenging requirement given the fact that the former interface, CAPI V2, has been deprecated.

Configuration follows the approach suggested by [cf-for-k8s](https://github.com/cloudfoundry/cf-for-k8s-metric-examples). See also that page fro examples how to set annotations. 

These annotations are supported:

| Annotation | supported | Comments |
|------------|-----------|----------|
| `prometheus.io/scrape` | yes | must be set to `"true"` |
| `prometheus.io/path` | yes | overwrites any other `path` attribute in Promregator's main configuration, if provided |
| `prometheus.io/port` | no | |

For the metadata to be evaluated, the corresponding target needs to have set the attribute `kubernetesAnnotations` to `true` (see also details at the [configuration option page](./config.md) for this option). Note that if this option is set, any target which does *not* provide the necessary metadata annotations will not be selected for scraping (but ignored).

