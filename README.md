# Promregator Project

The term "Promregator" is an artifical composite of the two terms "Prometheus" and "aggregator".

Prometheus is referring to the [Prometheus project](https://prometheus.io/), which is toolset intended for retrieving telemetry data of running applications. It supports monitoring these metrics and triggering alerts based on thresholds.

Aggregator is referring to the aggregator concept, which you encounter several times in the Platform-as-a-Service offering of Cloud Foundry. 
The most prominent case of an aggregator in Cloud Foundry most likely is the [Loggregator](https://docs.cloudfoundry.org/loggregator/architecture.html).

The Promregator project intends to provide an aggregator-like tool for the Cloud Foundry environment, which allows fetching 
the Prometheus metrics of a set of *Cloud Foundry app instances*. Note that it is not the intention to facilitate scraping of
metrics *on platform level* (e.g. for monitoring services, which are exposed via BOSH containers on the Cloud Foundry platform), but support monitoring of metrics *on application level* (if you are looking for platform monitoring, you might find https://github.com/pivotal-cf/prometheus-on-PCF and https://github.com/bosh-prometheus/prometheus-boshrelease interesting).

## Quickstart

If you want to jumpstart and see what Promregator may do, please refer to our [quickstart description](docs/quickstart-docker.md).

## Feature List

Here is the list of major features provided by Promregator:

* Standard Java-Application implemented using the Spring Framework. There is **no need for administrative privileges on your Cloud Foundry** installation!
  You can even run Promregator **outside of your Cloud Foundry environment** (such as behind an internal firewall), as Promregator also supports proxy servers.
* **Automatic discovery** of instances and hostnames of Cloud Foundry apps supported
  * **Multiple CF apps** in **multiple Orgs** and **multiple spaces** supported
  * **Multiple instances** per app supported, **automatically detecting up- and downscaling** (results are cached, timeout of caching can be configured)
* Fetching of the **Prometheus Metric endpoints** of multiple app instances is performed in **parallel** (number of concurrent threads used for retrieving metrics can be configured)
* **Support of Authentication Schemes** at the CF app's endpoint. The following Authentication schemes are currently available:
  - Basic HTTP Authentication (as of [RFC2617](https://www.ietf.org/rfc/rfc2617.txt))
  - [JWT](https://jwt.io/)-based authentication (with retrieval of JWT from [OAuth2](https://oauth.net/2/) server, JWT is cached)
  - Null Authentication (not recommended for productive environments!)
  
  The Authentication schemes are easily extensible.
* **Configuration using standard Spring properties** as defined by the Spring Framework (e.g. using `application.yml` file).
* Simple **HTTP proxy support** is available for contacting CF app endpoints.
* All metrics provided from the Cloud Foundry applications are **automatically [enriched with additional labels](docs/enrichment.md)**, indicating their origin (similar to the `job` and `instance` labels [created by Prometheus](https://prometheus.io/docs/concepts/jobs_instances/)).
* [Additional metrics are provided](docs/enrichment.md) supporting you to **monitor Promregator** and the **communication to the Cloud Foundry applications**.
* Promregator's endpoint (`/metrics`) supports **GZIP compression**, if the clients indicates to accept it.


## Architecture
![Architecture of Promregator](docs/architecture.png)

Promregator is sitting between your Prometheus server on the one hand and talks to your Cloud Foundry apps on the other hand. 
It is converting the Prometheus' scraping requests into queries targeting your instances of your apps, which are running on Cloud Foundry. 

For further details on the architecture of Promregator, please look at the [architecture page](docs/architecture.md).


## Prerequisites

Running Promregator requires:
* JRE8 or higher

Compiling Promregator additionally requires:
* Maven 3.3.0 or higher

Further dependencies required are automatically loaded when building the software. An internet connection to [Maven Central](https://search.maven.org/) is necessary for that.

## Installation

Promregator has been [released first](https://github.com/promregator/promregator/releases/tag/v0.1.0) on 2018-03-18. It currently provides two binary-based deliveries:

* You may download versions of the released jar file at [https://github.com/promregator/promregator/releases](https://github.com/promregator/promregator/releases).
* You may use the prepared Docker container which is available at [https://hub.docker.com/r/promregator/promregator/](https://hub.docker.com/r/promregator/promregator/). Hints on suggested parameters can be found at [this page](docs/docker-start.md).

Furthermore, you may create your own package by downloading the source code, unpacking it into a local folder and calling

```bash
mvn clean package
```

The runnable artifact will be available at `target/promregator-*-SNAPSHOT.jar`. It is a self-contained (e.g. including all library dependencies) JAR file, which can be copied around and executed at an arbitrary folder by calling

```bash
java -jar promregator-x.y.z-SNAPSHOT.jar
```

## Configuration (of Promregator)

The configuration may be performed using any variant of the [Spring Property Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html) 
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

From the perspective of Prometheus, Promregator behaves like any other target. 
You may set up a scraping job for example like this:

```yaml
[...]
scrape_configs:
  - job_name: 'prometheus'
    scheme: http
    static_configs:
      - targets: ['hostname-of-promregator:8080']
```

Note that the option `honor_labels: true` is **not** required. 

Authentication currently is not required / not available (yet). For the current progress on this topic, please refer to [#17](https://github.com/promregator/promregator/issues/17).
