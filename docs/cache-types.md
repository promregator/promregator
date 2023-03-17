# Cache Types

Over the time, Promregator has used different cache types for caching metadata provided by the Cloud Foundry's Cloud Controller. These caches partly have different behaviors, which require explanation.

## Behavior Overview

The following table describes the major different bahaviors of the caches used:

| Property | Classical Cache (only V0.*) | Caffeine Cache |
|----------|-----------------|----------------|
| Timeout  | Enforced Eviction | No eviction  |
| Blocking on Timeout | Yes   | No |

The following sections will explain these differences in more detail.

### Timeout Behavior

When hitting the timeouts of cache values (cf. [configuration options](config.md) `cf.cache.timeout.*`), the classical cache will simply remove the value from the cache. The next consumer will then get a cache miss of the corresponding value, which in turn triggers a reload of that value. The requester then is blocked until the value is available.

The Caffeine Cache does not remove the value from the cache. Instead reloading of the value is triggered asynchronously. The requester is provided the old value from the cache. While the asynchronous request is still pending, other additional requesters will also still receive the old value. Once the asynchronous response is available, the cache is updated accordingly. Starting from there, requesters will get the new value. Additional information about Caffeine's Cache behavior can also be found at its [Eviction page](https://github.com/ben-manes/caffeine/wiki/Eviction). Promregator uses the concept of time-based eviction.

### Blocking on Timeout

As also described in the previous section, the classical cache will block the execution of the requester, if a value is timed out.

If the Caffeine Cache still has an old value for the key in the cache, it will return the old value immediately. If there is no such value available, the cache will block the requester until a value is available.

## Cache Invalidation

Independent of the type of cache, it can be invalidated manually. For more information refer to the [page "Cache Invalidation"](invalidate-cache.md).
