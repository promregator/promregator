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

## Promregator with Endpoints for Single Target Scraping

As laid out in the previous section, PushProx is separating the service discovery aspect to one endpoint, which allows to register multiple targets in Prometheus using the interface of `file_sd_configs`. Scraping itself then takes place at a dedicated on scraping endpoint, whose path defines which single target (in our case: CF app instance) shall be scraped. 

Whilst this way has some advantages with regards to configuration in Prometheus, it also has several disadvantages. For a detailed discussion about this, refer to the [Single Target Scraping page](singleTargetScraping.md).

Note that Promregator supports this mode since version 0.2.0. However, the preferred approach still is the one using a combined endpoint, which both deals with service discovery and scraping in one shot (as depicted on the [architecture page](architecture.md)).
