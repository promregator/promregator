# Annotation-Driven Configuration

Starting with version 0.9.0 of Promregator, annotation-driven configuration of targets is supported.

Configuration follows the approach suggested by [cf-for-k8s](https://github.com/cloudfoundry/cf-for-k8s-metric-examples). See also that page fro examples how to set annotations.

These annotations are supported:

| Annotation | supported | Comments |
|------------|-----------|----------|
| `prometheus.io/scrape` | yes | must be set to `"true"` |
| `prometheus.io/path` | yes | overwrites any other `path` attribute in Promregator's main configuration, if provided |
| `prometheus.io/port` | no | |

For the metadata to be evaluated, the corresponding target needs to have set the attribute `kubernetesAnnotations` to `true` (see also details at the [configuration option page](./config.md) for this option). Note that if this option is set, any target which does *not* provide the necessary metadata annotations will not be selected for scraping (but ignored).

This provides the opportunity for an "opt-in" to scraping by applications without the need of modifying Promregator's configuration: If another application wants to get scraped, it may set `prometheus.io/scrape` to `true` (and provide a `prometheus.io/path` if applicable). On the next metadata update, the new application will be detected automatically and added to discovery.

## Example application

An example Cloud Foundry application using these annotations would be deployed
using a sample manifest like below:

```yaml
---
applications:
- name: go-app-with-metrics
  metadata:
    annotations:
      prometheus.io/scrape: "true"
      prometheus.io/path: "/not/default/metrics"
```

Given this configuration, the app would be scraped by promregator and the path
for scraping would be `/not/default/metrics`.
