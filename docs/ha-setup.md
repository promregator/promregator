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
  Note that setting the path is very important, as otherwise scraping will fail entirely, as you would be trying to
  scrape not just Promregator's own metrics, but all metrics (you get a fallback to Single Endpoint Scraping mode), which would lead to an endless recursive loop.

By this, Promregator's own metrics will be scraped like all the metrics provided by the other applications. This also implies that the `promregator_*` metrics will be enriched with the labels of its scraping origin, i.e. each Promregator metric will also have `orgName`, `spaceName`, `applicationName` and -- most important -- `instanceId` assigned. By using the `instanceId` you are able to determine from which instance of Promregator the metrics are coming from. (As usual) special care has to be taken, if you are aggregating across multiple `instanceId`s.

### Recommendations on Deployment
*to be provided with experience report*

