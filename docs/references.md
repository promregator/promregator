# Related Work

While implementing and maintaining Promregator, we came across several other tools and offerings, which are related to the work we do. This page should serve you as "References" section, in case you are seeking for associated solutions:

| Tool | Link | Notes |
|------|------|-------|
| Prometheus | https://prometheus.io |  |
| Prometheus (Java) Simple Client |  https://github.com/prometheus/client_java | This is the client library intended for implementing Prometheus clients in Java. We make great use of this work in various places. |
| PushProxy  | https://github.com/RobustPerception/PushProx | Prometheus Proxy, which allows to invert the connectivity direction for retrieving metrics (e.g. due to firewalls) without storing the metrics information (like [Pushgateway](https://github.com/prometheus/pushgateway) does it) |
| Micrometer | http://micrometer.io/ | Tool provided by Pivotal (one of the members of the Cloud Foundry Alliance), which facilitates the generation of metrics in your application, allowing the data to be exported to various other monitoring tools (also besides Prometheus) |


