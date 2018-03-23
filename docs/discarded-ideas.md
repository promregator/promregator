# Discarded Ideas

On the way of implementing Promregator, we came across several alternatives, which also addressed the matter of scraping Prometheus metrics from Cloud Foundry-based applications (partially). We have documented the discussion, which we went through with them, here for your reference.


## Using nginx as Proxy
In [Prometheus Issue #1724](https://github.com/prometheus/prometheus/issues/1724), an approach is suggested to setup an nginx instance to be used as intermediate proxy for the scraping request allowing you to inject a custom HTTP Header. 

Whilst this is a super-simple solution, it has two major drawbacks:
* The approach would only work on scraping using HTTP and not HTTPS (this is due to the fact that nginx technically is man-in-the-middle, against which HTTPS explicitly engaged security countermeasures). 
* Applying the [X-CF-APP-INSTANCE header approach](https://docs.cloudfoundry.org/concepts/http-routing.html#app-instance-routing) here fails due to the fact that the value of the header attribute `X-CF-APP-INSTANCE` would have to have a new value for each target, which shall be scraped. In sum this would mean that you have to maintain `n` nginx proxy configurations, if you want to scrape `n` CF App instances. Moreover, this approach does not provide a solution on the service discovery problem associated with the fact that in Cloud Foundry, apps may be scaled by starting and stopping further instances as needed (each of these dynamically started instances would require an own nginx configuration/instance).


## Using Pushgateway as Intermediate Bridge
In [Prometheus Issue #1724](https://github.com/prometheus/prometheus/issues/1724), robachmann proposes to a workaround to use  [Pushgateway](https://github.com/prometheus/pushgateway) as intermediate storing-proxy. With this it is possible to connect an arbitrary number of clients to a central communication instance, exposing samples to Prometheus. The major difference in this approach is the concept that the metrics endpoint of CF App instances are not being pulled from outside, but the application coding will ensure that results are regularly published to the Pushgateway instance in an active fashion. Details about this approach in general can also be found [here](https://prometheus.io/docs/practices/pushing/).

Our analysis has shown that this approach has several disadvantages:
* Prometheus clearly rejects using Pushgateway for periodic scraping events -- for many good reasons. Amongst the most severe one's in the context of Cloud Foundry applications is, that Pushgateway stores samples of metrics until their value is explicitly removed from the server again. This implied - for example - that if an instance of a CF app crashes, then the "last-known" value will still be forwarded to Prometheus, even though the instance was long gone.
* Pushgateway (as of writing) does not support authentication schemes. This means that the instance of Pushgateway needs to be reachable from the publicly available CF applications via a trusted channel. Given the fact, that CF app instances may be moved across cells on the Cloud Foundry platform without further warning and that on the same platform (using the same IPs) further applications may be running, estabilishing such a safe connection poses a challenge by itself.
* Pushgateway cannot be run on multiple instances in parallel (e.g. you may not run multiple instances of Pushgateway, if you pushed it as own CF application to your CF platform). Background to this is, that if you did, it would not be predictable, which instance would receive the set of metric's samples, which a given CF app instance would try to submit. This mainly also rules out virtually any kind of fail-over/high availability configuration, thus making Pushgateway a "single point of failure" in your monitoring setup.


## Using PushProx

[PushProxy](https://github.com/RobustPerception/PushProx) is a Prometheus proxy, which can be used to tunnel requests through firewalls and proxies. It also supports the inversion of the communication direction by establishing the connection from client side (in our case, this is the CF Application / Instance side), connecting to a server. It differs from [Pushgateway](https://github.com/prometheus/pushgateway) in that way, that it does not store the metrics on the (proxy) server, but only tunnels the request to the (pre-connected) client.

Whilst this approach bypasses the connectivity problem which you have to connect to single CF App instance (e.g. you do not need the [X-CF-APP-INSTANCE header approach](https://docs.cloudfoundry.org/concepts/http-routing.html#app-instance-routing)), it creates a new one with its client: This tool needs to be made available inside the CF App instance. To make this happen, you either need to have a container-based application which you run in your CF application or you need to adjust the buildpack which you use to deploy your application.

However, PushProx nicely solves the problem of publishing the discovered clients by providing an endpoint called `/clients`, which contains a ready-to-use configuration file for the service discovery method `file_sd_configs`. 

## Promregator with Multiple Scraping Endpoints

As laid out in our [architecture discussion](architecture.md), Promregator outsources parts of the landscape configuration from the Prometheus' configuration file to its own configuration file. In the previous section, it has been discussed that this issue is bypassed nicely by PushProx by providing a ready-to-use configuration file for the service discovery method `file_sd_configs`. On first glance it looks appealing to apply the same pattern also to Promregator:

1. Promregator would perform the service discovery and expose the result in the appropriate `file_sd_configs` JSON format via an endpoint like `/clients`.
2. The content of the endpoint would be fetched regularly (for example via `wget` running in the same environment as Prometheus). By this, Prometheus the scraping targets are updated automatically.
3. The configuration file contains pseudo-URL pointing to endpoints provided by Promregator, which contain unique identifiers to determine for which CF App Instance scraping is requested.
4. Based on the unique identifier in the scraping request, the scraping request is forwarded (like a proxy) to the corresponding CF App instance (e.g. using the [X-CF-APP-INSTANCE header approach](https://docs.cloudfoundry.org/concepts/http-routing.html#app-instance-routing)).

Yet, on second glance, this kind of additional approach has four major drawbacks:

* With the aggregating approach used by Promregator today, Prometheus only needs to send a single HTTP request to retrieve a complete set of data. With the approach depicted above, scraping `n` CF App Instances requires `n + 1` HTTP requests between Prometheus and Promregator (`n` for each instance one, which are then proxied, `+1` for the service discovery request, fetching the `file_sd_configs` JSON file). Given the fact that for each request additional overhead costs (for network, sockets, transport, establishing communication), this will have a negative impact on performance and scalability.
* There is no native `file_sd_configs` JSON file pull mechanism provided by Prometheus. As such, periodically retrieving the new configuration from Prometheus would have to be done using a second tool (e.g. `curl` or `wget`), which was scheduled using yet-another-tool (e.g. `crond`). Whilst this may not be considered a big deal for an average administrator, it adds another single point of failure, which also may break and is out of the control of Prometheus' usual monitoring capabilities.
* With the aggregating approach used by Promregator today, also Promregator's own-generated metrics can be conveyed along to Prometheus with the same request. The metric's samples are transferred along the same communication channel as all the other metrics are transported. Thus, they provide a common set of metrics, making sure that they are always in sync with each other. Furthermore, using the approach depicted above, transporting Promregator's own-generated metrics to Prometheus would require yet another metrics endpoint and thus one additional HTTP request, which needs to be handled by Prometheus.
* Finally, the approach depicted above does not really solve the "outsourcing problem" of Promregator: Major Cloud Foundry-specific configuration parameters, such as API Host, CF Username, CF Password or the candidates of CF Apps to be monitored, do not fit into Prometheus' configuration file. Thus, they still have to be specified in a Promregator's own configuration file. Moreover, the dynamic nature of the `file_sd_configs` approach does not facilitate rule handing or rewrites either, as the administrator would have to be kept alone with the challenge that the names of the targets may still change their names dynamically.

There is one more challenge with this approach: the `file_sd_configs` JSON file format does not support providing different paths natively. Instead, a deviating path (the path which would indicate Prometheus' endpoint which instance shall be scraped) would have to be provided by an additional label. Then, in Prometheus' configuration file a label rewrite of the form

```yaml
relabel_configs:
- source_labels: [__meta_promregator_target_path]
  action: replace
  target_label: __metrics_path__
  regex: (.+)
```

would be required to pass on the new label to the scraping logic. Whilst this would allow the new variant of Promregator to scale along paths (and does not mean that for each CF App instance an own port was necessary), this still complicates the configuration of Prometheus -- and moreover feels like a workaround to the administrator.

