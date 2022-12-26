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


## Switch to new Scraping Limiting Nomenclature

Check, if you still use the old `promregator.endpoint.maxProcessingTime` or `promregator.endpoint.threads` configuration option. They are deprecated since long.
In case you do, rename those configuration attributes to `promregator.scraping.maxProcessingTime` and `promregator.scraping.threads`.

In case you have both attribute variants set, you may safely delete `promregator.endpoint.maxProcessingTime` and `promregator.endpoint.threads`: The new one's took precedence in the past anyway.


## Switch to Caffeine Cache

If you have not done so, switch to the Caffeine Cache. For that, 

1. set the configuration option `cf.cache.type` to `CAFFEINE`.
2. Read the [Cache Types documentation page](cache-types.md) for further configuration options you might have to change.

## (Optional) Remove Cache Type Configuration option.

If you want you may remove configuration option `cf.cache.type` from your configuration: The new Caffeine Cache is the new default, and there is only one cache type option left anyway.

## Migrate to new oAuth2XSUAABasic Authenticator

If you are still using the old `OAuth2XSUAA` authenticator, then you must migrate to the new `OAuth2XSUAABasic`. In most cases, it is sufficient to switch the value of configuration option(s) `promregator.authenticator.type` from `OAuth2XSUAA` to `OAuth2XSUAABasic`.

Note that you also will have to change the other configuration option's names from `promregator.authenticator.oauth2xsuaa` to `promregator.authenticator.oauth2xsuaabasic`.

For further information, please refer to the [configuration documentation in release 0.11](https://github.com/promregator/promregator/blob/rel-0.11/docs/config.md).



