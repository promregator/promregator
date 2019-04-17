# Scraping using Separate Service Discovery and Single Target Scraping Endpoints

Motivated by the approach taken by [PushProxy](https://github.com/RobustPerception/PushProx), Promregator also supports the mode of Single Target Scraping since version 0.2.0. The general idea employed by PushProx is to separate the endpoint responsible for service discovery (in case of Promregator, this is the `/discovery` endpoint), which provides a directory for proxying endpoints for each target to be scraped (in our case: CF app instances; these endpoints start with `/singleTargetMetrics`).

This approach's major benefit is scalability: If Promregator is operated in an environment with a dozen or more target instances on the Cloud Foundry platform, synchronous but parallel scraping them may become a major bottleneck. It becomes more and more difficult to ensure that a consistent scraping result is returned to Prometheus. This is due to the fact that all targets of Promregator are hidden behind Prometheus, if Single Endpoint Scraping is used. 

Instead, Single Target Scraping exposes a list of targets to Prometheus. Due to this, Prometheus may take control on the set of targets to be scraped. Prometheus then distributes the scraping requests for each target of the scraping interval. By this, load is evenly distributed over time.

Single Target Scraping has its pros and cons, which are being discussed here on this page.

## General setup
In general the logic of Single Target Scraping mode looks like this:

1. Promregator performs the service discovery and exposes the result in the appropriate `file_sd_configs` JSON format via an endpoint called `/discovery`.
2. The content of the endpoint has to be fetched regularly (for example via `wget` or `curl` running in the same environment as Prometheus) and stored in a file which is registered with `file_sd_configs`. As the file-based discovery service has auto-reloading of it discovery file, Prometheus' scraping targets may be updated automatically.
3. The configuration file contains pseudo-URLs pointing to endpoints provided by Promregator, which contain unique identifiers to determine for which CF App Instance scraping is requested.
4. Based on the unique identifier in the scraping request, the scraping request is forwarded (like a proxy) to the corresponding CF App instance (e.g. using the [X-CF-APP-INSTANCE header approach](https://docs.cloudfoundry.org/concepts/http-routing.html#app-instance-routing)).


## Drawbacks of this approach

Single Target Scraping mode has the following disadvantages:

* Employing the Single Endpoint Scraping approach used by Promregator, Prometheus only needs to send a single HTTP request to retrieve a complete set of data. With the approach described above, scraping `n` CF App Instances requires `n + 1` HTTP requests between Prometheus and Promregator (`n` for each instance one, which are then proxied, `+1` for the service discovery request, fetching the `file_sd_configs` JSON file). Given the fact that for each request additional overhead costs (for network, sockets, transport, establishing communication), this may have a negative impact on performance.
* There is no native `file_sd_configs` JSON file pull mechanism provided by Prometheus. As such, periodically retrieving the new configuration from Prometheus would have to be done using a second tool (e.g. `curl` or `wget`), which is to be scheduled using yet-another-tool (e.g. `crond`). Whilst this may not be considered a big deal for an average administrator, it adds another single point of failure, which also may break and is out of the control of Prometheus' usual monitoring capabilities.
* With the Single Endpoint mode, Promregator's own-generated metrics can be conveyed along to Prometheus with the same request. The metric's samples are transferred along the same communication channel as all the other metrics are transported. Thus, they provide a common set of metrics, making sure that they are always in sync with each other. Furthermore, using Single Target Scraping, transporting Promregator's own-generated metrics to Prometheus requires yet another metrics endpoint and thus one additional HTTP request, which needs to be handled by Prometheus.

## Advantages of this Approach

Single Target Scraping mode has the following advantages:

* Control of when scraping for which target happens is back to Prometheus (where it belongs in general). This allows to scale significantly better, if you have a large number of targets on your scraping list. Major advantage here is that Prometheus is able to prevent peak loads on scraping by distributing the scraping requests within the scraping interval.
* As Prometheus is in control of each target again, individual relabeling is possible again. This may become necessary, if the default labels provided by Promregator do not suite your needs.


## Conclusion

Overall, the Single Target Scraping mode should be preferred for installations where configuration complexity is less of a problem, but configuration flexibility on the Prometheus' side is required or the number of targets to scrape demands Promregator to scale accordingly.

