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

Check, if you still use the old `cf.request.timeout.app` configuration option. It is  deprecated since long.
In case you do, rename those configuration attributes to `cf.request.timeout.appInSpace`.

In case you have both attribute variants set, you should check about Promregator's behavior: Due to a bug, it may have happened before that `cf.request.timeout.app` was ignored and the default of 2.5 seconds was applied - even though you *thought* to have advised otherwise. 


## Switch to new Request Timeout Nomenclature (for App Details)

Check, if you still use one of the old configuration options:

* `cf.request.timeout.routeMapping`
* `cf.request.timeout.sharedDomain`

They are deprecated since long (version 0.5.0).

They need to be replaced with the new configuration option `cf.request.timeout.appSummary`. Note that there is only one configuration option available for all of them. Consider this, when "merging" their values. If in doubt which value you should use, use the highest value.

## New Cache Types available (for App Details)

Promregator introduced new cache types:

* Process Cache
* Route Cache

Also Promregator uses the already-available Domain Cache much differently as before.

Consequently, there are new configuration options available:

* `cf.request.timeout.route`
* `cf.request.timeout.process`
* `cf.request.timeout.domain` (already available in V0.* but used rarely)
* `cf.request.expiry.route`
* `cf.request.expiry.process`
* `cf.request.expiry.domain` (already available in V0.* but used rarely)

Check the [configuration page](./config.md) for these options and consider, if you need to set deviating values for them in your configuration.


## Label Enrichment and Single Endpoint Scraping

Both support for Label Enrichment and Single Endpoint Scraping was dropped. Check if you either have set configuration option `promregator.scraping.labelEnrichment` to `true`, or if your Prometheus was calling the `/metrics` endpoint of Promregator. If none of that is the case, you may skip the rest of this section.

If you have used Single Endpoint Scraping (via `/metrics` endpoint) before, you must migrate to Single Target Scraping first. Refer to [this document](./singleTargetScraping.md) how this works in general.

Additionally, if were using label enrichment before, you must adjust Prometheus' configuration to perform that for you. A description how that works can be found in the [document about Label Enrichment](./enrichment.md).


## Changes to metric names of type COUNTER

With [version 0.10.0](https://github.com/prometheus/client_java/blob/eb4e694b00024043f948e407510f516dea58cbc7/simpleclient_common/src/main/java/io/prometheus/client/exporter/common/TextFormat.java#L70) Prometheus' simpleclient has decided to change the way how counter-typed metrics are serialized. The metric's name automatically follows its sample's naming convention, which has a `_total` suffix attached. This leads to the fact that on the wire, instead of reading

```
# HELP metric this is my help text
# TYPE metric info
metric_total 12.47 123456789012345600
```

you will read

```
# HELP metric_total this is my help text
# TYPE metric_total info
metric_total 12.47 123456789012345600
```
Depending on how the metadata is evaluated by the consumer of Prometheus, this may or may not lead to a renaming of the metric from `metric` to `metric_total`. Check on the impact at your Prometheus' consumer.

