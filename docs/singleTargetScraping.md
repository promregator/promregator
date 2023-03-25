# Scraping using Separate Service Discovery and Single Target Scraping Endpoints

Motivated by the approach taken by [PushProxy](https://github.com/RobustPerception/PushProx), Promregator also supports the mode of Single Target Scraping since version 0.2.0. The general idea employed by PushProx is to separate the endpoint responsible for service discovery (in case of Promregator, this is the `/discovery` endpoint), which provides a directory for proxying endpoints for each target to be scraped (in our case: CF app instances; these endpoints start with `/singleTargetMetrics`).

This approach's major benefit is scalability: If Promregator exposed only a single endpoint to scrape metrics from a dozen or more targets on the Cloud Foundry platform, synchronous but parallel scraping them may become a major bottleneck. It becomes more and more difficult to ensure that a consistent scraping result is returned to Prometheus. This is due to the fact that for Prometheus all targets are hidden behind Promregator.

That is why Promregator exposes a list of targets to Prometheus directly. Due to this, Prometheus may take control on the set of targets to be scraped. Prometheus then distributes the scraping requests for each target of the scraping interval. By this, load is evenly distributed over time, and scraping failures caused by a single targets can be determined individually by Prometheus. 

## General setup
In general the procedure of discovery and scraping looks like this:

1. Promregator performs the service discovery and exposes the result in the appropriate `file_sd_configs` JSON format via an endpoint called `/discovery`.
2. The content of the endpoint has to be fetched regularly (for example via `wget` or `curl` running in the same environment as Prometheus) and stored in a file which is registered with `file_sd_configs`. As the file-based discovery service has auto-reloading of it discovery file, Prometheus' scraping targets may be updated automatically.
3. The configuration file contains pseudo-URLs pointing to endpoints provided by Promregator, which contain unique identifiers to determine for which CF App Instance scraping is requested.
4. Based on the unique identifier in the scraping request, the scraping request is forwarded (like a proxy) to the corresponding CF App instance (e.g. using the [X-CF-APP-INSTANCE header approach](https://docs.cloudfoundry.org/concepts/http-routing.html#app-instance-routing)).



