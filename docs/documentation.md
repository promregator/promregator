# Documentation of Promregator

## Quickstart Guide

A quickstart guide using docker images is availabe at our [quickstart page](quickstart-docker.md).

## Architecture

A detailed analysis of Promregator's architecture is described in the [architecture page](architecture.md). Please refer to this page for further details.

## Single Endpoint Scraping or Single Target Scraping

Since version 0.2.0, Promregator supports two modes on how it allows to integrate with Prometheus:

* **Single Endpoint Scraping Mode**: This is the default mode. Promregator provides only one single endpoint (`/metrics`), which handles both the discovery of CF App instances on the Cloud Foundry platform, the scraping of the metrics from these instances and the merging of the metric samples which have been scraped. Prometheus only needs to scrape one single (static) target and thus only needs to send a single request for retrieving all metrics of all CF App instances at once. However, this comes at a cost of flexibility on labeling / relabeling in Prometheus and does not scale well, if you have a larger number of Cloud Foundry apps to scrape. Yet, it is quite easy to configure.

* **Single Target Scraping Mode**: Service discovery (determining which CF App instances are subject to scraping) is separated in an own endpoint (`/discovery`). It provides a JSON-formatted downloadable file, which can be used with the `file_sd_configs` service discovery method of Prometheus. The file includes an own target for each CF app instance to be scraped, for which Promregator serves as proxy. Therefore, Prometheus will send multiple scraping requests to Promregator (at the endpoint starting with path `/singleTargetMetrics`), which redirects them to the corresponding CF app instances. This approach allows to also scale to hundreds of apps to be scraped, as control over the point of time for scraping is handled properly by Prometheus. Moreover, it gives you additional flexibility on rewriting. However, all this also makes the mode more complex to configure and to maintain.

In general, consumers are free to choose between these two modes. For a start, we recommend to set up the Single Endpoint Scraping mode, and switch to the Single Target Scraping mode, if the number of CF apps increase and scalability becomes an issue. For a detailed discussion on that refer to our [Single Target Scraping mode page](singleTargetScraping.md).

Note that Promregator is capable of running in both modes at the same point in time. That is to say: You may switch the mode on the fly - even without restarting Promregator. The major difference only is, what you need to do in Prometheus' configuration to make it talk to Promregator.

An overview on the endpoints provided by Promregator can be found at the [endpoint's page](endpoint.md).

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
  api_host: api.cf.example.org
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

The documentation of the configuration options can be found [here](config.md).


## Java Memory Configuration
Promregator is written in Java and therefore requires a Java Virtual Machine (e.g. Java Runtime Edition) to run. Finding a proper memory configuration can be a tricky thing with JVMs - especially, if being run in a docker container. 

The current knowledge about memory configuration for Promregator can be found at the [Java Memory Configuration page](jvm-mem-config.md).

## Configuration of Prometheus Server

As there are two modes of how Prometheus may talk to Promregator (Single Endpoint Scraping Mode and Single Target Scraping Mode, see also above), the suggested configuration of Prometheus depends on the mode you want to use.

### Configuration using Single Endpoint Scraping Mode (easy to configure; recommended for starting)

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

Promregator provides a JSON-formatted file, which can be fed directly to a file of service discovery `file_sd_configs`. The endpoint, where this file is available, is called `/discovery`. The endpoint is enabled automatically. You may retrieve the document using `wget` or `curl` my using the following command line:

```bash
$ curl http://hostname-of-promregator:8080/discovery > promregator.json
```

(NB: This assumes that there is no authentication enabled, cf. `promregator.discovery.auth` on our [configuration options page](config.md))

The file then is downloaded to the file called `promregator.json`. This file contains references to the corresponding paths of endpoints which support the Single Target Scraping mode. 

A sample service discovery configuration at Prometheus then may look like this:

```yaml
[...]
    file_sd_configs:
    - files:
      - promregator.json
```

Note that the file has to explicitly mention the hostname and the port of your Promregator instance as it is seen from Prometheus. Promregator tries to auto-detect this based on the request retrieved. However, for example if Promregator is running in a Docker container, this mechanism may fail. You then have to explicitly set the configuration parameters `promregator.discovery.hostname` and `promregator.discovery.port` accordingly. For further details on these two options, also refer to the (configuration options page)[config.md].

Moreover, it may be worth mentioning that querying the `/discovery` endpoint significantly more frequently than the application cache (see also configuration option `cf.cache.timeout.application`) and the resolver cache (see also `cf.cache.timeout.resolver`) is of little use: The results provided by the endpoint are mainly generated out of the values in these two caches. Extraordinary querying might still make sense, though, if you have [explicitly invalidated the caches manually](./invalidate-cache.md).



#### Label Rewriting

By default, Promegator (still) performs [label enrichment](./enrichment.md) if used with Single Target Scraping mode. Single Target Scraping mode permits that label enrichment may be done by Prometheus. This allows to comply to Prometheus' recommended approach of handling labels which is using [rewriting rules](https://prometheus.io/docs/prometheus/latest/configuration/configuration/#relabel_config). 

As Prometheus does not permit targets to set the value of label `instance` (which is used to indicate a single endpoint from where you scraped your metrics), you should use Prometheus' feature of rewrite labels to update that label. The recommended configurationeven if label enrichment is enabled in Single Target Scraping mode is:

```yaml
    relabel_configs:
     - source_labels: [__meta_promregator_target_instanceId]
       target_label: instance
```

To enable full label rewriting to be performed by Prometheus only, set the [configuration option](./config.md) `promregator.scraping.labelEnrichment` to `false`. In your configuration of Prometheus you then may specify `relabel_configs` which you may adjust to your own needs. For that Promregator's discovery service provides the following meta labels:

| Label name | Meaning | Example(s) |
|------------|---------|------------|
| `__meta_promregator_target_orgName` | the name of the Cloud Foundry organization in which the CF app instance is located | `yourOrgName` |
| `__meta_promregator_target_spaceName` | the name of the Cloud Foundry space in which the CF app instance is located | `yourSpaceName` |
| `__meta_promregator_target_applicationName` | the name of the Cloud Foundry application of the CF app instance which is being scraped | `appName` |
| `__meta_promregator_target_applicationId` | the GUID of the Cloud Foundry application of the CF app instance which is being scraped| `5d49f9b0-8ac7-46b3-8945-1f500be8b96a` |
| `__meta_promregator_target_instanceNumber` | the instance number of the CF app instance which is being scraped| `0` or `2` |
| `__meta_promregator_target_instanceId` | the instance identifier of the CF app instance which is being scraped| `5d49f9b0-8ac7-46b3-8945-1f500be8b96a:0` |

If you want to have the same labels provided as Promregator does during classical label enrichment (e.g. adding `org_name`, `app_name` and so forth), you may use the following configuration snippet:

```yaml
    relabel_configs:
     - source_labels: [__meta_promregator_target_instanceId]
       target_label: instance
      
     - source_labels: [__meta_promregator_target_instanceId]
       target_label: cf_instance_id
       
     - source_labels: [__meta_promregator_target_orgName]
       target_label: org_name
       
     - source_labels: [__meta_promregator_target_spaceName]
       target_label: space_name
       
     - source_labels: [__meta_promregator_target_applicationName]
       target_label: app_name
       
     - source_labels: [__meta_promregator_target_instanceNumber]
       target_label: cf_instance_number
```

Users of Promregator version 0.4.x and earlier should be aware of the page ["Rewriting Rule For __metrics_path__ No Longer Required for Promregator 0.5.0 and Later"](https://github.com/promregator/promregator/wiki/Rewriting-Rule-For-__metrics_path__-No-Longer-Required-for-Promregator-0.5.0-and-Later).


#### Summary

Summarizing the suggestions for the Prometheus' configuration, it is recommended to configure Prometheus like this:

```yaml
[...]
    file_sd_configs:
      - files:
        - /path/to/your/promregator.json

    relabel_configs:
     - source_labels: [__meta_promregator_target_instanceId]
       target_label: instance
       
     - source_labels: [__meta_promregator_target_instanceId]
       target_label: cf_instance_id
       
     - source_labels: [__meta_promregator_target_orgName]
       target_label: org_name
       
     - source_labels: [__meta_promregator_target_spaceName]
       target_label: space_name
       
     - source_labels: [__meta_promregator_target_applicationName]
       target_label: app_name
       
     - source_labels: [__meta_promregator_target_instanceNumber]
       target_label: cf_instance_number
```

If you follow the approach above, make sure that in your Promregator's configuration the configuration option `promregator.scraping.labelEnrichment` is set to `false`.

### Common to both Scraping Modes

Basic Authentication available starting with version 0.2.0 of Promregator.

In general, you need to specify a set of username and password, which may be used for authentication at various places. To define the set of credentials to be used is done by

```yaml
promregator:
  authentication:
    basic:
      username: someuser
      password: somepassword
```

There are three places where inbound authentication checks can be enabled:

* At the metrics' scraping endpoints `/metrics` and `/singleTargetMetrics` by setting the configuration option `promregator.endpoint.auth` to `BASIC`.
* At the discovery endpoint `/discovery` by setting the configuration option `promregator.discovery.auth` to `BASIC`.
* At the Promregator's internal metrics endpoint `/promregatorMetrics` by setting the configuration option `promregator.metrics.auth` to `BASIC`

or any combination of these. 

For further details on these configuration options, also refer to the [configuration options page](config.md)).

The corresponding option in Prometheus is the `scrape_configs[].basic_auth` option. Let us assume that you have configured Promregator like this 

```yaml
promregator:
  endpoint:
    auth: BASIC

  authentication:
    basic:
      username: someuser
      password: somepassword
```

Then the corresponding configuration in Prometheus may look like this:

```yaml
scrape_configs:
  - job_name: 'promregator'
    basic_auth:
      username: someuser
      password: somepassword
```

## Logging

### Log Levels

By default, logging is set to only emit messages, which are of major severity to allow running Promregator out of the box. Therefore, only messages of level "Warning" or higher are provided. Promregator uses [logback](https://logback.qos.ch/) as logging tool. It is aware of the following levels:

| Level | Meaning | Written by default settings | may contain secret data |
|-------|---------|-----------------------------|-------------------------|
| ERROR | Something fatal has happened, which Promregator does not permit to go on as expected | Yes | No |
| WARN  | A situation occurred, which most likely is not expected and thus may hint to some other mistake (e.g. wrong configuration setting) | Yes | No |
| INFO  | Documents typical and usual results of operations; the main flow of logic can be seen in the logs | No | No <sup>(1)</sup> |
| DEBUG | Additionally provides internal state information to allow finding bugs | No | No <sup>(1)</sup> |
| TRACE | Very detailed logging providing detailed internal state information (currently not used) | No | Yes |

As the "Trace" level may expose internal secrets (such as passwords, credentials, or similar), it is **not** recommended to post such logs in Github issue reports without scanning them manually before.

<sup>(1)</sup> Please note that also higher levels (especially "Info" and "Debug") may also contain references to URLs and hostnames, which might be internal to your network. If this is relevant in your case, you might also need to check the content of these log records before posting the log to github.

### Configuration of Log Levels

You may change the log level provided by setting the Spring configuration variable

```
logging.level.org.cloudfoundry.promregator
```

to the corresponding log level mentioned in the table above. You may do so, for example, by specifying the variable in your `pomregator.yml` file like this:

```yaml
[...]
logging:
  level:
    org:
      cloudfoundry:
        promregator: INFO
```

Alternatively, you may also provide a Java system variable via the command line like this:

```bash
java -Dlogging.level.org.cloudfoundry.promregator=INFO -jar promregator-x.y.z-SNAPSHOT.jar
```

or, if you are running the docker container, you may do it like this:

```bash
docker run -d \
 --env JAVA_OPTS=-Dlogging.level.org.cloudfoundry.promregator=INFO \
 -v /path/to/your/own/promregator.yaml:/etc/promregator/promregator.yml \
 promregator/promregator:<version>
```

