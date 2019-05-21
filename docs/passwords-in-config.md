# Specifying Passwords in Configuration

When configuring Promregator there are multiple situations where you are prompted for specifying a password. 

**WARNING!** In general it is highly discouraged to entering the passwords in plain-text. This is considered a security flaw.


## Better Approach Than Specifying Passwords in Plain-Text

As stated already on the [documentation page](./documentation.md), Promregator supports all the configuration capabilities, which come with Spring Boot. Two options can be combined intelligently to provide a proper solution for injecting passwords into the configuration environment. These two options are:

* [Placeholders in Properties](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html#boot-features-external-config-placeholders-in-properties) and
* Using [OS environment variables](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html#boot-features-external-config) for setting properties.

This also permits setting password for such complex cases like in [target-specific authentication configurations](./outbound-authentication.md).

Please find here an example how this can be done:

```yaml
promregator:
  targetAuthenticators:
    - id: testAuthenticator
      type: basic
      basic: 
        username: username
        password: ${my.password.os.variable}
```

In the example above you see that the password was not specified directly, but a placeholder property was used for it. To fill the placeholder property with the appropriate value, you may start Promregator like this:

```bash
export MY_PASSWORD_OS_VARIABLE=mysecretPassword
java -Dspring.config.location=file:/path/to/your/myconfig.yaml -jar promregator-x.y.z.jar
```

Observe here that the placeholder `${my.password.os.variable}` gets capitalized and all dots in the placeholder gets replaced by the underscore symbol to form the OS environment variable you have to set.

Note that you could also set the Java system properties with the same naming convention, too, but they are typically passed to the application using the command line, which could be spied on by attackers via `ps auxwww` (the command line is not considered a secret in unix'ish systems). 


## Note on Setting OS Environment Variables using Docker
Keep in mind that you able to set OS environment variables for the Docker container by using 
```bash
docker run --env MY_PASSWORD_OS_VARIABLE=mysecretPassword promregator/promregator:x.y.z
```

Further information on this can also be found in the official [Docker reference documentation](https://docs.docker.com/engine/reference/commandline/run/#set-environment-variables--e---env---env-file).

## Using Encrypted Password in Configuration

As an alternative for using environment variables for the password, you can use encrypted password (values starting with `{cipher}`) in the configuration (available since Promregator version 0.6.0).
This allows you to check the configuration into a version control system. 
See also the documentation about [Encryption & Decryption](http://cloud.spring.io/spring-cloud-config/spring-cloud-config.html#_encryption_and_decryption) of the Spring Cloud Config Server.

To encrypt a value you can use the [Spring Cloud CLI](https://cloud.spring.io/spring-cloud-cli/) extension and encrypt the password on the command line.
```bash
spring encrypt mysecret --key mySecretKey
d93dd725143e187804e4105a681706c1185c58f92086bf55aa830e61c0527060
```

Then you need to set the encryption key as environment variable so that promregator can process it:
```bash
export ENCRYPT_KEY=mySecretKey
java -Dspring.config.location=file:/path/to/your/config/with/encrypted/attributes/myconfig.yaml -jar promregator-x.y.z-SNAPSHOT.jar
```

The config file would the look something like this. Note the value of the password attribute.

```yaml
promregator:
  targetAuthenticators:
    - id: testAuthenticator
      type: basic
      basic: 
        username: username
        password: '{cipher}d93dd725143e187804e4105a681706c1185c58f92086bf55aa830e61c0527060'
```

If you are running promregator as docker container you can provide the environment variable in the following way

```bash
docker run -d \
 --env ENCRYPT_KEY=mySecretKey \
 -v /path/to/your/config/with/encrypted/attributes/myconfig.yaml:/etc/promregator/promregator.yml \
 promregator/promregator:<version>
```

### Note on Spring Boot Version Clashes

Note that there is an incompatibility on the spring-boot-cli at 2.0.9 and above against the spring-boot-cloud-cli 2.0.0. To prevent this, make sure that you first install spring-boot-cli:2.0.8 until a compatible version is provided.


### Support for Docker Secrets

The Docker image of Promregator (starting with version 0.6.0) also supports setting the ENCRYPT_KEY environment variable using the [Docker Secrets approach](https://docs.docker.com/engine/swarm/secrets/). Note that there is also a configuration option "secrets" in Docker Compose, which permits you [using docker secrets even without having swarm enabled](https://stackoverflow.com/a/48460539).

For this, the environment variable `ENCRYPT_KEY_FILE` must specify the name of the file containing the secret in the `/run/secrets` directory. Here is an example:

Let us assume that you have the following two files in your folder:

```
docker-compose.yaml
promregator_encrypt_key.txt
```

The latter file contains the secret which you want to set as `ENCRYPT_KEY` (keep in mind that you should NOT have a newline at the end in this file, if you do not mean so!). Then, consider the following content of your `docker-compose.yaml` file:

```yaml
version: '3.3'

services:
  promregator:
    image: promregator/promregator:0.6.0
    environment:
     ENCRYPT_KEY_FILE: promregator_encrypt_key
    volumes:
     - ./promregator.yaml:/etc/promregator/promregator.yml
    secrets:
     - promregator_encrypt_key
    
secrets:
  promregator_encrypt_key:
    file: promregator_encrypt_key.txt
```

Technically, this makes docker read the file `promregator_encrypt_key.txt` and bind it as `/run/secrets/promregator_encrypt_key` into the running container. The value `promregator_encrypt_key` of the environment variable `ENCRYPT_KEY_FILE` instructs Promregator to look for the file `/run/secrets/promregator_encrypt_key` and retrieve its content. It then will be mapped to the `ENCRYPT_KEY` environment variable prior to starting Promregator.

By this, the spring cloud encryption key never is stored in the container's environment context, but only read at the last moment into the environment *inside* the container, where it is the hardest to be retrieved for a potential attacker.
