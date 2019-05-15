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
  proxyHost: 192.168.111.1
  proxyPort: 8080
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

### Option "cf.proxyHost" (optional)
If you want to make the system establish the connection to the API host using an HTTP (sorry, HTTPS not supported yet) proxy, enter the *IP address* of this server here.

Please also make sure that you set "cf.proxyPort", too, as otherwise proxy support will be disabled.

*HINT*
For the time being (i.e. this will change in future) the proxy specified here will also be used to access the targets.

### Option "cf.proxyPort" (optional)
If you want to make the system establish the connection to the API host using an HTTP (sorry, HTTPS not supported yet) proxy, enter the port number 
of this server here.

Please also make sure that you set "cf.proxyHost", too, as otherwise proxy support will be disabled.

### Option "cf.skipSslValidation" (optional)
This option became available starting with version 0.2.0.

Allows to disable the SSL/TLS certificate validation when talking to the Cloud Foundry API host at the servers reported via `cf.api_host`. This is usually necessary, if your Cloud Foundry platform is only equipped with a self-signed certificate, or a certificate, which the Java Virtual Machine is not aware of (NB: the default-provided docker image only is aware of the publicly-known Root certificates as defined by the underlying operating system Ubuntu). 

By default, this option is disabled, which means that the validation is performed. If the validation fails, you typically get the following error message:

```
Caused by: sun.security.validator.ValidatorException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target
```

*WARNING!*
Enabling this option (and thus *disabling* the validation) may make you vulnerable to [man-in-the-middle attacks](https://en.wikipedia.org/wiki/Man-in-the-middle_attack). Thus, this option should never be enabled in a productive environment, but may only be used for testing purpose in a properly controlled environment.


### Option "cf.watchdog" (optional)
This option became available starting with version 0.5.5.

Allows to enable a periodic check of the connection to the Cloud Foundry Cloud Controller if set to `true`. If the check fails, a warning message is logged by the logger `org.cloudfoundry.promregator.cfaccessor.ReactiveCFAccessorImpl` (the exact name of the logger is subject to possible future change) and an attempt to re-establishing the connection is made. 

The default of this option is `false`.

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


### Option "cf.request.timeout.org" (optional)
During discovery Promregator needs to retrieve metadata from the Cloud Foundry platform. To prevent congestion on requests, which may be caused by ongoing requests of scraping by Prometheus, requests sent to the Cloud Foundry platform have to respond within a certain timeframe (the "request timeout"). 

This option defines the request timeout value for sending requests retrieving data about organizations. Its unit always is specified in milliseconds.

By default, this value is set to 2500 milliseconds.

### Option "cf.request.timeout.space" (optional)
During discovery Promregator needs to retrieve metadata from the Cloud Foundry platform. To prevent congestion on requests, which may be caused by ongoing requests of scraping by Prometheus, requests sent to the Cloud Foundry platform have to respond within a certain timeframe (the "request timeout"). 

This option defines the request timeout value for sending requests retrieving data about spaces. Its unit always is specified in milliseconds.

By default, this value is set to 2500 milliseconds.

### Option "cf.request.timeout.app" (deprecated)
This option is no longer in use. Use `cf.request.timeout.appInSpace` instead.


### Option "cf.request.timeout.appInSpace" (optional)
During discovery Promregator needs to retrieve metadata from the Cloud Foundry platform. To prevent congestion on requests, which may be caused by ongoing requests of scraping by Prometheus, requests sent to the Cloud Foundry platform have to respond within a certain timeframe (the "request timeout"). 

This option defines the request timeout value for sending requests retrieving a list of applications within a space. Its unit always is specified in milliseconds.

By default, this value is set to 2500 milliseconds.


### Option "cf.request.timeout.routeMapping" (optional)
This option became obsolete with version 0.5.0. If applicable, consider using `cf.request.timeout.appSummary` instead.
Any value specified for this option will be ignored in higher versions.

### Option "cf.request.timeout.route" (optional)
This option became obsolete with version 0.5.0. If applicable, consider using `cf.request.timeout.appSummary` instead.
Any value specified for this option will be ignored in higher versions.

### Option "cf.request.timeout.sharedDomain" (optional)
This option became obsolete with version 0.5.0. If applicable, consider using `cf.request.timeout.appSummary` instead.
Any value specified for this option will be ignored in higher versions.

### Option "cf.request.timeout.process" (optional)
This option became obsolete with version 0.5.0. If applicable, consider using `cf.request.timeout.appSummary` instead.
Any value specified for this option will be ignored in higher versions.

### Option "cf.request.timeout.appSummary" (optional)
During discovery Promregator needs to retrieve metadata from the Cloud Foundry platform. To prevent congestion on requests, which may be caused by ongoing requests of scraping by Prometheus, requests sent to the Cloud Foundry platform have to respond within a certain timeframe (the "request timeout"). 

This option defines the request timeout value for sending requests retrieving a detailed (summary) configurations of applications within a space. Its unit always is specified in milliseconds.

By default, this value is set to 4000 milliseconds.


## Group "promregator"
This group configures the behavior of Promregator itself. It is mainly meant on how requests shall be handled, as soon as the Prometheus server starts to pull metrics.

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

#### Item property "promregator.targets[].protocol" (optional)
Specifies the protocol (`http` or `https`) which shall be used to retrieve the metrics.

Defaults to `https` if not set otherwise.

#### Item property "promregator.targets[].authenticatorId" (optional)
Specifies the identifier of the *target-specific* authentication configuration, which shall be used for **outbound authentication**, when this target shall be scraped. 

If not specified, the global authentication configuration is applied for this target.


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

#### Option "promregator.endpoint.maxProcessingTime" (optional, *deprecated*)
This option is deprecated since version 0.5.0. Please use `promregator.scraping.maxProcessingTime` instead.

#### Option "promregator.endpoint.threads" (optional, *deprecated*)
This option is deprecated since version 0.5.0. Please use `promregator.scraping.threads` instead.

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

#### Option "promregator.scraping.labelEnrichment" (optional)
Specifies if [label enrichment](./enrichment.md) for metrics shall take place, if **Single Target Scraping is being used**.

Due to compatibility reasons, the default value of this options is "true".

This configuration options does *not* have any influence on scraping, if Single **Endpoint** Scraping is used (this is due to the fact that label enrichment must take place for Single Endpoint Scraping, as otherwise the metrics could overwrite each other). 

If this option is set to "true" and Single Target Scraping is used, then - even though the necessary information is available via the "file_sd_configs" metadata - label enrichment takes place for all metrics returned by Promregator. Due to compatibility reasons this is necessary.

If this option is set to "false" and Single Target Scraping is used, then label enrichment does not take place for any metric returned by Promregator (for the scraping endpoints). Note that with Prometheus' feature of label rewriting, it is still possible to have labels in place. For recommendations on this refer the [enrichment's documentation](./enrichment.md).

Note that the metrics generated by Promregator itself are not affected by this setting. They still will be enriched by its labels accordingly.


### Subgroup "promregator.metrics"
Configures the way how the promregator shall expose its own-generated metrics via the endpoints `/metrics` and `/promregatorMetrics`.

#### Option "promregator.metrics.auth" (optional)
Specifies the way how authentication shall be verified, if a request reaches the endpoint `/promregatorMetrics`. Note that the authentication verification of `/metrics` is controlled by `promregator.endpoint.auth`. Valid values for this option are:

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
Instead it is suggested to set the identically named environment variable `PROMREGATOR_AUTHENTICATOR_BASIC_PASSWORD` when starting the application.

Example:

```bash
export PROMREGATOR_AUTHENTICATOR_BASIC_PASSWORD=myPassword
java -Dspring.config.location=file:/path/to/your/myconfig.yaml -jar promregator-0.0.1-SNAPSHOT.jar
```

#### Option "promregator.authenticator.oauth2xsuaa.tokenServiceURL" (mandatory, if using promregator.authenticator.type=OAuth2XSUAA)
Specifies the URL of the OAuth2 endpoint, which contains the token service of your authorization server in case of global authentication. Typically, this is the endpoint with the path `/oauth/token`, as Promregator will try to perform to establish a ["Client Credentials"-based authentication](https://www.digitalocean.com/community/tutorials/an-introduction-to-oauth-2#grant-type-client-credentials).

#### Option "promregator.authenticator.oauth2xsuaa.client_id" (mandatory, if using promregator.authenticator.type=OAuth2XSUAA)
Specifies the client identifier (a.k.a. "client_id") which shall be used during the OAuth2 request based on the Grant Type Client Credentials flow in case of global authentication.

#### Option "promregator.authenticator.oauth2xsuaa.client_secret" (mandatory, if using promregator.authenticator.type=OAuth2XSUAA)
Specifies the client secret (a.k.a. "client_secret") which shall be used during the OAuth2 request based on the Grant Type Client Credentials flow in case of global authentication.

*WARNING!* 
Due to security reasons, it is *neither* recommended to store this value in your YAML file, nor to put it into the command line when starting Promregator.
Instead it is suggested to set the identically named environment variable `promregator.authenticator.oauth2xsuaa.client_secret` when starting the application.

Example:

```bash
export promregator.authenticator.oauth2xsuaa.client_secret=myClientSecret
java -Dspring.config.location=file:/path/to/your/myconfig.yaml -jar promregator-0.0.1-SNAPSHOT.jar
```

#### Option "promregator.authenticator.oauth2xsuaa.scopes" (optional, only available if using promregator.authenticator.type=OAuth2XSUAA)
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


