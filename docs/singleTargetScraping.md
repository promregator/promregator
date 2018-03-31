# Scraping using Separate Service Discovery and Single Target Scraping Endpoints

Motivated by the approach taken by [PushProxy](https://github.com/RobustPerception/PushProx), Promregator also supports the mode of Single Target Scraping since version 0.2.0. The general idea employed by PushProx is to separate the endpoint responsible for service discovery (in case of Promregator, this is the `/discovery` endpoint), which provides a directory for proxying endpoints for each target to be scraped (in our case: CF app instances; these endpoints start with `/singleTargetMetrics`).

On first glance it looks very easy to apply the same pattern also to Promregator. However, the approach - to our analysis - has quite some pros and cons, which are being discussed here on this page.

## General setup
In general the logic looks like this:

1. Promregator performs the service discovery and exposes the result in the appropriate `file_sd_configs` JSON format via an endpoint called `/discovery`.
2. The content of the endpoint has to be fetched regularly (for example via `wget` or `curl` running in the same environment as Prometheus) and stored in a file which is registered with `file_sd_configs`. As the file-based discovery service has auto-reloading of it discovery file, Prometheus' scraping targets may be updated automatically.
3. The configuration file contains pseudo-URLs pointing to endpoints provided by Promregator, which contain unique identifiers to determine for which CF App Instance scraping is requested.
4. Based on the unique identifier in the scraping request, the scraping request is forwarded (like a proxy) to the corresponding CF App instance (e.g. using the [X-CF-APP-INSTANCE header approach](https://docs.cloudfoundry.org/concepts/http-routing.html#app-instance-routing)).

## Drawbacks of this approach

Yet, on second glance, this kind of approach has four major drawbacks:

* Employing the Single Endpoint Scraping approach used by Promregator, Prometheus only needs to send a single HTTP request to retrieve a complete set of data. With the approach depicted above, scraping `n` CF App Instances requires `n + 1` HTTP requests between Prometheus and Promregator (`n` for each instance one, which are then proxied, `+1` for the service discovery request, fetching the `file_sd_configs` JSON file). Given the fact that for each request additional overhead costs (for network, sockets, transport, establishing communication), this will have a negative impact on performance.
* There is no native `file_sd_configs` JSON file pull mechanism provided by Prometheus. As such, periodically retrieving the new configuration from Prometheus would have to be done using a second tool (e.g. `curl` or `wget`), which was scheduled using yet-another-tool (e.g. `crond`). Whilst this may not be considered a big deal for an average administrator, it adds another single point of failure, which also may break and is out of the control of Prometheus' usual monitoring capabilities.
* With the aggregating approach used by Promregator today, also Promregator's own-generated metrics can be conveyed along to Prometheus with the same request. The metric's samples are transferred along the same communication channel as all the other metrics are transported. Thus, they provide a common set of metrics, making sure that they are always in sync with each other. Furthermore, using the approach depicted above, transporting Promregator's own-generated metrics to Prometheus would require yet another metrics endpoint and thus one additional HTTP request, which needs to be handled by Prometheus.
* Finally, the approach depicted above does not really solve the "outsourcing problem" of Promregator: Major Cloud Foundry-specific configuration parameters, such as API Host, CF Username, CF Password or the candidates of CF Apps to be monitored, do not fit into Prometheus' configuration file. Thus, they still have to be specified in a Promregator's own configuration file (or at least in the latter's domain). Moreover, the dynamic nature of the `file_sd_configs` approach does not facilitate rule handing or rewrites either, as the administrator would have to be kept alone with the challenge that the names of the targets may still change their names dynamically.

## Relabeling on metrics path

There is one more challenge with this approach: the `file_sd_configs` JSON file format does not support providing different paths natively. Instead, a deviating path (the path which would indicate Prometheus' endpoint which instance shall be scraped) would have to be provided by an additional label. Then, in Prometheus' configuration file a relabel configuration of the form

```yaml
relabel_configs:
- source_labels: [__meta_promregator_target_path]
  action: replace
  target_label: __metrics_path__
  regex: (.+)
```

would be required to pass on the new label to the scraping logic (cf. a [similar configuration, which is used for Kubernetes](https://github.com/prometheus/prometheus/blob/60dafd425cdc96f7df3019cf756998b42209cf1d/documentation/examples/prometheus-kubernetes.yml#L257)). Whilst this would allow the new variant of Promregator to scale along paths (and does not mean that for each CF App instance an own port was necessary), this still complicates the configuration of Prometheus -- and moreover feels like a workaround to the administrator.

## Advantages of this Approach

On "pro side" of the discussion you may find the following aspects:

* Control of when scraping for which target happens is back to Prometheus (where it belongs in general). This allows to scale significantly better, if you have a large number of targets on your scraping list. Major advantage here is that Prometheus is able to prevent peak loads on scraping by distributing the scraping requests within the scraping interval.
* As Prometheus is in control of each target again, individual relabeling is possible again. This may become necessary, if the default labels provided by Promregator do not suite your needs.


## Conclusion

Overall, the Single Target Scraping mode should be preferred for installations where configuration complexity is less of a problem, but additional control on the Prometheus' side is required mainly due to the following reasons:

* Scalability is an issue as many (dynamically selected) targets need to be scraped
* Additional relabeling is required, as the provided labels do not fit for the use case

