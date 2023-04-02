# Configuration Options

This page describes the various configuration options which Promregator currently supports.
It is suggested to configure Promregator using a YAML file as described on the [main page](../README.md).

## Group "cf"

The group "cf" defines the way how Promregator connects the API server of the Cloud Foundry platform. 

Example:
```yaml
cf:
  api_host: api.cf.example.org
  username: myCFUserName
  proxy:
    host: 192.168.111.1
    port: 8080
```

### Option "cf.api_host" (mandatory)
Specifies the hostname of the API server of the Cloud Foundry platform to which Promregator shall connect.
The API server is required to resolve the targets into instances which Prometheus then will connect to.

Note that this compliant to how [cf-exporter](https://github.com/bosh-prometheus/cf_exporter) is being configured.

*NOTE*
Specify the hostname only here. Do not prefix it with `https://` or similar. Promregator will always try to connect to the API host of the Cloud Foundry platform using the HTTPS protocol (HTTP-only currently is not supported). This holds true, even if you only have an HTTP proxy as mentioned below.

### Option "cf.username" (mandatory)
Specifies the username, which shall be used when connecting to the API server. This is your "standard" username - the same one, which you also may use
for connecting to Cloud Foundry, e.g. when issuing `cf login`.

Note that this compliant to how [cf-exporter](https://github.com/bosh-prometheus/cf_exporter) is being configured.

### Option "cf.password" (mandatory)
Specifies the password, which shall be used when connecting to the API server. This your "standard" password - the same one, which you also may use
for connecting to Cloud Foundry, e.g. when issuing `cf login`.

Note that this compliant to how [cf-exporter](https://github.com/bosh-prometheus/cf_exporter) is being configured.

*WARNING!* 
Due to security reasons, it is recommended *not* to store this value in your YAML file, but instead set the special environment variable `CF_PASSWORD` when starting the application. Note that the environment variable `CF_EXPORTER_CF_PASSWORD`, which [cf-exporter](https://github.com/bosh-prometheus/cf_exporter) uses, is **not** supported by Promregator.

Example:

```bash
export CF_PASSWORD=mysecretPassword
java -Dspring.config.location=file:/path/to/your/myconfig.yaml -jar promregator-0.0.1-SNAPSHOT.jar
```

### Option "cf.skipSslValidation" (optional)
This option became available starting with version 0.2.0.

Allows to disable the SSL/TLS certificate validation when talking to the Cloud Foundry API host at the servers reported via `cf.api_host`. This is usually necessary, if your Cloud Foundry platform is only equipped with a self-signed certificate, or a certificate, which the Java Virtual Machine is not aware of (NB: the default-provided docker image only is aware of the publicly-known Root certificates as defined by the underlying operating system Ubuntu). 

By default, this option is disabled, which means that the validation is performed. If the validation fails, you typically get the following error message:

```
Caused by: sun.security.validator.ValidatorException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target
```

*WARNING!*
Enabling this option (and thus *disabling* the validation) may make you vulnerable to [man-in-the-middle attacks](https://en.wikipedia.org/wiki/Man-in-the-middle_attack). Thus, this option should never be enabled in a productive environment, but may only be used for testing purpose in a properly controlled environment.


### Option "cf.watchdog.enabled" (optional)
This option became available starting with version 0.8.0.

Allows to enable a periodic check of the connection to the Cloud Foundry Cloud Controller if set to `true`. If the check fails, a warning message is logged by the logger `org.cloudfoundry.promregator.cfaccessor.ReactiveCFAccessorImpl` (the exact name of the logger is subject to possible future change) and an attempt to re-establishing the connection is made. 

The default of this option is `false`.

Refer to [issue #83](https://github.com/promregator/promregator/issues/83) to see a scenario where this might be helpful.

### Option "cf.watchdog.timeout" (optional)
This option became available starting with version 0.8.0.

Specifies the timeout in milliseconds after which the watchdog shall consider a connection test to the Cloud Foundry Cloud Controller to be considered failing. 

The default value is 2500 (milliseconds).

Refer to [issue #83](https://github.com/promregator/promregator/issues/83) to see a scenario where this might be helpful.

### Option "cf.watchdog.rate" (optional)
This option became available starting with version 0.8.0.

This option requires that you have `cf.watchdog.enabled` set to `true`.

Allows to specify how often the periodic check of the connection to the Cloud Foundry Cloud Controller shall be performed. A higher value indicates less frequent checking.
The unit of this value is in seconds. 

The default value is 60 (seconds).

Refer to [issue #83](https://github.com/promregator/promregator/issues/83) to see a scenario where this might be helpful.

### Option "cf.watchdog.initialDelay" (optional)
This option became available starting with version 0.8.0.

This option requires that you have `cf.watchdog.enabled` set to `true`.

Allows to specify an initial delay (after Promregator has started) before the periodic check of the connection to the Cloud Foundry Cloud Controller is started. 
The unit of this value is in seconds. 

The default value is 60 (seconds).

Refer to [issue #83](https://github.com/promregator/promregator/issues/83) to see a scenario where this might be helpful.

### Option "cf.watchdog.restartCount" (optional)
This option became available starting with version 0.8.0.

This option requires that you have `cf.watchdog.enabled` set to `true`.

Allows to specify the number of reconnection attempts to the Cloud Foundry Cloud Controller until Promregator is terminating with exit code 161. The idea is to restart Promregator in this case. 

By default no value is specified, which disables this option., i.e. no restart is triggered.

Refer to [issue #83](https://github.com/promregator/promregator/issues/83) to see a scenario where this might be helpful.


### Option "cf.connectionPool.size" (optional)
This option became available starting with version 0.6.0.

When communicating with the Cloud Foundry Cloud Controller, a fixed-sized connection pool (operated by reactor-netty) gets used. It defines the maximal number of connections which may be open at the same point in time. By default, netty-reactor sets this value based on the following formula:

```
Number of concurrent connections = max(cores available, 8) * 2
```

Due to peak load situations on cache refreshing, it may happen that this value is not sufficient. The symptom the usually is that latency of the Cloud Controller is higher than expected. A typical situation where this may happen is, if you have more than 4-8 spaces configured.

If not specified otherwise, the default of netty-reactor is taken. If specified, the integer number indicating the requested connection pool size is expected.

### Option "cf.threadPool.size" (optional)
This option became available starting with version 0.6.0.

When communicating with the Cloud Foundry Cloud Controller, a fixed-sized connection pool (operated by reactor-netty) gets used. Connection handling takes place using an I/O thread pool, which is operated by reactor-netty. 

 By default, netty-reactor sets this value based on the following formula:

```
Number of threads in the pool = max(cores available, 4)
```

Due to peak load situations on cache refreshing, it may happen that this value is not sufficient. The symptom the usually is that latency of the Cloud Controller is higher than expected.

If not specified otherwise, the default of netty-reactor is taken. If specified, the integer number indicating the desired size of the thread pool is expected.

*WARNING!*
Do not exaggerate this value by going beyond the number of cores available! Otherwise your system may become unresponsive. In many cases the number of threads in the I/O pool is not the limiting factor. More often the connection pool is responsible for that (see also `cf.connectionPool.size`).

### Option "cf.cache.type" (optional)
This option became available starting with version 0.7.1.

Promregator highly relies on caching the metadata provided by the Cloud Controller of Cloud Foundry. In earlier versions of Promregator, a "classic cache" was available, which was discontinued with version 1.0.0. Version 1.x.y makes use of the Caffeine-based cache by default

Possible values for this option are:

| Value         | Meaning |
|---------------|---------|
| CAFFEINE      | The Caffeine-based cache is being used (default) |

The default value of this option is `CAFFEINE`.

The caches have differences in their behavior. For more details refer to the [page "Cache Types"](cache-types.md).


### Option "cf.cache.timeout.org" (optional)
For performance reasons the metadata of the Cloud Foundry environment (organization, space, applications, routes) is cached locally in Promregator.

This option allows you to specify how often the metadata of the organizations you have selected in your targets shall be verified after it has been fetched. The value is a timeout after which the metadata is retrieved again. Its unit is seconds.

Note that organizations typically do not change often. That is why you should pick a high value here, as otherwise you would produce unnecessary network traffic for everyone.

By default, this value is set to 3600 seconds, which means that the metadata is retrieved (again) after an hour.

Caches can also be invalidated out of line by sending an HTTP REST request to Promregator. Further details can be found at the [Cache Invalidation page](./invalidate-cache.md).

### Option "cf.cache.timeout.space" (optional)
For performance reasons the metadata of the Cloud Foundry environment (organization, space, applications, routes) is cached locally in Promregator.

This option allows you to specify how often the metadata of spaces you have selected in your targets shall be verified after it has been fetched. The value is a timeout after which the metadata is retrieved again. Its unit is seconds.

Note that spaces typically do not change often. That is why you should pick a high value here, as otherwise you would produce unnecessary network traffic for everyone.

By default, this value is set to 3600 seconds, which means that the metadata is retrieved (again) after an hour.

Caches can also be invalidated out of line by sending an HTTP REST request to Promregator. Further details can be found at the [Cache Invalidation page](./invalidate-cache.md).


### Option "cf.cache.timeout.application" (optional)
For performance reasons the metadata of the Cloud Foundry environment (organization, space, applications, routes) is cached locally in Promregator.

This option allows you to specify how often the metadata of the applications and routes you have selected in your targets shall be verified after they have been fetched. The value is a timeout after which the metadata is retrieved again. Its unit is seconds.

By default, this value is set to 300 seconds, which means that the metadata is retrieved every five minutes.

Note that applications and routes *may* change often. That is why you should pick a quite *low* value here to ensure that you do not miss and update for long time. Otherwise, you might get metrics indicating that an app may be down, but in fact it is running, but you only deployed a new version of the app or you changed a route.

Caches can also be invalidated out of line by sending an HTTP REST request to Promregator. Further details can be found at the [Cache Invalidation page](./invalidate-cache.md).

### Option "cf.cache.timeout.domain" (optional)
For performance reasons the metadata of the Cloud Foundry environment (organization, space, applications, routes, domains) is cached locally in Promregator.

This option allows you to specify how often the metadata of the domains you have selected in your targets shall be verified after they have been fetched. The value is a timeout after which the metadata is retrieved again. Its unit is seconds.

By default, this value is set to 3600 seconds, which means that the metadata is retrieved (again) after an hour.

Note that domains typically do not change often. That is why you should pick a high value here, as otherwise you would produce unnecessary network traffic for everyone.

Caches can also be invalidated out of line by sending an HTTP REST request to Promregator. Further details can be found at the [Cache Invalidation page](./invalidate-cache.md).

### Option "cf.cache.timeout.route" (optional)
For performance reasons the metadata of the Cloud Foundry environment (organization, space, applications, routes, domains) is cached locally in Promregator.

This option allows you to specify how often the metadata of the routes you have selected in your targets shall be verified after they have been fetched. The value is a timeout after which the metadata is retrieved again. Its unit is seconds.

By default, this value is set to 300 seconds, which means that the metadata is retrieved (again) after an hour.

Note that routes typically may change, if you deploy a new version of your application. That is why you should pick a a reasonable value here, typically in the same order of the timeout of applications.

Caches can also be invalidated out of line by sending an HTTP REST request to Promregator. Further details can be found at the [Cache Invalidation page](./invalidate-cache.md).

### Option "cf.cache.timeout.process" (optional)
For performance reasons the metadata of the Cloud Foundry environment (organization, space, applications, routes, domains) is cached locally in Promregator.

This option allows you to specify how often the process information of the applications you have selected in your targets shall be verified after they have been fetched. The value is a timeout after which the metadata is retrieved again. Its unit is seconds.

By default, this value is set to 300 seconds, which means that the metadata is retrieved (again) after an hour.

Note that processes typically may change, if you up-/downscale your application. That is why you should pick a a reasonable value here, typically in the same order of the timeout of applications.

Caches can also be invalidated out of line by sending an HTTP REST request to Promregator. Further details can be found at the [Cache Invalidation page](./invalidate-cache.md).


### Option "cf.cache.timeout.resolver" (optional)
For performance reasons the metadata of the Cloud Foundry environment (organization, space, applications, routes) is cached locally in Promregator.

This option allows you to specify how often the mapping between your configured targets and its resolution to Cloud Foundry Organization names, Cloud Foundry Space names and Cloud Foundry Application names takes place. This cache is used, if you do not explicitly specify the organization name, the space name and the application name in a target (e.g. you left out the application name to select all applications within a space). 

The value is a timeout after which this mapping is invalidated and thus determined again. Its unit is seconds.

By default, this value is set to 300 seconds, which means that the mapping is retrieved every five minutes.

Caches can also be invalidated out of line by sending an HTTP REST request to Promregator. Further details can be found at the [Cache Invalidation page](./invalidate-cache.md).

### Option "cf.cache.expiry.org" (optional)
For performance reasons the metadata of the Cloud Foundry environment (organization, space, applications, routes) is cached locally in Promregator.

This option allows you to specify how long an apparently no-longer-used record should stay in the organization cache, before it is removed. Its unit is seconds.

By default, this value is set to 120 seconds, which means that records, which were not used for more than two minutes, are considered deprecated and removed from the cache.

Caches can also be invalidated out of line by sending an HTTP REST request to Promregator. Further details can be found at the [Cache Invalidation page](./invalidate-cache.md).

### Option "cf.cache.expiry.space" (optional)
For performance reasons the metadata of the Cloud Foundry environment (organization, space, applications, routes) is cached locally in Promregator.

This option allows you to specify how long an apparently no-longer-used record should stay in the space cache, before it is removed. Its unit is seconds.

By default, this value is set to 120 seconds, which means that records, which were not used for more than two minutes, are considered deprecated and removed from the cache.

Caches can also be invalidated out of line by sending an HTTP REST request to Promregator. Further details can be found at the [Cache Invalidation page](./invalidate-cache.md).

### Option "cf.cache.expiry.application" (optional)
For performance reasons the metadata of the Cloud Foundry environment (organization, space, applications, routes) is cached locally in Promregator.

This option allows you to specify how long an apparently no-longer-used record should stay in the application cache, before it is removed. Its unit is seconds.

By default, this value is set to 120 seconds, which means that records, which were not used for more than two minutes, are considered deprecated and removed from the cache.

Caches can also be invalidated out of line by sending an HTTP REST request to Promregator. Further details can be found at the [Cache Invalidation page](./invalidate-cache.md).

### Option "cf.cache.expiry.domain" (optional)
For performance reasons the metadata of the Cloud Foundry environment (organization, space, applications, routes, domains) is cached locally in Promregator.

This option allows you to specify how long an apparently no-longer-used record should stay in the domain cache, before it is removed. Its unit is seconds.

By default, this value is set to 300 seconds, which means that records, which were not used for more than five minutes, are considered deprecated and removed from the cache.

Caches can also be invalidated out of line by sending an HTTP REST request to Promregator. Further details can be found at the [Cache Invalidation page](./invalidate-cache.md).

### Option "cf.cache.expiry.route" (optional)
For performance reasons the metadata of the Cloud Foundry environment (organization, space, applications, routes, domains) is cached locally in Promregator.

This option allows you to specify how long an apparently no-longer-used record should stay in the route cache, before it is removed. Its unit is seconds.

By default, this value is set to 120 seconds, which means that records, which were not used for more than two minutes, are considered deprecated and removed from the cache.

Caches can also be invalidated out of line by sending an HTTP REST request to Promregator. Further details can be found at the [Cache Invalidation page](./invalidate-cache.md).

### Option "cf.cache.expiry.process" (optional)
For performance reasons the metadata of the Cloud Foundry environment (organization, space, applications, routes, domains) is cached locally in Promregator.

This option allows you to specify how long an apparently no-longer-used record should stay in the process cache of applications, before it is removed. Its unit is seconds.

By default, this value is set to 120 seconds, which means that records, which were not used for more than two minutes, are considered deprecated and removed from the cache.

Caches can also be invalidated out of line by sending an HTTP REST request to Promregator. Further details can be found at the [Cache Invalidation page](./invalidate-cache.md).

### Option "cf.cache.aggregator.blocksize.route" (optional)
If multiple applications are configured to be scraped by Promregator, the number of requests on fetching route metadata of of the Cloud Foundry environment may become high.
To limit the load Promregator imposes on the CF infrastructure, multiple route requests are being bundled into one big route request to the platform. This process is called "Request Aggregation". 

This parameter configures the maximal number of requests aggregating route data. Its default value is 100.

The logic does not wait until a block is full - this parameter only defines what is the maximal number of requests allowed (upper boundary).

In high load situation (i.e. thousands of applications configured), increasing this value may be counterproductive in that sense that it will cause additional latency: As the platform will require more time to respond to the request, scrape requests will have to wait longer until their individual request for data can be fulfilled.

### Option "cf.cache.aggregator.checkinterval.route" (optional)
If multiple applications are configured to be scraped by Promregator, the number of requests on fetching route metadata of of the Cloud Foundry environment may become high.
To limit the load Promregator imposes on the CF infrastructure, multiple route requests are being bundled into one big route request to the platform. This process is called "Request Aggregation". 

This parameter configures the duration (unit: milliseconds) of how long the Request Aggregator waits for requests to come in, which shall be bundled together in one block.

The default value of this parameter is 125 (milliseconds).

Assuming a value of "cf.cache.aggregator.blocksize.route" to be set to 100 and this parameter to be set to the default value, this means that the Request Aggregator may send out 8 blocks of requests per second with 100 (applications) each. Given a scraping interval of 15 seconds, this means that at maximum (i. e. no caching considered) 12,000 requests can be handled. In case you have more applications configured and you need to increase the performance due to your high load situation, you should *lower* this configuration parameter's value: Cutting it by half (i.e. 62), Promregator may serve double as many applications.

Note, be careful to go below a value of 10 for this parameter: With such a low value, the algorithm will become ineffective - the managerial overhead may consume a lot of CPU cycles unnecessarily. If you are reaching such a high load, consider increasing the value of "cf.cache.timeout.route" instead.

### Option "cf.cache.aggregator.blocksize.process" (optional)
If multiple applications are configured to be scraped by Promregator, the number of requests on fetching route metadata of of the Cloud Foundry environment may become high.
To limit the load Promregator imposes on the CF infrastructure, multiple process requests are being bundled into one big process request to the platform. This process is called "Request Aggregation". 

This parameter configures the maximal number of requests aggregating process data. Its default value is 100.

The logic does not wait until a block is full - this parameter only defines what is the maximal number of requests allowed (upper boundary).

In high load situation (i.e. thousands of applications configured), increasing this value may be counterproductive in that sense that it will cause additional latency: As the platform will require more time to respond to the request, scrape requests will have to wait longer until their individual request for data can be fulfilled.

### Option "cf.cache.aggregator.checkinterval.process" (optional)
If multiple applications are configured to be scraped by Promregator, the number of requests on fetching process metadata of of the Cloud Foundry environment may become high.
To limit the load Promregator imposes on the CF infrastructure, multiple process requests are being bundled into one big process request to the platform. This process is called "Request Aggregation". 

This parameter configures the duration (unit: milliseconds) of how long the Request Aggregator waits for requests to come in, which shall be bundled together in one block.

The default value of this parameter is 125 (milliseconds).

Assuming a value of "cf.cache.aggregator.blocksize.process" to be set to 100 and this parameter to be set to the default value, this means that the Request Aggregator may send out 8 blocks of requests per second with 100 (applications) each. Given a scraping interval of 15 seconds, this means that at maximum (i. e. no caching considered) 12,000 requests can be handled. In case you have more applications configured and you need to increase the performance due to your high load situation, you should *lower* this configuration parameter's value: Cutting it by half (i.e. 62), Promregator may serve double as many applications.

Note, be careful to go below a value of 10 for this parameter: With such a low value, the algorithm will become ineffective - the managerial overhead may consume a lot of CPU cycles unnecessarily. If you are reaching such a high load, consider increasing the value of "cf.cache.timeout.process" instead.


### Option "cf.request.timeout.org" (optional)
During discovery Promregator needs to retrieve metadata from the Cloud Foundry platform. To prevent congestion on requests, which may be caused by ongoing requests of scraping by Prometheus, requests sent to the Cloud Foundry platform have to respond within a certain timeframe (the "request timeout"). 

This option defines the request timeout value for sending requests retrieving data about organizations. Its unit always is specified in milliseconds.

By default, this value is set to 2500 milliseconds.

### Option "cf.request.timeout.space" (optional)
During discovery Promregator needs to retrieve metadata from the Cloud Foundry platform. To prevent congestion on requests, which may be caused by ongoing requests of scraping by Prometheus, requests sent to the Cloud Foundry platform have to respond within a certain timeframe (the "request timeout"). 

This option defines the request timeout value for sending requests retrieving data about spaces. Its unit always is specified in milliseconds.

By default, this value is set to 2500 milliseconds.

### Option "cf.request.timeout.domain" (optional)
During discovery Promregator needs to retrieve metadata from the Cloud Foundry platform. To prevent congestion on requests, which may be caused by ongoing requests of scraping by Prometheus, requests sent to the Cloud Foundry platform have to respond within a certain timeframe (the "request timeout").

This option defines the request timeout value for sending requests retrieving data about domains. Its unit always is specified in milliseconds.

By default, this value is set to 2500 milliseconds.

### Option "cf.request.timeout.route" (optional)
During discovery Promregator needs to retrieve metadata from the Cloud Foundry platform. To prevent congestion on requests, which may be caused by ongoing requests of scraping by Prometheus, requests sent to the Cloud Foundry platform have to respond within a certain timeframe (the "request timeout").

This option defines the request timeout value for sending requests retrieving data about routes. Its unit always is specified in milliseconds.

By default, this value is set to 2500 milliseconds.

### Option "cf.request.timeout.process" (optional)
During discovery Promregator needs to retrieve metadata from the Cloud Foundry platform. To prevent congestion on requests, which may be caused by ongoing requests of scraping by Prometheus, requests sent to the Cloud Foundry platform have to respond within a certain timeframe (the "request timeout").

This option defines the request timeout value for sending requests retrieving data about processes of applications. Its unit always is specified in milliseconds.

By default, this value is set to 2500 milliseconds.


### Option "cf.request.timeout.appInSpace" (optional)
During discovery Promregator needs to retrieve metadata from the Cloud Foundry platform. To prevent congestion on requests, which may be caused by ongoing requests of scraping by Prometheus, requests sent to the Cloud Foundry platform have to respond within a certain timeframe (the "request timeout"). 

This option defines the request timeout value for sending requests retrieving a list of applications within a space. Its unit always is specified in milliseconds.

By default, this value is set to 2500 milliseconds.


### Option "cf.request.backoff" (optional)

When Promregator is sending metadata requests to the Cloud Foundry platform and receives an error, it will automatically retry the request once more. However, it will not do so immediately on receiving the error (as this could lead to flooding of an already failing server). Instead it will retry only after waiting for a short amount of time - the backoff interval. Moreover, an additional random delay (of up to 50% of the backoff interval) is added to prevent load peaks caused by parallel attempts to help a potentially heavily loaded server to recover.

The higher the backoff interval is set, the easier it is for the Cloud Foundry platform to recover from an error situation.

It is not recommended to set the backoff interval to a value larger than 2/3 of the scraping interval. If you do, you may risk Promregator to become unstable due to large amounts of queued metadata requests.

The unit of this option is in milliseconds. By default, this value is set to 500 milliseconds.

### Option "cf.request.rateLimit" (optional)

Promregator is able to send large amounts of requests to the Cloud Foundry platform. Due to its design, in large environments it is even possible that too many requests are sent in a too short time. In this case, the Cloud Foundry platform may [take protective measures for self-protection](https://docs.cloudfoundry.org/running/rate-limit-cloud-controller-api.html). Due to this, corresponding requests will fail, because they will be completed with various types of error messages.

This option defines the number of requests per second that Promregator is allowed to send for fetching metadata from the Cloud Foundry platform. Requests which exceed this threshold are queued and will be processed only after enough "capacity" is available again.

By default, this value is set to 0 (zero), which means that rate limiting is disabled.

In contrast to many other settings in this document, the type of this parameter is a float with double precision. So providing values such as `22.5` is acceptable here.


### Subgroup "cf.proxy"

#### Option "cf.proxy.host" (optional)
This option became available starting with version 0.6.4 and 0.7.0.

If you want to make the system establish the connection to the API host using an HTTP (sorry, HTTPS not supported yet) proxy, enter the IP address or the hostname of this server here. If a hostname is given, it must be resolvable locally (i.e. by Promregator).

Please also make sure that you set "cf.proxy.port", too, as otherwise proxy support will be disabled.

Note: This configuration option will *not* be used for accessing the targets in any case. Use configuration options "promregator.scraping.proxy" instead.

#### Option "cf.proxy.port" (optional)
This option became available starting with version 0.6.4 and 0.7.0.

If you want to make the system establish the connection to the API host using an HTTP (sorry, HTTPS not supported yet) proxy, enter the port number of this server here.

Please also make sure that you set "cf.proxy.host", too, as otherwise proxy support will be disabled.

## Group "promregator"
This group configures the behavior of Promregator itself. It is mainly meant on how requests shall be handled, as soon as the Prometheus server starts to pull metrics.

#### Option "promregator.defaultInternalRoutePort" (optional)
This option became available starting with version 0.9.0.

Specifies the default port to use for internal routes if no `internalRoutePort` is defined on a target. The default value for this is port `8080`.

### Subgroup "promregator.targets"
Lists one or more Cloud Foundry applications, which shall be queried for metrics.
The subgroup expects an item list, which contains additional mandatory properties

#### Item property "promregator.targets[].orgName" (optional)
Specifies the name of the Cloud Foundry Organization which hosts the application, which you want to query for metrics.

To ensure consistency with the behavior of many Cloud Foundry implementations, the name is treated case-**in**sensitively since Promregator version 0.5.0.

If left out (and also "orgRegex" is omitted), *all* organizations are considered as places for targets to searched for.

By this, automatic detection of new applications is possible. Note that discovery of *new* applications within the space only takes place after the timeout of "cf.cache.timeout.resolver" has occurred. To enforce a discovery, you may [invalidate the resolver cache manually](./invalidate-cache.md).

#### Item property "promregator.targets[].orgRegex" (optional)
Specifies the regular expression (based on a [Java Regular expression](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html)) for scanning the organization name, which will contain applications, which you want to query for metrics. Only organizations, for which the name matches to this regular expression, are considered for searching for applications.

To ensure consistency with the behavior of many Cloud Foundry implementations, the comparison is performed case-**in**sensitively since Promregator version 0.5.0.

By this, automatic detection of new applications is possible. Note that discovery of *new* organizations only takes place after the timeout of "cf.cache.timeout.resolver" has occurred. To enforce a discovery, you may [invalidate the resolver cache manually](./invalidate-cache.md).

#### Item property "promregator.targets[].spaceName" (optional)
Specifies the name of the Cloud Foundry Space (within a Cloud Foundry Organization detected before), which hosts the application, which you want to query for metrics.

To ensure consistency with the behavior of many Cloud Foundry implementations, the name is treated case-**in**sensitively since Promregator version 0.5.0.

If left out (and also "spaceRegex" is omitted), *all* spaces within the specified Cloud Foundry Organization are considered as places for targets to be searched for.

By this, automatic detection of new applications is possible. Note that discovery of *new* spaces within the organizations only takes place after the timeout of "cf.cache.timeout.resolver" has occurred. To enforce a discovery, you may [invalidate the resolver cache manually](./invalidate-cache.md).

#### Item property "promregator.targets[].spaceRegex" (optional)
Specifies the regular expression (based on a [Java Regular expression](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html)) for scanning the space name of the Cloud Foundry Application (within the Cloud Foundry Organization), which contains the application, which you want to query for metrics. Only spaces, for which the name matches to this regular expression, are considered for searching for applications. 

To ensure consistency with the behavior of many Cloud Foundry implementations, the comparison is performed case-**in**sensitively since Promregator version 0.5.0.

By this, automatic detection of new applications is possible. Note that discovery of *new* spaces within organizations only takes place after the timeout of "cf.cache.timeout.resolver" has occurred. To enforce a discovery, you may [invalidate the resolver cache manually](./invalidate-cache.md).

#### Item property "promregator.targets[].applicationName" (optional)
Specifies the name of the Cloud Foundry Application (within the Cloud Foundry Organization and Space specified above), which hosts the application, which you want to query for metrics.

To ensure consistency with the behavior of many Cloud Foundry implementations, the name is treated case-**in**sensitively since Promregator version 0.5.0.

If left out (and also "applicationRegex" is omitted), *all* applications within the specified Cloud Foundry Organization and Cloud Foundry Space are considered as targets. Only applications in the Cloud Foundry Application state "STARTED" are considered.

By this, automatic detection of new applications is possible. Note that discovery of *new* applications within the space only takes place after the timeout of "cf.cache.timeout.resolver" has occurred. To enforce a discovery, you may [invalidate the resolver cache manually](./invalidate-cache.md).

#### Item property "promregator.targets[].applicationRegex" (optional)
Specifies the regular expression (based on a [Java Regular expression](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html)) for scanning the application name of the Cloud Foundry Application (within the Cloud Foundry Organization and Space specified above), which hosts the application, which you want to query for metrics. Only applications, for which the name matches to this regular expression, are considered for scraping. 

To ensure consistency with the behavior of many Cloud Foundry implementations, the comparison is performed case-**in**sensitively since Promregator version 0.5.0.

By this, automatic detection of new applications is possible. Note that discovery of *new* applications within the space only takes place after the timeout of "cf.cache.timeout.resolver" has occurred. To enforce a discovery, you may [invalidate the resolver cache manually](./invalidate-cache.md).

#### Item property "promregator.targets[].path" (optional)
Specifies the path under which the application's endpoint provides its Prometheus metrics.

Note that the data returned by this endpoint of the target must comply to the [Text-Based Exposition Format (a.k.a. Text Format "0.0.4")](https://github.com/prometheus/docs/blob/ad9fcc1b0c13ec199358bab9af6913b2ffab95ac/content/docs/instrumenting/exposition_formats.md#text-based-format). If it does not, scraping will fail. 

Defaults to `/metrics`, as this is the value which is suggested by Prometheus.

Note that there may be frameworks out there, which expose their metrics in a different format using the same path `/metrics`, though.
Note that setting `promregator.targets[].kubernetesAnnotations` to `true` will cause that any value set to this parameter is ignored by applications that have annotation `prometheus.io/path` set.

#### Item property "promregator.targets[].overrideRouteAndPath" (optional)
This option became available starting with version 0.11.0.

Specifies the route (and path) to a metric endpoint which is in front of the service metrics endpoint. For example, if the service endpoint is protected with a tool like Ory oathkeeper. In that case, it is not possible to call the service endpoint from promregator, because ORY oathkeeper blocks any access to the service endpoint with no authorization token. Promregator needs to call ORY oathkeeper with this override definition and with a valid token so that oathkeeper forwards the request to the service metrics endpoint. The authorization token could be retrieved with `promregator.authenticator.oauth2xsuaaBasic` for example.

#### Item property "promregator.targets[].kubernetesAnnotations" (optional)
Enables support for the de facto standard Kubernetes Prometheus annotations on your CF applications. This allows each application to "opt-in" to scraping
by specifying the annotation `prometheus.io/scrape: "true"`. Annotations support requires a version of Cloud Foundry with the V3 API otherwise this setting will be ignored. 

For an example on deploying an app with these annotations see [here](https://github.com/cloudfoundry/cf-for-k8s-metric-examples).

Defaults to `false`. Enabling this feature will override the `promregator.targets[].path` when it is specified using the annotation `prometheus.io/path: "/some/other/metrics"`

Does not currently support the `prometheus.io/port` annotation.

#### Item property "promregator.targets[].protocol" (optional)
Specifies the protocol (`http` or `https`) which shall be used to retrieve the metrics.

Defaults to `https` if not set otherwise.

#### Item property "promregator.targets[].authenticatorId" (optional)
Specifies the identifier of the *target-specific* authentication configuration, which shall be used for **outbound authentication**, when this target shall be scraped. 

If not specified, the global authentication configuration is applied for this target.

#### Item property "promregator.targets[].internalRoutePort" (optional)
This option became available starting with version 0.9.0.

Specifies the port to be used if the route selected is identified as an internal domain.

If not specified then the value of `promregator.defaultInternalRoutePort` will be used as the port for all internal routes.

For further information, see also [Internal Routing](./internal-routing.md).


#### Subgroup "promregator.targets[].preferredRouteRegex" (optional)
This option became available starting with version 0.6.0.

Specifies a list of one or more regular expressions (based on a [Java Regular expression](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html)) which can be used to determine the route that shall be taken for this target for scraping. If multiple patterns are provided, the pattern of the application, which matches first (from top to bottom), is taken. 
If no `preferredRouteRegex` is specified (default), the first route provided by the Cloud Foundry Platform is taken (legacy behavior due to compatibility). If `preferredRouteRegex` are specified, but they did not match any of the routes, also the first route is still taken (even if they did not match!).

Note that the routes are without the protocol (http or https) specified. They *may* (but not must) contain paths. A possible value `path` of the same target (as specified here in configuration) is *not* included. 

For a set of examples, refer to the [preferredRouteRegex example page](./preferredRouteRegex-examples.md).


### Subgroup "promregator.discovery"
Configures the way how the discovery endpoint `/discovery` behaves.

#### Option "promregator.discovery.hostname" (optional)
Specifies the name of the host (or the IP address) which shall be used for specifying the target during discovery. As a rule of thumb, this name should always be the name under which Prometheus is capable of reaching Promregator.

Setting this option is optional. If not specified, Promregator tries to auto-detect this value based on the configuration of the underlying operating system and the request, which triggered the service discovery.

Note that in various situations, this auto-detection mechanism may fail (e.g. when running in a Docker container). Setting this option then is recommended.

#### Option "promregator.discovery.port" (optional)
Specifies the port number of the host (or the IP address) which shall be used for specifying the target during discovery. As a rule of thumb, this name should always be the port under which Prometheus is capable of reaching Promregator.

Setting this option is optional. If not specified, Promregator tries to auto-detect this value based on the configuration of the underlying operating system and the request, which triggered the service discovery.

#### Option "promregator.discovery.auth" (optional)
Specifies the way how authentication shall be verified, if a request reaches the endpoint. Valid values are:

* *NONE*: no authentication verification is required (default)
* *BASIC*: an authentication verification using HTTP Basic Authentication is performed. Valid credentials are taken from `promregator.authentication.basic.username` and `promregator.authentication.basic.password`.

#### Option "promregator.discovery.ownMetricsEndpoint" (optional)
Specifies, whether the document provided by the discovery endpoint should contain an additional scraping target, which refers to Promregator's own metrics endpoint, where promregator's own data is exposed (value `true`), or not (value `false`).

Setting this option is optional. If not specified, the option is set to `true` by default. Thus, the metrics endpoint is also mentioned in the JSON document. 

You might want to set this option here to `false`, if you want to run Promregator in a [high-availability setup](./ha-setup.md).


### Subgroup "promregator.discoverer"
Configures how the way how the discoverer (mind the difference to the discover**y**) resolves non-complete target configurations with the help of the metadata provided by Cloud Foundry.

The primary task of the discovere**r** is to resolve target configurations, which do *not* refer to a single application. The discoverer introspects the Cloud Foundry Organizations, Spaces and Applications and provides a list of applications, which are selected for scraping.

#### Option "promregator.discoverer.timeout" (optional)
This option allows you to specify how long (in seconds) the discoverer should consider a once-fetched application to be valid.

The primary relevance of this option is to determine the point in time, how low [(internal) metrics](enrichment.md) generated shall be considered valid. The timeout is automatically prolonged, if the instance still is seen on the Cloud Foundry platform. Once the application is gone (for example, it has been deleted), this timeout (counting from the point of time it was seen last) comes into play. If the application does not reappear within this time frame, also the samples of the internal metrics will be deleted.
If the application reappears after the deletion, it is considered "a new application" and the internal metrics start from zero.

The default value is 600 seconds (i.e. 10 minutes).


### Subgroup "promregator.endpoint"
Configures the way how the metrics endpoints `/metrics` and `/singleTargetMetrics` behave.

#### Option "promregator.endpoint.auth" (optional)
Specifies the way how authentication shall be verified, if a request reaches the scraping endpoints of Promregator (e.g. `/metrics` and `/singleTargetMetrics`). Valid values are:

* *NONE*: no authentication verification is required (default)
* *BASIC*: an authentication verification using HTTP Basic Authentication is performed. Valid credentials are taken from `promregator.authentication.basic.username` and `promregator.authentication.basic.password`.


### Subgroup "promregator.scraping"
Configures the way how the scraping is performed.

#### Option "promregator.scraping.maxProcessingTime" (optional)
Specifies the maximal time which may be used to query (all) targets. The value is expected to be specified in milliseconds. 
Targets which did not respond after this amount of time are considered non-functional and no result will be returned to the Prometheus server.

The unit of this configuration option is milliseconds.

The default value of this option is 5000 (=5 seconds).

Warning! The value should always be lower than the (shortest) scraping interval you expose Promregator to.

#### Option "promregator.scraping.connectionTimeout" (optional)
Specifies the maximal time which may be used for establishing a connection to a single target. If this timeout is reached, the target is considered unreachable and no metrics will be transferred.

The unit of this configuration option is milliseconds.

The default value of this option is 5000 (=5 seconds).

Warning! The value should always be lower than the the maximal processing time (`promregator.scraping.maxProcessingTime`).

#### Option "promregator.scraping.socketReadTimeout" (optional)
Specifies the maximal time which may pass between two received packets from a single target. If this timeout is reached, the target is considered unreachable and no metrics will be transferred. The connection established will be aborted. Metrics, which might have been read (partially) already, will be ignored (as the request is considered incomplete and probably inconsistent).

The unit of this configuration option is milliseconds.

The default value of this option is 5000 (=5 seconds).

Warning! The value should always be lower than the the maximal processing time (`promregator.scraping.maxProcessingTime`).

#### Option "promregator.scraping.threads" (optional)
Specifies how many threads may be used to query the list of targets.
Note that for each target request sent, an own thread is required and stays blocked (synchronously) until the Cloud Foundry Application has returned a response. 
Thus, it may be reasonable to allow more threads than you have cores in your environment where Promregator is running.

In general, the more targets you have registered, the higher this value should be. However, running too many threads is simply a waste of resources. As an upper boundary, it does not make sense to allow more threads to run than you have specified targets in your configuration.

Note that if you increase this value, more threads will spawned inside of Promregator. Be aware that these threads may consume memory and thus it will have impact on your memory configuration. For details on the latter, also refer to the [Java Memory Configuration page](jvm-mem-config.md).

The default value of this option is 5.

#### Subsubgroup "promregator.scraping.proxy"

##### Option "promregator.scraping.proxy.host" (optional)
This option became available starting with version 0.6.4 and 0.7.0.

If you want to make the system establish the connection to the application (containers) using an HTTP (sorry, HTTPS not supported yet) proxy, enter the IP address or the hostname of this server here. If a hostname is given, it must be resolvable locally (i.e. by Promregator).

Please also make sure that you set "promregator.scraping.proxy.port", too, as otherwise proxy support will be disabled.

##### Option "promregator.scraping.proxy.port" (optional)
This option became available starting with version 0.6.4 and 0.7.0.

If you want to make the system establish the connection to the application (containers) using an HTTP (sorry, HTTPS not supported yet) proxy, enter the port number of this server here.

Please also make sure that you set "promregator.scraping.proxy.host", too, as otherwise proxy support will be disabled.

### Subgroup "promregator.metrics"
Configures the way how the promregator shall expose its own-generated metrics via the endpoints `/metrics` and `/promregatorMetrics`.

#### Option "promregator.metrics.auth" (optional)
Specifies the way how authentication shall be verified, if a request reaches the endpoint `/promregatorMetrics`. Valid values for this option are:

* *NONE*: no authentication verification is required (default)
* *BASIC*: an authentication verification using HTTP Basic Authentication is performed. Valid credentials are taken from `promregator.authentication.basic.username` and `promregator.authentication.basic.password`.

#### Option "promregator.metrics.requestLatency" (optional)
A boolean which specifies, if the additional metrics `promregator_request_latency`, a histogram metric measuring the latency generated for each Cloud Foundry Application Instance, shall be recorded and exposed to Prometheus. 

The default of this option is "false", which disables the recording. 

If you have many targets (or many instances) which shall be scraped, the data volume of this metric may become huge and thus may cause performance issues (especially w.r.t. memory throughput and thus cause high load situations for the JVM garbage collector). That is why the default is set to "false".

#### Option "promregator.metrics.internal" (optional)
Specifies, if additional internal metrics shall be exposed describing the internal state of Promregator.

The default value of this option is `false`, which disables the exposure. 

Note that these metrics are not meant for productive usage. As they are primarily meant for facilitating debugging issues in Promregator, their naming and labels may change at any point in time without further notice.

#### Option "promregator.metrics.labelNamePrefix" (optional)
Specifies a prefix for label names of own-generated metrics. The prefix will be added to the label names

* `org_name`
* `space_name`
* `app_name`
* `cf_instance_id` and
* `cf_instance_number`

Usually, if specified, you want to have the value of this configuration option end with an underscore (`_`).

If you specify this configuration option as `target_`, then `org_name` would become `target_org_name`.


### Subgroup "promregator.authenticator"
Configures the way how authentication shall happen between Promregator and the targets configured above (**outbound** authentication). Mind the difference to the settings provided in `promregator.authentication`!

This subgroup only defines the "fallback case" of the outbound authentication to targets ("Global Authentication"). 

Configuration of outbound authentication may become complex. Therefore, for further details (including a verbose example), refer to the [outbound authentication page](./outbound-authentication.md).

*Note!*
This option does not have any influence on how Promregator authenticates to the Cloud Foundry platform's API, but only has an impact to the way how Promregator tries to authenticate on scraping Cloud Foundry Applications.

#### Option "promregator.authenticator.type" (mandatory)
Specifies the type of the Authenticator which shall be used when connecting to targeted Cloud Foundry Applications as a fallback ("Global Authentication"). 

If this option is omitted, a "Null authentication scheme" is applied, e.g. no authentication takes places in case that global authentication takes place.


#### Option "promregator.authenticator.basic.username" (mandatory, if using promregator.authenticator.type=basic)
specifies the username which is being used for authenticating the call to the Prometheus client (CF application) in case of global authentication.

#### Option "promregator.authenticator.basic.password" (mandatory, if using promregator.authenticator.type=basic)
Specifies the password which is being used for authenticating the call to the Prometheus client (CF application) in case of global authentication.

*WARNING!* 
Due to security reasons, it is *neither* recommended to store this value in your YAML file, nor to put it into the command line when starting Promregator.
Instead it is suggested to set the corresponding environment variable `PROMREGATOR_AUTHENTICATOR_BASIC_PASSWORD` when starting the application.

Example:

```bash
export PROMREGATOR_AUTHENTICATOR_BASIC_PASSWORD=myPassword
java -Dspring.config.location=file:/path/to/your/myconfig.yaml -jar promregator-0.0.1-SNAPSHOT.jar
```

#### Option "promregator.authenticator.oauth2xsuaaBasic.tokenServiceURL" (mandatory, if using promregator.authenticator.type=OAuth2XSUAABasic)
Specifies the URL of the OAuth2 endpoint, which contains the token service of your authorization server in case of global authentication. Typically, this is the endpoint with the path `/oauth/token`, as Promregator will try to perform to establish a ["Client Credentials"-based authentication](https://www.digitalocean.com/community/tutorials/an-introduction-to-oauth-2#grant-type-client-credentials).

#### Option "promregator.authenticator.oauth2xsuaaBasic.client_id" (mandatory, if using promregator.authenticator.type=OAuth2XSUAABasic)
Specifies the client identifier (a.k.a. "client_id") which shall be used during the OAuth2 request based on the Grant Type Client Credentials flow in case of global authentication.

#### Option "promregator.authenticator.oauth2xsuaaBasic.client_secret" (mandatory, if using promregator.authenticator.type=OAuth2XSUAABasic)
Specifies the client secret (a.k.a. "client_secret") which shall be used during the OAuth2 request based on the Grant Type Client Credentials flow in case of global authentication.

*WARNING!*
Due to security reasons, it is *neither* recommended to store this value in your YAML file, nor to put it into the command line when starting Promregator.
Instead it is suggested to set the corresponding environment variables `PROMREGATOR_AUTHENTICATOR_OAUTH2XSUAA_BASIC_CLIENT_SECRET` when starting the application.

Example:

```bash
export PROMREGATOR_AUTHENTICATOR_OAUTH2XSUAA_BASIC_CLIENT_SECRET=myClientSecret
java -Dspring.config.location=file:/path/to/your/myconfig.yaml -jar promregator-0.0.1-SNAPSHOT.jar
```

#### Option "promregator.authenticator.oauth2xsuaaBasic.scopes" (optional, only available if using promregator.authenticator.type=OAuth2XSUAABasic)
Specifies the set of scopes/authorities (format itself is a comma-separated string of explicit scopes, see also https://www.oauth.com/oauth2-servers/access-tokens/client-credentials/), which shall be requested from the OAuth2 server when using the Grant Type Client Credentials flow in case of global authentication.

#### Option "promregator.authenticator.oauth2xsuaaCertificate.tokenServiceCertURL" (mandatory for if using promregator.authenticator.type=OAuth2XSUAACertificate)
Specifies the URL of the OAuth2 endpoint for certificate-based authentication, which contains the token service of your authorization server in case of global authentication. Typically, this is the endpoint with the path `/oauth/token`, as Promregator will try to perform to establish a ["Client Credentials"-based authentication](https://www.digitalocean.com/community/tutorials/an-introduction-to-oauth-2#grant-type-client-credentials). Note that this attribute is called `tokenServiceCertURL` and not `tokenServiceURL`!

#### Option "promregator.authenticator.oauth2xsuaaCertificate.client_id" (mandatory, if using promregator.authenticator.type=OAuth2XSUAACertificate)
Specifies the client identifier (a.k.a. "client_id") which shall be used during the OAuth2 request based on the Grant Type Client Credentials flow in case of global authentication.

#### Option "promregator.authenticator.oauth2xsuaaCertificate.client_certificates" (mandatory if using promregator.authenticator.type=OAuth2XSUAACertificate)
Specifies the certificate chain which shall be used during the OAuth2 request.

#### Option "promregator.authenticator.oauth2xsuaaCertificate.client_key" (mandatory, if using promregator.authenticator.type=OAuth2XSUAACertificate)
Specifies the private key which shall be used during the OAuth2 request.

*WARNING!* 
Due to security reasons, it is *neither* recommended to store this value in your YAML file, nor to put it into the command line when starting Promregator.
Instead it is suggested to set the corresponding environment variables `PROMREGATOR_AUTHENTICATOR_OAUTH2XSUAA_CERTIFICATE_CLIENT_KEY` when starting the application.

Example:

```bash
export PROMREGATOR_AUTHENTICATOR_OAUTH2XSUAA_CERTIFICATE_CLIENT_KEY=myClientKey
java -Dspring.config.location=file:/path/to/your/myconfig.yaml -jar promregator-0.0.1-SNAPSHOT.jar
```

#### Option "promregator.authenticator.oauth2xsuaaCertificates.scopes" (optional, only available if using promregator.authenticator.type=OAuth2XSUAACertificate)
Specifies the set of scopes/authorities (format itself is a comma-separated string of explicit scopes, see also https://www.oauth.com/oauth2-servers/access-tokens/client-credentials/), which shall be requested from the OAuth2 server when using the Grant Type Client Credentials flow in case of global authentication.

### Subgroup "promregator.targetAuthenticators[]"
Configures the way how authentication shall happen between Promregator and the targets configured above (**outbound** authentication). Mind the difference to the settings provided in `promregator.authentication`!

This subgroup only defines the target-specific case of the outbound authentication to targets ("Target-specific Authentication"). Multiple instances may be defined in this subgroup.

Configuration of outbound authentication may become complex. Therefore, for further details (including a verbose example), refer to the [outbound authentication page](./outbound-authentication.md).

*Note!*
This option does not have any influence on how Promregator authenticates to the Cloud Foundry platform's API, but only has an impact to the way how Promregator tries to authenticate on scraping Cloud Foundry Applications.


### Subgroup "promregator.authentication"
Configures the way how **inbound** authentication shall be verified (e.g. between Prometheus and Promregator). Mind the difference to the settings provided in `promregator.authenticator`!

#### Option "promregator.authentication.basic.username" (optional)
Specifies the username, which is used for HTTP Basic (inbound) authentication. 

If not specified, `promregator` is defaulted.


#### Option "promregator.authentication.basic.password" (optional)
Specifies the (plain-text) password, which is used for HTTP Basic (inbound) authentication.

If not specified, a random password is generated during startup. Its value is printed to the standard error device.

### Option "promregator.cache.invalidate.auth" (optional)
Specifies the authentication schema, which shall be used to for the [Cache Invalidation endpoint](./invalidate-cache.md). Valid values are:

* *NONE*: no authentication verification is required (default)
* *BASIC*: an authentication verification using HTTP Basic Authentication is performed. Valid credentials are taken from `promregator.authentication.basic.username` and `promregator.authentication.basic.password`.

### Option "promregator.gc.rate" (optional)

Promregator is quite memory intensive in that sense that it temporarily may allocate quite large chunks of memory. Those memory blocks, however, are released very soon again. However, this may cause a challenge to garbage collection. 

With this option, a rate (measured in seconds) can be specified after which the Java Virtual Machine shall be triggered to perform a (major) garbage collection by calling `System.gc()`. 

By default, a value of 1200 seconds (20 minutes) is configured. In case you experience dumps with error messages
```
java.lang.OutOfMemoryError: Java heap space
```
you may try to *decrease* this value. A value below 120 (seconds) does not make sense, though.

### Option "promregator.reactor.debug" (optional)

For the purpose of debugging issues with asynchronous handling of operations esp. with regard to the communication to Cloud Foundry's Cloud Connector, it is possible to set the Java Reactor framework into a debug mode. This is done, if this option is set to `true`.

Be default, this option is set to `false`.


### Option "promregator.workaround.dnscache.timeout" (optional)

As described in [issue #84](https://github.com/promregator/promregator/issues/84), there may be a situation where the IP address of your Cloud Foundry Cloud Controller changes over time, but the DNS cache of the Java Virtual machine does not refresh its records properly. 

The default value of this option is "-1", which means that Promregator will leave the configuration option of the Java Virtual Machine untouched. 

If you encounter issues that your scraping will suddenly stop at a certain point in time, combined with reports that the metadata (e.g. organizations, spaces cannot be resolved due to timeouts), you may want to set this value to some positive integer. The meaning is the "time-to-live value" of the DNS cache in seconds. 

There are sources which recommend to set this value to 60.

Keep in mind that this setting may expose you the threat of a DNS spoofing attack. 


## Further Options

Besides the options mentioned above, there are multiple further standard Spring properties, which might be useful when using Promregator. 
Note that these options are not in control of Promregator, but are provided here only for the purpose of additional information, as they might come handy.

### Changing the server port, on which Promregator runs
The configuration property `server.port` allows you specify the port on which Promregator listens for requests from the Prometheus server.

If not specified, this option defaults to 8080.

*Hint*
You may also explicitly specify this option on the command line prompt using Java System Properties, for example:

```bash
java -Dserver.port=8181 -jar promregator-0.0.1-SNAPSHOT.jar
```

which would make Promregator run on port 8181.

### Changing the server address, on which Promregator binds
The configuration property `server.address` allows you specify the address on which Promregator listens for requests from the Prometheus server.

If not specified, this option defaults to 0.0.0.0.

*Hint*
You may also explicitly specify this option on the command line prompt using Java System Properties, for example:

```bash
java -Dserver.address=1.2.3.4 -jar promregator-0.0.1-SNAPSHOT.jar
```

which would make Promregator listen on the IP address 1.2.3.4 instead on all interface addresses of the underlying operating system.

### Changing the number of HTTP worker Threads
Promregator has reduced the number of worker threads (defaulted by spring to 200) to 16. Experience has shown that in usual operating environments no more parallel threads than this are required. 

In case that you have a large number of targets which are scraped in Single Target Scraping mode, the number of worker threads might not be sufficient for you. The expected symptom then is that Prometheus is complaining about experiencing latency when scraping targets.

You may define the number of worker threads (and thus overwriting Promregator's default) by defining the spring configuration variable `server.tomcat.max-threads`.


### Changing Logging Levels
Promregator provides extensive logging facilities. The generation of logs on framework, however, are disabled by default, as during normal operation, the generation of these logs could bei either compromising security or might have a severely negative impact to performance.

You may change the default configuration for logging by adjusting the log levels via standard Spring Framework configuration parameters. The configuration options all start with 
```yaml
logging:
  level:
    ...
```
You may specify then the logging level by denoting the corresponding logger's name, for which you want to adjust the level. So for example, thus could read:
```
logging:
  level:
    org:
      promregator:
        testlogger: DEBUG
```
which would set the logger with the name `org.promregator.testlogger` to `DEBUG`.

As with any other configuration option, besides specifying logging levels in the configuration file, you also may specify these options on the command line. 

There are several important loggers, which might be of interest for you:

| Logger Name | Default Level | Purpose |
|-------------|---------------|---------|
| `org.cloudfoundry.promregator` | `WARN` | Global parent logger of all log messages, generated by Pomregator |
| `reactor.mono.OnAssembly` | `WARN` | Java Reactor-based asynchronous process handling, esp. communication with Cloud Foundry's Cloud Connector | 
| `reactor.ipc.netty` | unknown | HTTP reactor-based communication between Promregator and Cloud Connector (low-level), see also https://stackoverflow.com/q/47596571 |
| `cloudfoundry-client` | unknown | single-line log for all requests (and responses) the cloudfoundry-client library sends and receives. The response line also contains a brief information about the latency the request had. |

Note that this list is not considered to be stable; the logger's name represent internal components, whose design and implementation may change.
