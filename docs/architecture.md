# Architecture

## Intention

Prometheus is a great tool to retrieve metrics at a constant rate from a set of targets. It is based on a fetching mechanism. Besides that it also has a service discovery approach, which allows dynamic definition of targets which are subject to be pulled. However, the service discovery modules may only influence the target URL (the "whereabout"), but not the content of the pulling (the "how").

Cloud Foundry provides the capability to run nearly arbitrary HTTP-enabled applications (e.g. developed in Java) on a Platform-as-a-Service-enabled environment. 
To provide fail-over of applications and to support scalability, multiple instances of the same application may be run behind a reverse proxy (a.k.a. web dispatcher).
Requests are typically dispatched in a round-robin schedule.
Detailed monitoring of these applications (esp. if talking about custom metrics such as Prometheus supports) is considered "an implementation detail" (besides logging facilities like the [ELK stack](https://www.elastic.co/de/elk-stack)).

These two distinct worlds do not fit together on first glance, due to the following obstacles:
* CF apps are only reachable (from externally without having CF administrative privileges, which is not common on PaaS offerings) 
  via official, world-reachable URLs. The cells, on which on which the CF apps are running, are typically protected by a firewall and the reverse proxy. 
  Application developers do not have access via a side-channel to the cells, as this would bypass many security measures taken by the platform.
  
  This means that reading the metrics must be performed via the world-reachable URLs, i.e. going through the reverse proxy.
  As the endpoints for retrieving the Prometheus metrics will be world-reachable, and exposing such internal information will be a major security risk for
  such applications, the Prometheus metrics endpoints must be authentication-protected in some way. Often, this is done using OAuth2/JWT-based authentication, which is not supported (yet) by Prometheus natively.
* For providing failure-safety and scalability, Cloud Foundry supports to run multiple instances of an application, which are registered to the same
  world-reachable URL (see also [Routing](https://docs.cloudfoundry.org/devguide/deploy-apps/routes-domains.html)). As it is unpredictable for Prometheus
  to which instance a request to an endpoint is dispatched by the reverse proxy, the metrics retrieved by the pulling mechanism will hit instances by random each time.
  Thus, to prevent that a monitoring tool would see randomly-appearing sets of metrics, a monitoring solution must be aware of the application instances running on the platform.

Promregator wants to fix these both obstacles by providing a tool, which 
* at the one side behaves like a Prometheus client to a Prometheus server,
* while retrieving the source metrics from multiple instances running Prometheus metrics endpoints which are compliant to the Prometheus client protocol.

It tries to hide the complexity of the multi-instance approach provided by Cloud Foundry from both the operator and the Prometheus server.

## General Design

![Architecture of Promregator](architecture.png)

Promregator is sitting between your Prometheus server on the one hand and talks to your Cloud Foundry apps on the other hand. 
It is converting the Prometheus' scraping requests into queries targeting your instances of your apps, which are running on Cloud Foundry. To address Cloud Foundry application uniquely with their targetting requests, the principle of [App Instance Routing](https://docs.cloudfoundry.org/concepts/http-routing.html) is applied.

For Prometheus, Promregator feels like a single client, whilst Promregator may request multiple targets.

Promregator is also in contact with the Cloud Controller of the Cloud Foundry platform to be able to detect changes in the Cloud Foundry platform environment. For example, scaling up the number of running instances of an application (due to high load) can then be detected automatically. Discovery of this new instance is then performed automatically and the metrics are replicated to the Prometheus server.



## Why is Promregator not just a Prometheus discovery service?

Half of the activity performed by Promregator is the typical task of a discovery service (in the terms of Prometheus): Based on metadata provided, it determines a set of targets, which will be subject to scraping. 

However, if you want to apply the methodology of [App Instance Routing as provided by Cloud Foundry](https://docs.cloudfoundry.org/concepts/http-routing.html) to scrape the metrics, only discovery is not enough: As Cloud Foundry allows you to run multiple instances of the same application to provide fallback and scalability hidden behind the same URL, you need to address the individual instance uniquely (otherwise you might see randomly only the metrics of instance 1, whilst with the scraping you would see only the metrics of instance 2). In the case of Cloud Foundry, this requires adjusting the HTTP request which you are sending to scrape the metrics by adding an additional HTTP header. The content of this header attribute is highly dependent on the targets which have been discovered before. Thus, there is a tight coupling between the discovery service and the extension of the scraping request.

The subject of adding HTTP headers to scraping requests have been largely discussed in the Prometheus community. With regards to the App Instance Routing approach, this explicitly has been rejected in [Prometheus issue #2346](https://github.com/prometheus/prometheus/issues/2346) with references to further discussions. 

Based on this discussion, you might still argue that creating a discovery service would still be possible. Yet, it still would mean that it is not permitted that the target reported back to the Prometheus core cannot be the metrics endpoint of the application in the Cloud Foundry environment. Moreover, it would mean that yet-another-proxy would be required, which takes the targeting information (in case of App Instance Routing this mainly is the application id and the instance number) and reroutes the request to the Cloud Foundry application accordingly. Thus, either this proxy would be a major security risk (as it would blindly forward any well-formed request without further checking), or the proxy would have to be tightly integrated with the discovery service, which is running inside of Prometheus. The latter approach would create a very complicated implementation, which is expected to be error-prone.


## Why is Promregator not just an exporter?

An exporter as discussed [here](https://prometheus.io/docs/instrumenting/writing_exporters) is an enhancement to an existing type of a defined service. It enables services, which provide their metrics using other APIs, to also expose them in a manner that is compliant to what Prometheus expects. Usually, they are running on [dedicated own ports](https://github.com/prometheus/prometheus/wiki/Default-port-allocations) alongside to the services themselves (typically in the same environment as separate processes). Thus, additional port access is required to the outside world, which may have to be protected by additional firewall rules to prevent that non-authorized entities may not read the data.

In the world of Cloud Foundry, this would mean that exporters had to run on the same cell as the original application, which you want to scrape. Technically, you would have to modify the buildpack you are using to ensure that the exporter is also started properly (if you are not using the binary buildpack anyway). Yet, this is not enough, as you would have to expose the exporter's port via a route (NB: it is not given for all Cloud Foundry environments that additional port exposure is supported), for which afterwards access needs to be narrowed down again to block unwanted spectators to read the data.

All this is quite some effort, requires maintenance, makes setup complicated and error-prone. The approach of [App Instance Routing as provided by Cloud Foundry](https://docs.cloudfoundry.org/concepts/http-routing.html) is much less complicated and can be easily protected using application-side authentication.

## Implications on Landscape Configuration

The insight that scraping of Cloud Foundry application metrics neither is possible with a new discovery service alone nor with implementing an exporter leads to a major consequence: **The definition of the landscape configuration** (where the metrics are being retrieved from)**, which is considered *the* central task for the Prometheus configuration, is outsourced to the configuration of Promregator**, at least with regards to cases, where the metrics are being scraped from Cloud Foundry applications.

Please note, however, that whilst this is unavoidable as long as the decision of [Prometheus issue #2346](https://github.com/prometheus/prometheus/issues/2346) is not revised, this is not a severe practical problem: 

* Promregator supports to be configured using YAML files. As all configuration parameters specific to Promregator are separated in an own namespace, it may **share** the same configuration file with Prometheus. Thus, all the information still is located only in one file.
* If you have a more complex configuration of Promregator, which contains multiple Cloud Foundry applications distributed across multiple Cloud Foundry installations (for example) and you want to make use of metrics rewriting based on different targets, you are still able to separate the Promregator's configuration files into logical blocks and run **multiple instances of Promregator** using distinct configuration files. Each instance of Promregator then is registered as its own (static) target in Prometheus, thus allowing you to apply your rewriting rules on each Promregator's metrics individually.

