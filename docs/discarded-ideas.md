# Discarded Ideas

On the way of implementing Promregator, we came across several alternatives, which also addressed the matter of scraping Prometheus metrics from Cloud Foundry-based applications (partially). We have documented the discussion, which we went through with them, here for your reference.

## Using nginx as Proxy
In [Prometheus Issue #1724](https://github.com/prometheus/prometheus/issues/1724) an approach is suggested to setup an nginx instance to be used as intermediate proxy for the scraping request allowing you to inject a custom HTTP Header. 

Whilst this is a super-simple solution, it has two major drawbacks:
* The approach would only work on scraping using HTTP and not HTTPS (this is due to the fact that nginx technically is man-in-the-middle, against which HTTPS explicitly engaged security countermeasures). 
* Applying the [X-CF-APP-INSTANCE header approach](https://docs.cloudfoundry.org/concepts/http-routing.html#app-instance-routing) here fails due to the fact that the value of the header attribute `X-CF-APP-INSTANCE` would have to have a new value for each target, which shall be scraped. In sum this would mean that you have to maintain `n` nginx proxy configurations, if you want to scrape `n` CF App instances. Moreover, this approach does not provide a solution on the service discovery problem associated with the fact that in Cloud Foundry, apps may be scaled by starting and stopping further instances as needed (each of these dynamically started instances would require an own nginx configuration/instance).


## Using PushProx

[PushProxy](https://github.com/RobustPerception/PushProx) is a Prometheus proxy, which can be used to tunnel requests through firewalls and proxies. It also supports the inversion of the communication direction by establishing the connection from client side (in our case, this is the CF Application / Instance side), connecting to a server. It differs from a [pushgateway](https://github.com/prometheus/pushgateway) in that way, that it does not store the metrics on the (proxy) server, but only tunnels the request to the client.

Whilst this approach bypasses the connectivity problem which you have to connect to single CF App instance (e.g. you do not need the [X-CF-APP-INSTANCE header approach](https://docs.cloudfoundry.org/concepts/http-routing.html#app-instance-routing)), it creates a new one with its client: This tool needs to be made available inside the CF App instance. To make this happen, you either need to have a container-based application which you run in your CF application or you need to adjust the buildpack which you use to deploy your application.

However, PushProx nicely solves the problem of publishing the discovered clients by providing an endpoint called `/clients`, which contains a ready-to-use configuration file for the service discovery method `file_sd_configs`. 

## Promregator with Multiple Scraping Endpoints

As laid out in our [architecture discussion](architecture.md), Promregator outsources parts of the landscape configuration from the Prometheus' configuration file to its own configuration file. In the previous section, it has been discussed that this issue is bypassed nicely by PushProx by providing a ready-to-use configuration file for the service discovery method `file_sd_configs`. On first glance it looks appealing to apply the same pattern also to Promregator:

1. Promregator would perform the service discovery and expose the result in the appropriate `file_sd_configs` JSON format via an endpoint like `/clients`.
2. The content of the endpoint would be fetched regularly (for example via `wget` running in the same environment as Prometheus). By this, Prometheus the scraping targets are updated automatically.
3. The configuration file contains pseudo-URL pointing to endpoints provided by Promregator, which contain unique identifiers to determine for which CF App Instance scraping is requested.
4. Based on the unique identifier in the scraping request, the scraping request is forwarded (like a proxy) to the corresponding CF App instance (e.g. using the [X-CF-APP-INSTANCE header approach](https://docs.cloudfoundry.org/concepts/http-routing.html#app-instance-routing)).

Yet, on second glance, this kind of additional approach has three major drawbacks:

* With the aggregating approach used by Promregator today, Prometheus only needs to send a single HTTP request to retrieve a complete set of data. With the approach depicted above, scraping `n` CF App Instances requires `n + 1` HTTP requests between Prometheus and Promregator (`n` for each instance one, which are then proxied, `+1` for the service discovery request, fetching the `file_sd_configs` JSON file). Given the fact that for each request additional overhead costs (for network, sockets, transport, establishing communication), this will have a negative impact on performance and scalability.
* With the aggregating approach used by Promregator today, also Promregator's own-generated metrics can be conveyed along to Prometheus with the same request. The metric's samples are transferred along the same communication channel as all the other metrics are transported. Thus, they provide a common set of metrics, making sure that they are always in sync with each other. Furthermore, using the approach depicted above, transporting Promregator's own-generated metrics to Prometheus would require yet another metrics endpoint and thus one additional HTTP request, which needs to be handled by Prometheus.
* Finally, the approach depicted above does not really solve the "outsourcing problem" of Promregator: Important Cloud Foundry-specific configuration parameters, such as API Host, CF Username, CF Password or the candidates of CF Apps to be monitored, do not fit into Prometheus' configuration file. Thus, they still have to be located in a Promregator's own configuration file. Moreover, the dynamic nature of the `file_sd_configs` approach does not facilitate rule handing or rewrites either, as the administrator would have to be kept alone with the challenge that the names of the targets may still change their names dynamically.

