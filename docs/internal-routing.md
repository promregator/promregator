# Internal Routing
This functionality became available starting with version 0.9.0.

It is possible to deploy promregator into cloudfoundry as a cloud foundry app and this enables the possibility of routing all promregator calls internally so that they do not leave your environment.

## Defining an internal target

When promregator resolves your targets, it will automatically detect, if the route that has been selected is using an internal domain. You may use configuration parameter `preferredRouteRegex` to guide Promregator to the appropriate route. If an internal domain is detected, then your targets will be scraped internally. When this happens, promregator uses some slightly different logic to scrape your application. This is because the internal router does not understand the instance header used for regular promregator scraping. When scraping internally, it uses the URL format `<instance_number>.<hostname>.<domain>:<port>`. Because of this format, a port number is needed for scraping. A default port number of `8080` is defined (see also configuration parameter `defaultInternalRoutePort`), but you can also define the `internalRoutePort` on a per target basis.

When using internal routes for scraping, you should only have a single route per application. This is because the instance identification is via DNS and not headers. Therefore, it is not possible to distinguish different apps on the same route. If multiple apps are on the same route, it is possible that there are inconsistent scraping results.

Because there is no gorouter for internally routed applications, they will only have HTTP endpoints by default, unless you provide HTTPS functionality from directly within your apps. To allow scraping without providing HTTPS support natively by the application, you may set `promregator.targets[].protocol` to `http` to make Promregator send scraping requests unencrypted (via the internal CF network).

## Network policies

To enable internal communication between containers you must define network policies to allow the traffic between promregator and the target app. When defining the network policy, it is important to ensure you use the same port that you expect to scrape on and configure in the `internalRoutePort` value of your target.

```sh
cf add-network-policy promregator --destination-app apptoscrape --protocol tcp --port 9090
```

In the example above, you would also want to set `internalRoutePort` to `9090`.
