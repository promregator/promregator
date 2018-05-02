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
Allows to disable the SSL/TLS certificate validation when talking to the Cloud Foundry API host at the servers reported via `cf.api_host`. This is usually necessary, if your Cloud Foundry platform is only equipped with a self-signed certificate, or a certificate, which the Java Virtual Machine is not aware of (NB: the default-provided docker image only is aware of the publicly-known Root certificates as defined by the underlying operating system Ubuntu). 

By default, this option is disabled, which means that the validation is performed. If the validation fails, you typically get the following error message:

```
Caused by: sun.security.validator.ValidatorException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target
```

*WARNING!*
Enabling this option (and thus *disabling* the validation) may make you vulnerable to [man-in-the-middle attacks](https://en.wikipedia.org/wiki/Man-in-the-middle_attack). Thus, this option should never be enabled in a productive environment, but may only be used for testing purpose in a properly controlled environment.


### Option "cf.cache.timeout.org" (optional)
For performance reasons the metadata of the Cloud Foundry environment (organization, space, applications, routes) is cached locally in Promregator.

This option allows you to specify how often the metadata of the organizations you have selected in your targets shall be verified after it has been fetched. The value is a timeout after which the metadata is retrieved again. Its unit is seconds.

Note that organizations typically do not change often. That is why you should pick a high value here, as otherwise you would produce unnecessary network traffic for everyone.

By default, this value is set to 3600 seconds, which means that the metadata is retrieved (again) after an hour.

Caches can also be invalidated out of the row by sending an HTTP REST request to Promregator. Further details can be found at the [Cache Invalidation page](./invalidate-cache.md).

### Option "cf.cache.timeout.space" (optional)
For performance reasons the metadata of the Cloud Foundry environment (organization, space, applications, routes) is cached locally in Promregator.

This option allows you to specify how often the metadata of spaces you have selected in your targets shall be verified after it has been fetched. The value is a timeout after which the metadata is retrieved again. Its unit is seconds.

Note that spaces typically do not change often. That is why you should pick a high value here, as otherwise you would produce unnecessary network traffic for everyone.

By default, this value is set to 3600 seconds, which means that the metadata is retrieved (again) after an hour.

Caches can also be invalidated out of the row by sending an HTTP REST request to Promregator. Further details can be found at the [Cache Invalidation page](./invalidate-cache.md).


### Option "cf.cache.timeout.application" (optional)
For performance reasons the metadata of the Cloud Foundry environment (organization, space, applications, routes) is cached locally in Promregator.

This option allows you to specify how often the metadata of the applications and routes you have selected in your targets shall be verified after they have been fetched. The value is a timeout after which the metadata is retrieved again. Its unit is seconds.

By default, this value is set to 300 seconds, which means that the metadata is retrieved every five minutes.

Note that applications and routes *may* change often. That is why you should pick a quite *low* value here to ensure that you do not miss and update for long time. Otherwise, you might get metrics indicating that an app may be down, but in fact it is running, but you only deployed a new version of the app or you changed a route.

Caches can also be invalidated out of the row by sending an HTTP REST request to Promregator. Further details can be found at the [Cache Invalidation page](./invalidate-cache.md).

#### Option "promregator.cache.invalidate.auth" (optional)
Specifies the way how authentication shall be verified, if a request reaches the [Cache Invalidation endpoint](./invalidate-cache.md). Valid values are:

* *NONE*: no authentication verification is required (default)
* *BASIC*: an authentication verification using HTTP Basic Authentication is performed. Valid credentials are taken from `promregator.authentication.basic.username` and `promregator.authentication.basic.password`.

## Group "promregator"
This group configures the behavior of Promregator itself. It is mainly meant on how requests shall be handled, as soon as the Prometheus server starts to pull metrics.

### Subgroup "promregator.targets"
Lists one or more Clound Foundry applications, which shall be queried for metrics.
The subgroup expects an item list, which contains additional mandatory properties

#### Item property "promregator.targets[].orgName" (mandatory)
Specifies the name of the Cloud Foundry Organization which hosts the application, which you want to query for metrics.

#### Item property "promregator.targets[].spaceName" (mandatory)
Specifies the name of the Cloud Foundry Space (within the Cloud Foundry Organization specified above), which hosts the application, which you want to query for metrics.

#### Item property "promregator.targets[].applicationName" (optional)
Specifies the name of the Cloud Foundry Application (within the Cloud Foundry Organization and Space specified above), which hosts the application, which you want to query for metrics.

If left out (and also "applicationRegex" is omitted), *all* applications within the specified Cloud Foundry Organization and Cloud Foundry Space are considered as targets. Only applications in the Cloud Foundry Application state "STARTED" are considered.

By this, automatic detection of new applications is possible without the need to modify the configuration of Promregator. Note that discovery of *new* applications within the space only takes place after the timeout of "cf.cache.timeout.application" has occurred. To enforce a discovery, you may [invalidate the application cache manually](./invalidate-cache.md).

#### Item property "promregator.targets[].applicationRegex" (optional)
Specifies the regular expression (based on a [Java Regular expression](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html)) for scanning the application name of the Cloud Foundry Application (within the Cloud Foundry Organization and Space specified above), which hosts the application, which you want to query for metrics. Only applications, for which the name matches to this regular expression, are considered for scraping. 

By this, automatic detection of new applications is possible without the need to modify the configuration of Promregator. Note that discovery of *new* applications within the space only takes place after the timeout of "cf.cache.timeout.application" has occurred. To enforce a discovery, you may [invalidate the application cache manually](./invalidate-cache.md).

#### Item property "promregator.targets[].path" (optional)
Specifies the path under which the application's endpoint provides its Prometheus metrics.

Defaults to `/metrics`, as this is the value which is suggested by Prometheus.

#### Item property "promregator.targets[].protocol" (optional)
Specifies the protocol (`http` or `https`) which shall be used to retrieve the metrics.

Defaults to `https` if not set otherwise.

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

### Subgroup "promregator.endpoint"
Configures the way how the metrics endpoints `/metrics` and `/singleTargetMetrics` behave.

#### Option "promregator.endpoint.maxProcessingTime" (optional)
Specifies the maximal time which may be used to query (all) targets. The value is expected to be specified in milliseconds. 
Targets which did not respond after this amount of time are considered non-functional and no result will be returned to the Prometheus server.

The default value of this option is 5000ms (=5 seconds)

#### Option "promregator.endpoint.threads" (optional)
Specifies how many threads may be used to query the list of targets.
Note that for each target request sent, an own thread is required and stays blocked (synchronously) until the Cloud Foundry Application has returned a response. 
Thus, it may be reasonable to allow more threads than you have cores in your environment where Promregator is running.

In general, the more targets you have registered, the higher this value should be. However, running too many threads is simply a waste of resources.
As an upper boundary, it does not make sense to allow more threads to run than you have specified targets in your configuration.

The default value of this option is 5.

#### Option "promregator.endpoint.auth" (optional)
Specifies the way how authentication shall be verified, if a request reaches the scraping endpoints of Promregator (e.g. `/metrics` and `/singleTargetMetrics`). Valid values are:

* *NONE*: no authentication verification is required (default)
* *BASIC*: an authentication verification using HTTP Basic Authentication is performed. Valid credentials are taken from `promregator.authentication.basic.username` and `promregator.authentication.basic.password`.


### Subgroup "promregator.metrics"
Configures the way how the promregator shall expose its own-generated metrics via the endpoints `/metrics` and `/promregatorMetrics`.

#### Option "promregator.metrics.auth" (optional)
Specifies the way how authentication shall be verified, if a request reaches the endpoint `/promregatorMetrics`. Note that the authentication verification of `/metrics` is controlled by `promregator.endpoint.auth`. Valid values for this option are:

* *NONE*: no authentication verification is required (default)
* *BASIC*: an authentication verification using HTTP Basic Authentication is performed. Valid credentials are taken from `promregator.authentication.basic.username` and `promregator.authentication.basic.password`.


#### Option "promregator.metrics.internal" (optional)
Specifies, if additional internal metrics shall be exposed describing the internal state of Promregator.

The default value of this option is `false`, which disables the exposure. 

Note that these metrics are not meant for productive usage. As they are primarily meant for facilitating debugging issues in Promregator, their naming and labels may change at any point in time without further notice.


### Subgroup "promregator.authenticator"
Configures the way how authentication shall happen between Promregator and the targets configured above (**outbound** authentication). Mind the difference to the settings provided in `promregator.authentication`!

Please note that as of writing, there is no support of multiple authentication schemes across different targets. That is to say: all targets
are queried using the same authentication scheme. If you want to have different schemes configured, then you have to run multiple instances of
Promregator

#### Option "promregator.authenticator.type" (mandatory)
Specifies the type of the Authenticator which shall be used when connecting to targeted Cloud Foundry Applications. Valid options are:

* *basic*: Enables the Basic Authentication scheme using plain-text-based Basic Authentication as defined in [RFC2617](https://www.ietf.org/rfc/rfc2617.txt).
  Additional options must be provided to complete the configuration.
* *OAuth2XSUAA*: Enables the OAuth2/JWT-based authentication scheme using grant type "Client Credentials" as used for XSUAA servers. 
  Additional options must be provided to complete the configuration.
* *null* or *none*: Disables any additional authentication scheme; requests will be sent as unauthenticated HTTP(s) GET requests (depending of the protocol specified for the target). No further options need to be provided.

*Note!*
This option does not have any influence on how Promregator authenticates to the Cloud Foundry platform's API, but only has an impact to the way how Promregator tries to authenticate on scraping Cloud Foundry Applications.

#### Option "promregator.authenticator.basic.username" (mandatory, if using promregator.authenticator.type=basic)
Specifies the username which is being used for authenticating the call to the Prometheus client (CF application).

#### Option "promregator.authenticator.basic.password" (mandatory, if using promregator.authenticator.type=basic)
Specifies the password which is being used for authenticating the call to the Prometheus client (CF application).

*WARNING!* 
Due to security reasons, it is *neither* recommended to store this value in your YAML file, nor to put it into the command line when starting Promregator.
Instead it is suggested to set the identically named environment variable `PROMREGATOR_AUTHENTICATOR_BASIC_PASSWORD` when starting the application.

Example:

```bash
export PROMREGATOR_AUTHENTICATOR_BASIC_PASSWORD=myPassword
java -Dspring.config.location=file:/path/to/your/myconfig.yaml -jar promregator-0.0.1-SNAPSHOT.jar
```

#### Option "promregator.authenticator.oauth2xsuaa.tokenServiceURL" (mandatory, if using promregator.authenticator.type=OAuth2XSUAA)
Specifies the URL of the OAuth2 endpoint, which contains the token service of your authorization server. Typically, this is the endpoint with the path `/oauth/token`,
as Promregator will try to perform to establish a ["Client Credentials"-based authentication](https://www.digitalocean.com/community/tutorials/an-introduction-to-oauth-2#grant-type-client-credentials).

#### Option "promregator.authenticator.oauth2xsuaa.client_id" (mandatory, if using promregator.authenticator.type=OAuth2XSUAA)
Specifies the client identifier (a.k.a. "client_id") which shall be used during the OAuth2 request based on the Grant Type Client Credentials flow.

#### Option "promregator.authenticator.oauth2xsuaa.client_secret" (mandatory, if using promregator.authenticator.type=OAuth2XSUAA)
Specifies the client secret (a.k.a. "client_secret") which shall be used during the OAuth2 request based on the Grant Type Client Credentials flow.

*WARNING!* 
Due to security reasons, it is *neither* recommended to store this value in your YAML file, nor to put it into the command line when starting Promregator.
Instead it is suggested to set the identically named environment variable `promregator.authenticator.oauth2xsuaa.client_secret` when starting the application.

Example:

```bash
export promregator.authenticator.oauth2xsuaa.client_secret=myClientSecret
java -Dspring.config.location=file:/path/to/your/myconfig.yaml -jar promregator-0.0.1-SNAPSHOT.jar
```

#### Option "promregator.authenticator.oauth2xsuaa.scopes" (optional, only available if using promregator.authenticator.type=OAuth2XSUAA)
Specifies the set of scopes/authorities (format itself is a comma-separated string of explicit scopes, see also https://www.oauth.com/oauth2-servers/access-tokens/client-credentials/), which shall be requested from the OAuth2 server when using the Grant Type Client Credentials flow. If not specified, an empty string is assumed, which will suppress a dedicated request of scopes. Usually, OAuth2 servers then provide a JWT, which contains all scopes allowed for the set of credentials provided.


### Subgroup "promregator.authentication"
Configures the way how **inbound** authentication shall be verified (e.g. between Prometheus and Promregator). Mind the difference to the settings provided in `promregator.authenticator`!

#### Option "promregator.authentication.basic.username" (optional)
Specifies the username, which is used for HTTP Basic (inbound) authentication. 

If not specified, `promregator` is defaulted.


#### Option "promregator.authentication.basic.password" (optional)
Specifies the (plain-text) password, which is used for HTTP Basic (inbound) authentication.

If not specified, a random password is generated during startup. Its value is printed to the standard error device.


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



