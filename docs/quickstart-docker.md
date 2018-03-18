# Quickstart with Promregator using Docker Images


This guide should give you a jumpstart to get a first environment set up as fast as possible.
For this purpose, we will use multiple Docker images, as this greatly relieves you from installing all the fuzz. Therefore, **having Docker (CE) installed** is [mandatory](https://docs.docker.com/install/) for this quick start guide. 


## Setup metrics-generating Cloud Foundry application

1. Make sure that you have access to a Cloud Foundry Platform. If you don't have one, yet, then you may get a free account at various providers, such as [IBM Bluemix](https://www.ibm.com/cloud-computing/bluemix).
2. Download `TestMetricsApplication.jar` from https://github.com/promregator/promregator/releases/tag/v0.1.0 to your local machine.
3. Deploy this app onto the platform, for example with

   ```bash
   $ cf login
   $ cf create-space test
   $ cf target -s test
   $ cf push testapp -m 128m --random-route -p /path/to/TestMetricsApplication.jar
   ```

4. Note down the hostname under which the application is reachable. If you missed it, you may see an overview of your apps using

   ```bash
   $ cf apps
   ```

5. Test that your application is reachable by opening the url `https://<URLToYourApp>/metrics` in your browser. You should see a text file, which starts with something like this:
   ```
   # HELP jvm_buffer_pool_used_bytes Used bytes of a given JVM buffer pool.
   # TYPE jvm_buffer_pool_used_bytes gauge
   jvm_buffer_pool_used_bytes{pool="mapped",} 0.0
   jvm_buffer_pool_used_bytes{pool="direct",} 90160.0
   ```

  Note that the exact text may deviate.

6. Note down the values from
   ```bash
   $ cf target
   ```

   Especially the value of `api endpoint`, `org:` and `space:` will be important in a minute.

## Setup Promregator

1. On your local machine, create a file `promregator.yml` with the following content:

   ```yaml
   promregator:
     authenticator:
       type: none
   
      targets:
       - orgName: <hereGoesYourOrg>
         spaceName: <hereGoesYourSpace>
         applicationName: testapp
   
   cf:
     api_host: <hereGoesTheAPIEndpointHostOnly!>
     username: <yourCFUsername>
   ```
   Note that you have to replace of the placeholders above like this:
   
   * `<hereGoesYourOrg>` is the value printed after `org:` from above.
   * `<hereGoesYourSpace>` is the value printed after `space:` from above.
   * `<hereGoesTheAPIEndpointHostOnly!>` is the host and domain name part of `api endpoint` from above, leaving off `https:/`. So, if `api endpoint` reads `https://api.eu-gb.bluemix.net`, then you enter `api.eu-gb.bluemix.net` here.
   * `<yourCFUsername>` is the username which you used for logging on to the platform before.

2. Retrieve the Docker image of promregator by calling
   ```bash
   $ docker pull promregator/promregator:0.1.0
   ```

3. Start a container using the following command:
   ```bash
   $ docker run -d --name promregator -m 600m --env CF_PASSWORD=<yourCFPassword> \
     -v `pwd`/promregator.yaml:/etc/promregator/promregator.yml \
     -p 127.0.0.1:56710:8080 promregator/promregator:0.1.0
   ```
   
   Again note, that you have to replace `<yourCFPassword>` with the password which you had used for logging on to the Cloud Foundry platform (the password needs to fit to `<yourCFUsername>`).

4. Verify that your setup is okay by opening the url `http://localhost:56710` in your browser. Again, you should see a similar text file as before.

### Hint
If you made a mistake with setting up the container, you might get a broken container, which may cause troubles on getting rid of again.
```bash
$ docker rm -f promregator
```
then comes to your rescue.

## Setup Prometheus

1. On your local machine, create a file `prometheus.yml` with the following content:

   ```yaml
   global:
     scrape_interval: 15s
     evaluation_interval: 15s
   
   scrape_configs:
     - job_name: 'promregator'
       scheme: http
       static_configs:
         - targets: ['promregator:8080']
   ```
   
2. Retrieve the Docker image of Prometheus by calling
   ```bash
   $ docker pull prom/prometheus:latet
   ```

3. Start a container using the following command:
   ```bash
   $ docker run --name prometheus -v `pwd`/prometheus.yml:/etc/prometheus/prometheus.yml -p 127.0.0.1:9090:9090 \
     --link promregator \
     prom/prometheus:latest
   ```

4. Start your browser at `http://localhost:9090`.

5. Wait a couple of seconds and type `test_random_value` (that's one of the metrics the test application emits). Click on "Execute" and switch to the graph.

6. Play around with the other metrics, which are provided.

Done! You have just created your first promregator setup!
