# Java Memory Configuration of Promregator

## Using your own Memory Configuration

When using the `jar` file in your own Java Virtual machine (e.g. you are starting Promregator by typing `jar -jar promregator.jar [...]` your own), or if you are creating your own Docker image, you need to define your own memory configuration.

Currently, there is no sufficient experience with the memory consumption of Promregator. However, as Promregator in essence is quite stateless, only the amount of memory is required to allow handling the number of concurrent requests.

As soon as more experience is available, this documentation will be adjusted.

## In the pre-delivered Docker Image

The image provided by Promregator has a fixed memory configuration. As of writing it is [configured](https://github.com/promregator/promregator/blob/e615d8e567f97277f1b3996cfaed6c504c05caab/docker/data/promregator.sh#L6) using the following parameters:

```
-Xmx300m -Xms300m -Xss600k -XX:ReservedCodeCacheSize=256m -XX:MaxMetaspaceSize=300m
```

In a nutshell this means:
* The heap is limited to 300 megabytes at maximum.
* All memory (for the heap) is allocated right from the beginning
* Each thread may allocate a stack having up to 600 kilobytes
* Metaspace (non-Heap) is limited to 300 megabytes at maximum (but may grow dynamically).
* There are 256 megabytes (within metaspace) reserved for Code Caching.

These are standard values which should permit Promregator to run in most of the usual environments properly.

Given this memory configuration, a maximal memory allocation **for the Docker container** of 700 megabytes shall be enough for Promregator to run properly.

Note that with the fixed memory configuration in the image for the Java Virtual Machine, it does not provide any benefit to increase the memory allocation for the Docker container **alone**. Instead, you have to provide your own set of memory configuration parameters by providing an own configuration in the environment variable `JAVA_MEM_OPTS`. Assuming that you wanted to increase the maximal heap memory size (and only that one!) to 400 megabytes, you would have to start the Docker container like this:

```bash
$ docker run -d -m 800m --env JAVA_MEM_OPTS="-Xmx400m -Xms300m -Xss600k -XX:ReservedCodeCacheSize=256m -XX:MaxMetaspaceSize=300m" promregator/promregator:0.x.y [...]
```

Note that all other parameters still need to be provided, as setting `JAVA_MEM_OPTS` externally disables any kind of defaulting.


## Monitoring Memory Consumption of Promregator

Having attached Prometheus to Promregator, you are also able to monitor the memory consumption of Promregator. For instance, the following metrics might be of interest for you:

* `promregator_jvm_memory_bytes_used{area="heap"}` provides the amount of memory allocated for the heap
* `promregator_jvm_memory_bytes_used{area="non-heap"}` provides the amount of memory allocated for the non-heap part (i.e. Metaspace)

Note that Java is running a garbage collector; thus, old and unused objects may still be reported (for example) to consume memory on the heap, because they have not been cleaned up, yet. If you want to see when garbage collection has taken place, please refer to `promregator_jvm_gc_collection_seconds_count`. You may also gain further insight on the different memory areas subject to garbage collecting by looking at `promregator_jvm_memory_pool_bytes_used`.
