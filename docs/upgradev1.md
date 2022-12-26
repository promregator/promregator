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


## Switch to Caffeine Cache

If you have not done so, switch to the Caffeine Cache. For that, 

1. set the configuration option `cf.cache.type` to `CAFFFEINE`.
2. Read the [Cache Types documentation page](cache-types.md) for further configuration options you might have to change.

## (Optional) Remove Cache Type Configuration option.

If you want you may remove configuration option `cf.cache.type` from your configuration: The new Caffeine Cache is the new default, and there is only one cache type option left anyway.

