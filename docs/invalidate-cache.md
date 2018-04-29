# Cache Invalidation

## Available Caches

Promregator has several caches build-in, which mainly serve the purpose to buffer the layout of your landscape on Cloud Foundry. As of now, Promregator knows about the following caches:

| Name of Cache | Purpose |
|---------------|---------|
| Org Cache     | Caches the metadata of Cloud Foundry Organizations, especially the mapping between human-readable names and internal CF Org Ids |
| Space Cache   | Caches the metadata of Cloud Foundry Spaces within Organizations, especially the mapping between human-readable names and internal CF Space Ids |
| Application Cache | Caches the metadata of Cloud Foundry Applications within Organizations and Spaces, especially the mapping between human-readable names and internal CF Org/Space Ids. It also caches routes, hostnames, and domains. |

The application cache also is used in cases that you have not specified the application name in a target and thus *all* applications within a space are requested to be scraped.

## Automatic Cache Invalidation

Caches are automatically invalidated after a certain timeout. Each cache has a corresponding (default) timeout. Timeouts can be configured. See the sections `cf.cache.timeout.*` in our [configuration page](./config.md).

## Cache Invalidation via HTTP REST Endpoint

The HTTP REST [endpoint](endpoint.md) `/cache/invalidate` allows to trigger a cache invalidation manually. This might be a good idea, if you just have deployed a new application into a space and you have automatic application discovery enabled (by omitting the `applicationName` in your targets configuration). By this, on the next scraping request, Promregator will perform the application discovery again and thus detect the new application without you having to wait that an automatic cache invalidation takes place.

The HTTP REST endpoint allows to specify which caches shall be flushed by using URL parameters. The value of the URL parameter must be either `1` or `true`. The table below describes the mapping between URL parameters and the cache which will be flushed.

| Name of Cache | URL parameter |
|---------------|---------------|
| Org Cache     | `org`        |
| Space Cache   | `space`      |
| Application Cache | `application` |

Not specifying any of these parameters will lead to no cache to be flushed. On completion of any request, the HTTP status code of the request will be 204 ("no content").

### Example

Sending the request

```
http://promregator:8080/cache/invalidate?org=1&application=true
```

will invalidate the Org Cache and the Application Cache, but will leave the Space Cache untouched.
