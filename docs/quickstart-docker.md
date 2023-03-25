# Quickstart with Promregator using Docker Images


This guide should give you a jumpstart to get a first environment set up as fast as possible.
For this purpose, we will use multiple Docker images, as this greatly relieves you from installing all the fuzz. Therefore, **having Docker (CE) installed** is [mandatory](https://docs.docker.com/install/) for this quick start guide. 


## Setup metrics-generating Cloud Foundry application

1. Make sure that you have access to a Cloud Foundry Platform. If you don't have one, yet, then you may get a trial account at various providers, such as [IBM Cloud](https://www.ibm.com/cloud-computing/bluemix) (f.k.a. IBM Bluemix).
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

5. Test that your application is reachable by opening the URL `https://<URLToYourApp>/metrics` in your browser. You should see a text file, which starts with something like this:
   ```
   # HELP jvm_buffer_pool_used_bytes Used bytes of a given JVM buffer pool.
   # TYPE jvm_buffer_pool_used_bytes gauge
   jvm_buffer_pool_used_bytes{pool="mapped",} 0.0
   jvm_buffer_pool_used_bytes{pool="direct",} 90160.0
   ```

   Note that the exact text may deviate.
   
   Also note that, if you don't have a browser at hand, you simply may also just use `curl` or `wget` for that.
   
   _Side remark_: The application also exposes the Spring Boot Actuator metrics at endpoint `/actuator/prometheus`. However, the application is not set up properly for scraping them. Therefore, for the sake of this quickstart guide here, you **must** use the metrics provided at path `/metrics`.

6. Check the certificate used when having `https://<URLToYourApp>/metrics` open in your browser: The certificate must be valid and signed by one of the official Root CAs that OpenJDK supports. 

   If that is not the case, try if you can reach your application with `http://<URLToYourApp>/metrics` (mind the different protocol). If that works, you still will be able to continue with this quickstart guide, but you will have to react to that later on. Note that this setup is not recommended for productive usage!

7. Note down the values from
   ```bash
   $ cf target
   ```

   Especially the value of `api endpoint:`, `org:` and `space:` will become important in a minute.

## Setup Promregator

1. On your local machine, create a file `promregator.yml` with the following content:

   ```yaml
   promregator:
     authenticator:
       type: none
       
     discovery:
      hostname: promregator
   
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
   * `<hereGoesTheAPIEndpointHostOnly!>` is the host and domain name part of `api endpoint:` from above, leaving off `https://`. So, if `api endpoint:` reads `https://api.eu-gb.bluemix.net`, then you should enter `api.eu-gb.bluemix.net` here.
   * `<yourCFUsername>` is the username, which you used for logging on to the platform before.

   Keep in mind that you are writing a [YAML](http://yaml.org/spec/) file, which requires that you use spaces for indentation - not tabs!
   
2. In case that your application is not reachable by HTTPS, but only by HTTP, add the option `protocol: http` to your target. That part of the YAML file then would look like this:

   ```yaml
     ...
     targets:
       - orgName: <hereGoesYourOrg>
         spaceName: <hereGoesYourSpace>
         applicationName: testapp
         protocol: http
     ...
   ```

3. Retrieve the Docker image of promregator by calling
   ```bash
   $ docker pull promregator/promregator:1.0.1
   ```

4. Start a container using the following command:
   ```bash
   $ docker run -d --name promregator -m 600m --env CF_PASSWORD=<yourCFPassword> \
     -v `pwd`/promregator.yaml:/etc/promregator/promregator.yml \
     -p 127.0.0.1:56710:8080 promregator/promregator:1.0.1
   ```
   
   Again note, that you have to replace `<yourCFPassword>`, with the password which you had used for logging on to the Cloud Foundry platform (the password needs to fit to `<yourCFUsername>`).

5. Verify that your setup is okay by opening the URL `http://localhost:56710/discovery` in your browser (`curl` or `wget` is okay again, too). You should get a response that looks similar like this:

   ```json
   [
    {
      "targets": [
        "promregator:56710"
      ],
      "labels": {
        "__meta_promregator_target_path": "/singleTargetMetrics/6460bea4-c300-46a2-a6c2-3aa2fb271c86/0",
        "__meta_promregator_target_orgName": "<hereGoesYourOrg>",
        "__meta_promregator_target_spaceName": "<hereGoesYourSpace>",
        "__meta_promregator_target_applicationName": "testapp",
        "__meta_promregator_target_applicationId": "6460bea4-c300-46a2-a6c2-3aa2fb271c86",
        "__meta_promregator_target_instanceNumber": "0",
        "__meta_promregator_target_instanceId": "6460bea4-c300-46a2-a6c2-3aa2fb271c86:0",
        "__metrics_path__": "/singleTargetMetrics/6460bea4-c300-46a2-a6c2-3aa2fb271c86/0"
      }
    }, ...
   ```

   In the first block, copy the path that is mentioned as value of `__metrics_path__`. It should start with `/singleTargetMetrics/` and is followed then by a GUID. 
   If you have only a single instance in the JSON document and the `__metrics_path__`'s value starts with `/promregatorMetrics`, then something went wrong with the configuration above: Promregator could not fetch your application's metadata from Cloud Foundry. Check your configuration. Further hints may be found in Promregator's log, which you can read from
  
   ```bash
   $ docker logs promregator
   ```

6. Create a copy of the content of the JSON document above and store it as `discovery.json` locally. Using `curl` you may do so like this:

   ```bash
   $ curl -o discovery.json http://localhost:56710/discovery 
   ```

6. Create a URL like this: `http://localhost:56710/<yourmetricspath>` where `<yourmetricspath>` is replaced by the value of `__metrics_path__`. It then will read something like `http://localhost:56710/singleTargetMetrics/6460bea4-c300-46a2-a6c2-3aa2fb271c86/0`.
   Use an HTTP tool again (such as CURL) to send a request to this endpoint. Note that you have to provide a special header line like this:
   
   ```
   Accept: application/openmetrics-text; version=1.0.0; charset=utf-8
   ```
   
   Using `curl` the command to achieve this would be something like this:
   
   ```bash
   $ curl -H "Accept: application/openmetrics-text; version=1.0.0; charset=utf-8" http://localhost:56710/singleTargetMetrics/6460bea4-c300-46a2-a6c2-3aa2fb271c86/0
   ```
   Once executed, you should see a similar text file containing the metrics of the application as above.

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
       file_sd_configs:
        - files:
           - /etc/promregator/discovery.json
         
      relabel_configs:
        - source_labels: [__meta_promregator_target_instanceId]
          target_label: instance
          
        - source_labels: [__meta_promregator_target_instanceId]
          target_label: cf_instance_id
          
        - source_labels: [__meta_promregator_target_orgName]
          target_label: org_name
          
        - source_labels: [__meta_promregator_target_spaceName]
          target_label: space_name
          
        - source_labels: [__meta_promregator_target_applicationName]
          target_label: app_name
          
        - source_labels: [__meta_promregator_target_instanceNumber]
          target_label: cf_instance_number
   ```
   
   Again, please be reminded to use tabs for indentation - not tabs.
   
2. Retrieve the Docker image of Prometheus by calling
   ```bash
   $ docker pull prom/prometheus:latest
   ```

3. Start a container using the following command:
   ```bash
   $ docker run --name prometheus -v `pwd`/prometheus.yml:/etc/prometheus/prometheus.yml \
     -p 127.0.0.1:9090:9090 --link promregator \
     prom/prometheus:latest
   ```

4. Start your browser at `http://localhost:9090` (here you really should have a proper browser).

5. Wait a couple of seconds and type `test_random_value` (that's one of the metrics the test application emits). Click on "Execute" and switch to the graph.

6. Play around with the other metrics, which are provided.

Done! You have just created your first promregator setup!

## Final Remarks

This is only a very quick walk through the most important settings to get you started. There are many more configuration options available to you. Foremost, there are the [documentation](./documentation.md) and [configuration](./config.md) pages. They also explain how you can operate Promregator using the JAR files (instead of the docker image).
