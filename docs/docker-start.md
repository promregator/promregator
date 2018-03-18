# Suggested Parameters on Starting the Promregator Docker Image

The following parameters are suggested to be used when running the promregator docker image:

```bash
docker run -d \
 -m 600m \
 --env CF_PASSWORD=yoursecretpassword \
 -v /path/to/your/own/promregator.yaml:/etc/promregator/promregator.yml \
 -p 127.0.0.1:56710:8080 \
 promregator/promregator:<version>
```
This has the following background:

* `-m 600m` limits the memory consumption of the container to 600MB. Unless you have a super-large set of targets, this is quite enough memory for promregator.
* `--env CF_PASSWORD=yoursecretpassword` specifies your Cloud Foundry API password, which should not be stored (plain-text) in your configuration file.
* `-v /path/to/your/own/promregator.yaml:/etc/promregator/promregator.yml` specifies the location of your promregator file. Note that promregator always will try to find that file **within the image** at the location of `/etc/promregator/promregator.yml` (that's why you only have to adopt the path to the left-hand side of the colon!)
* `-p 127.0.0.1:56710:8080` By default, promregator in the docker container exposes its endpoint via port 8080. This parameters maps the port to your port 56710 at 127.0.0.1 (i.e. `localhost`). Adjust your values as needed.
  
  Warning! Do not leave off the leading IP address! Otherwise docker will bind your container against IP address 0.0.0.0 and thus makes promregator world-accessible via the network!
