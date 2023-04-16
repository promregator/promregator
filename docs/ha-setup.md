# High-Availability Setup

To ensure availability of service, you may run Promregator in a high-availability environment. Main aspect of this setup is that multiple instances of Promregator need to run in parallel. 

As Promregator can be used in multiple different environment (and those environments provide high-availability with multiple different approaches), this page should give you guidance how to achieve it.

## In Cloud Foundry Environment

You may run Promregator as an own application using the Cloud Foundry platform. This can be done by deploying Promregator manually (by deploying its JAR file), or by using Promregator's docker image. 

### General Guidance

If Promregator is running on the same platform as the applications which you intend to scrape, you may use Promregator to support "self-scraping". For this setup, the following recommendations are provided:

* Deploy Promregator to the Cloud Foundry platform, using at least two instances.
* Set the configuration option `promregator.discovery.ownMetricsEndpoint` to `false`. 
* Assuming that you have deployed it to organization `promOrg` in space `promSpace` and the application is called `promApplication`, then add another target to Promregator's configuration like this:
  ```yaml
  promregator:
    targets:
      # - [...]
      - orgName: promOrg
        spaceName: promSpace
        applicationName: promApplication
        protocol: https
        path: /promregatorMetrics
  ```

By this, Promregator's own metrics will be scraped like all the metrics provided by the other applications. This also implies that the `promregator_*` metrics will be enriched with the labels of its scraping origin, i.e. each Promregator metric will also have `orgName`, `spaceName`, `applicationName` and -- most important -- `instanceId` assigned. By using the `instanceId` you are able to determine from which instance of Promregator the metrics are coming from. (As usual) special care has to be taken, if you are aggregating across multiple `instanceId`s.

It may happen that the label names of the self-scraped metrics overlap with the attributes provided by discovery at Prometheus' side (see also [Issue #235](https://github.com/promregator/promregator/issues/235)). This may lead to error messages at Prometheus' log like

> label name "app_name" is not unique: invalid sample

You have two options to resolve this:

* Use the configuration option [`honor_labels` provided by Prometheus](https://prometheus.io/docs/prometheus/latest/configuration/configuration/), setting it to `true`. This will add skip label enrichment by Prometheus for colliding labels (i.e. Promregator's values will be taken).
* Set Promregator's configuration option `promregator.metrics.labelNamePrefix` to a non-empty value. This will prefix all label names created by Promregator with the string configured, which will resolve the conflict.

Note that if you protect your `/promregatorMetrics` (e.g. via `promregator.metrics.auth`) it is necessary to add an `authenticatorId` attribute to your target mentioned above. Promregator behaves here like any other target. So, for details you may refer to the [outbound authentication page](./outbound-authentication.md)


### Recommendations on Deployment

#### Running as Docker Image on Cloud Foundry

Running multiple instances of promregator using docker is fairly easy. You can use the already available docker images from Docker hub and just add your configuration in as in the following Dockerfile:
```
FROM promregator/promregator:latest

ADD promregator.yaml /etc/promregator/promregator.yml
```

As best practice, assign 1G memory and one CPU per instance. It also proved useful to deploy with a health-check timeout of 180 seconds. You can achieve this using the command `cf push -t 180` as mentioned in the [official Cloud Foundry documentation](https://docs.cloudfoundry.org/devguide/deploy-apps/large-app-deploy.html).

Monitoring the individual instances is possible since the label `cf_instance_number` is provided. A sample Grafana dashboard for monitoring multiple instances of promregator can found at https://gist.github.com/vervas/306afdf50b679594b4b0692ff6741d2f.
