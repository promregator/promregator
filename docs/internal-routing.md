# Internal Routing

Its possible to deploy promregator into cloudfoundry as a cloud foundry app and this enables the possibility of routing all promregator calls internally so that they don't leave your environment.

## Defining Routes

When defining your internal routes for apps its importnat to specify the port to be used on your route so that promregator can detect the correct port from the API.

You can define a route like so

```sh
cf map-route myapp app.internal --hostname myapphost --port 9090
```

## Network policies

To enable internal communication between containers you must define network policies to allow the traffic between promregator and the target app.

```sh
cf add-network-policy promregator --destination-app apptoscrape --protocol tcp --port 9090
```
