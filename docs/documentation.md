# Documentation of Prometheus

## Quickstart Guide

A quickstart guide using docker images is availabe at our [quickstart page](quickstart-docker.md).

## Architecture

A detailed analysis of Promregator's architecture is described in the [architecture page](architecture.md). Please refer to this page for further details.

## Single Endpoint Scraping or Single Target Scraping

Since version 0.2.0, Promregator supports two modes on how it allows to integrate with Prometheus:

* **Single Endpoint Scraping Mode**: This is the preferred mode. Promregator provides only one single endpoint (`/metrics`), which handles both the discovery of CF App instances on the Cloud Foundry platform, the scraping of the metrics from these instances and the merging of the metric samples which have been scraped. Prometheus only needs to scrape one single (static) target and thus only needs to send a single request for retrieving all metrics of all CF App instances at once. However, this comes at a cost of flexibility on labeling / relabeling in Prometheus.

* **Single Target Scraping Mode**: Service discovery (determining which CF App instances are subject to scraping) is separated in an own endpoint (`/discovery`). It provides a JSON-formatted downloadable file, which can be used with the `file_sd_configs` service discovery method of Prometheus. The file includes an own target for each CF app instance to be scraped, for which Promregator serves as proxy. Therefore, Prometheus will send multiple scraping requests to Promregator (at the endpoint starting with path `/singleTargetMetrics`), which redirects them to the corresponding CF app instances. 

We consider the Single Endpoint Scraping mode as superior to the Single Target Scraping mode. For a detailed discussion why we think so, refer to our [Single Target Scraping mode page](singleTargetScraping.md).

Note that Promregator is capable of running in both modes at the same point in time. That is to say: You may switch the mode even without restarting Promregator. The major difference only is, what you need to do in Prometheus' configuration to make it talk to Promregator.


## Configuration (of Promregator)

Configuration of Promregator is performed using any variant of the [Spring Property Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html) 
approach. 

The suggested approach is to create a configuration YAML file, such as `myconfig.yaml`, and start Promregator with the following command line option:

```bash
java -Dspring.config.location=file:/path/to/your/myconfig.yaml -jar promregator-x.y.z-SNAPSHOT.jar
```

Here is an dummy example for such a configuration yaml file:
```yaml
cf:
  apiHost: api.cf.example.org
  username: myCFUserName
  proxyHost: 192.168.111.1
  proxyPort: 8080

promregator:
  authenticator:
    type: OAuth2XSUAA
    oauth2xsuaa:
      tokenServiceURL: https://jwt.token.server.example.org/oauth/token
      client_id: myOAuth2ClientId
    
  targets:
    - orgName: myCfOrgName
      spaceName: mySpaceName
      applicationName: myApplication1
      
    - orgName: myOtherCfOrgName
      spaceName: myOtherSpaceName
      applicationName: myOtherApplication
```

The documentation of the configuration options can be found [here](docs/config.md).


## Configuration of Prometheus Server

As there are two modes of how Prometheus may talk to Promregator (Single Endpoint Scraping Mode and Single Target Scraping Mode, see also above), the suggested configuration of Prometheus depends on the mode you want to use.

### Configuration using Single Endpoint Scraping Mode (recommended)

From the perspective of Prometheus, Promregator behaves like any other (single) target. Thus, you may use a static scraping configuration to connect Prometheus to Promregator. You may set up a scraping job for example like this:

```yaml
[...]
scrape_configs:
  - job_name: 'prometheus'
    scheme: http
    static_configs:
      - targets: ['hostname-of-promregator:8080']
```

Note that the option `honor_labels: true` is **not** required. 


### Configuration using Single Target Scraping Mode

From the perspective of Prometheus, Promregator behaves like both a service discovery tool and a service which contains several scraping targets. 

#### Service Discovery

Promregator provides a JSON-formatted file, which can be fed directly to a file of service discovery `file_sd_configs`. The endpoint where this file is available is called `/discovery`. The endpoint is enabled automatically. You may retrieve the document using `wget` or `curl` my using the following command line:

```bash
$ curl http://hostname-of-promregator:8080/discovery > promregator.json
```

The file then is downloaded to the file called `promregator.json`. This file contains references to the corresponding paths of endpoints which support the Single Target Scraping mode. 

Note that the file has to explicitly mention the hostname and the port of your Promregator instance. Promregator tries to auto-detect this based on the request retrieved. However, for example if Promregator is running in a Docker container, this mechanism may fail. You then have to explicitly have to set the configuration parameters `promregator.discovery.hostname` and `promregator.discovery.port`. For futher details on these two options, also refer to the (configuration options page)[config.md].

A sample service discovery configuration at Prometheus then may look like this:

```yaml
[...]
    file_sd_configs:
    - files:
      - promregator.json
```

Note that the service discovery endpoint is not protected (yet) by any authentication mechanism and thus may needs to be considered in your security concept.

#### Label Rewriting for Setting Metrics Path

Prometheus does not support setting the metrics path for a target directly when defining targets using the `file_sd_configs` approach. Therefore, relabeling has to take place using the meta label `__meta_promregator_target_path`. Usually, 

```yaml
[...]
    relabel_configs:
    - source_labels: [__meta_promregator_target_path]
      action: replace
      target_label: __metrics_path__
      regex: (.+)
```

Additionally, the following meta labels are also provided by the discovery service:

| Label name | Meaning | Example(s) |
|------------|---------|------------|
| `__meta_promregator_target_orgName` | the name of the Cloud Foundry organization in which the CF app instance is located | `yourOrgName` |
| `__meta_promregator_target_spaceName` | the name of the Cloud Foundry space in which the CF app instance is located | `yourSpaceName` |
| `__meta_promregator_target_applicationName` | the name of the Cloud Foundry application of the CF app instance which is being scraped | `appName` |
| `__meta_promregator_target_applicationId` | the GUID of the Cloud Foundry application of the CF app instance which is being scraped| `5d49f9b0-8ac7-46b3-8945-1f500be8b96a` |
| `__meta_promregator_target_instanceNumber` | the instance number of the CF app instance which is being scraped| `0` or `2` |
| `__meta_promregator_target_instanceId` | the instance identifier of the CF app instance which is being scraped| `5d49f9b0-8ac7-46b3-8945-1f500be8b96a:0` |


#### Summary

Summarizing the suggestions for the Prometheus' configuration, it is recommended to configure Prometheus like this:

```yaml
[...]
    file_sd_configs:
      - files:
        - /path/to/your/promregator.json

    relabel_configs:
    - source_labels: [__meta_promregator_target_path]
      action: replace
      target_label: __metrics_path__
      regex: (.+)
```

### Common to both Scraping Modes

Authentication currently is not required / not available (yet). For the current progress on this topic, please refer to [#17](https://github.com/promregator/promregator/issues/17).


 