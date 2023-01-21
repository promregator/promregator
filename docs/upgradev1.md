# Upgrade Guide to V1

For upgrading Promregator version 0.x.y to 1.a.b, make sure that you have made the following adjustments:

## Verify CFCC V3 Support of your Platform

Verify that your Cloud Foundry platform installation supports the [CFCC API V3](https://v3-apidocs.cloudfoundry.org/version/3.130.0/index.html).
The simplest approach is to use a browser and navigate to 

``` 
https://<your api url of the platform>/v3/info
```

Alternatively, you may also do the following shell command call
```bash
$ curl https://<your api url of the platform>/v3/info
```

If your platform does *not* support that API, the browser will come back with an error message, such as `404 Not Found`.
If your platform supports that API, you will see a JSON file like this:

```json
{
	"build":"",
	"cli_version":{
		"minimum":"",
		"recommended":""
	},
	"custom":{},
	"description":"some name here",
	"name":"",
	"version":0,
	"links":{
		"self":{
			"href":"https://<your api url of the platform>/v3/info"
		},
		"support":{
			"href":""
		}
	}
}
```

Note that you upgrade to Promregator V1 (or later), if your platform does not support API V3 (or later).

## Switch to new Proxy Nomenclature

Check, if you still use the old `cf.proxyHost` or `cf.proxyPort` configuration options. They are deprecated since long.
In case you do, rename those configuration attributes to `cf.proxy.host` and `cf.proxy.port` respectively.

In case you have both attribute variants set, you may safely delete `cf.proxyHost` and `cf.proxyPort`: The new one's took precedence in the past anyway.

If you also want to make use of the same proxy configuration for scraping, copy the values of the configuration option to `promregator.scraping.proxy.host` and `promregator.scraping.proxy.port` respectively.



## Switch to Caffeine Cache

If you have not done so, switch to the Caffeine Cache. For that, 

1. set the configuration option `cf.cache.type` to `CAFFEINE`.
2. Read the [Cache Types documentation page](cache-types.md) for further configuration options you might have to change.

## (Optional) Remove Cache Type Configuration option.

If you want to do so, you may remove configuration option `cf.cache.type` from your configuration: The new Caffeine Cache is the new default, and there is only one cache type option left anyway.

## Migrate to new oAuth2XSUAABasic Authenticator

If you are still using the old `OAuth2XSUAA` authenticator, then you must migrate to the new `OAuth2XSUAABasic`. In most cases, it is sufficient to switch the value of configuration option(s) `promregator.authenticator.type` from `OAuth2XSUAA` to `OAuth2XSUAABasic`.

Note that you also will have to change the other configuration option's names from `promregator.authenticator.oauth2xsuaa` to `promregator.authenticator.oauth2xsuaabasic`.

For further information, please refer to the [configuration documentation in version 0.11](https://github.com/promregator/promregator/blob/rel-0.11/docs/config.md).

## Switch to new Scraping Limiting Nomenclature

Check, if you still use the old `promregator.endpoint.maxProcessingTime` or `promregator.endpoint.threads` configuration option. They are deprecated since long.
In case you do, rename those configuration attributes to `promregator.scraping.maxProcessingTime` and `promregator.scraping.threads`.

In case you have both attribute variants set, you may safely delete `promregator.endpoint.maxProcessingTime` and `promregator.endpoint.threads`: The new one's took precedence in the past anyway.

## Switch to new Request Timeout Nomenclature (for Apps)

Check, if you still use the old `cf.request.timeout.app` configuration option. It is deprecated since long.
In case you do, rename those configuration attributes to `cf.request.timeout.appInSpace`.

In case you have both attribute variants set, you should check about Promregator's behavior: Due to a bug, it may have happened before that `cf.request.timeout.app` was ignored and the default of 2.5 seconds was applied - even though you *thought* to have advised otherwise. 


## Switch to new Request Timeout Nomenclature (for App Details)

Check, if you still use one of the old configuration options:

* `cf.request.timeout.routeMapping`
* `cf.request.timeout.route`
* `cf.request.timeout.sharedDomain`
* `cf.request.timeout.process`

They are deprecated since long (version 0.5.0).

Their replacement, configuration option `cf.request.timeout.appSummary`, has also lost their meaning in the meantime. 

If you have specified any of these five configuration options, you should remove any of them from your configuration file.


## New Cache Types available (for App Details)

Promregator introduced new cache types:

* Process Cache
* Route Cache

Also Promregator uses the already-available Domain Cache much differently as before.

Consequently, there are new configuration options available:

* `cf.cache.timeout.route` (not to be confused with `cf.request.timeout.route`!)
* `cf.cache.timeout.process` (not to be confused with `cf.request.timeout.process`!)
* `cf.cache.timeout.domain` (already available in V0.* but used rarely, not to be confused with `cf.request.timeout.domain`!)
* `cf.cache.expiry.route`
* `cf.cache.expiry.process`
* `cf.cache.expiry.domain` (already available in V0.* but used rarely)

Check the [configuration page](./config.md) for these options and consider, if you need to set deviating values for them in your configuration.

## New Request Timeouts for new Cache Types

With the new Cache Types mentioned before, also new request timeout configuration parameters have been introduced:

* `cf.request.timeout.route` (configuration option might already have been set before in version 0.x)
* `cf.request.timeout.process` (configuration option might already have been set before in version 0.x)
* `cf.request.timeout.domain`

Note that this may be confusing: As just mentioned two sections above, `cf.request.timeout.route` and `cf.request.timeout.process` had been deprecated in version 0.5.0. However, with version 1.0.0 we reintroduce some of them - with a slight difference in their meaning. Still their value denotes the maximal runtime of a request to the Cloud Foundry platform. The old configuration options were describing requests timeouts for endpoints using the CAPI V2 specification. The newly introduced one's are describing request timeouts for endpoints using the CAPI V3 specification (as Promregator version 1.x has been switched from CAPI V2 to V3 - and support for V2 is dropped).

Check the [configuration page](./config.md) for these options and consider, if you need to set deviating values for them in your configuration.


## Label Enrichment and Single Endpoint Scraping

Both support for Label Enrichment and Single Endpoint Scraping was dropped. Check if you either have set configuration option `promregator.scraping.labelEnrichment` to `true`, or if your Prometheus was calling the `/metrics` endpoint of Promregator. If none of that is the case, you may skip the rest of this section.

If you have used Single Endpoint Scraping (via `/metrics` endpoint) before, you must migrate to Single Target Scraping first. Refer to [this document](./singleTargetScraping.md) how this works in general.

Additionally, if were using label enrichment before, you must adjust Prometheus' configuration to perform that for you. A description how that works can be found in the [document about Label Enrichment](./enrichment.md).


## Changes to Sample Names of Type COUNTER

With [version 0.10.0](https://github.com/prometheus/client_java/blob/eb4e694b00024043f948e407510f516dea58cbc7/simpleclient_common/src/main/java/io/prometheus/client/exporter/common/TextFormat.java#L70) Prometheus' simpleclient has decided to change the way how counter-typed metrics are serialized. The metric's name automatically follows its sample's naming convention, which has a `_total` suffix attached. This leads to the fact that on the wire, instead of reading

```
# HELP metric this is my help text
# TYPE metric counter
metric 12.47 123456789012345600
```

you will read

```
# HELP metric this is my help text
# TYPE metric counter
metric_total 12.47 123456789012345600
```
Depending on how the metadata is evaluated by the consumer of Prometheus, this may or may not lead to a renaming of the metric from `metric` to `metric_total`. Check on the impact at your Prometheus' consumer.


## Change to the OpenMetrics Format 1.0.0

With [version 0.10.0](https://github.com/prometheus/client_java/blob/eb4e694b00024043f948e407510f516dea58cbc7/simpleclient_common/src/main/java/io/prometheus/client/exporter/common/TextFormat.java#L70) Prometheus' simpleclient only supports generating metricsets, which comply to the [OpenMetrics Format 1.0.0](https://github.com/OpenObservability/OpenMetrics/blob/1386544931307dff279688f332890c31b6c5de36/specification/OpenMetrics.md). This implies that Promregator cannot generate pure [Text 0.0.4 Exposition Format](https://github.com/prometheus/docs/blob/4874c371ee439b11babc13ff88ae9747e19dbd8f/content/docs/instrumenting/exposition_formats.md) responses anymore. If queried by a caller (e.g. Prometheus) for returning Text 0.0.4 Exposition Format responses, a 401 Bad Request response will be issued.

That is why that you must make sure that you use Prometheus [2.5.0](https://github.com/prometheus/prometheus/blob/main/CHANGELOG.md#250--2018-11-06) or higher for scraping with Promregator. Due to some bugs in early Prometheus versions, it is recommended to upgrade Prometheus to version [2.34.0](https://github.com/prometheus/prometheus/blob/64842f137e1ae6e041e12a2707d99d6da4ba885b/CHANGELOG.md#2340--2022-03-15) or higher.

*Note*: This does not also mean that all targets from where Promregator is scraping data have to support the OpenMetrics format as well! If Promregator receives a request to a target to be scraped that is able to only respond with the classic Text 0.0.4 Exposition Format, Promregator parses the old format and automatically converts its content up to the OpenMetrics Format 1.0.0.

*Note*: The same also applies for Promregator's own metrics exposed to Prometheus: Also there only the OpenMetrics Format 1.0.0 is supported.
