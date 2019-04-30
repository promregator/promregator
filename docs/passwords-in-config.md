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
java -Dspring.config.location=file:/path/to/your/myconfig.yaml -jar promregator-0.0.1-SNAPSHOT.jar
```

Observe here that the placeholder `${my.password.os.variable}` gets capitalized and all dots in the placeholder gets replaced by the underscore symbol to form the OS environment variable you have to set.

Note that you could also set the Java system properties with the same naming convention, too, but they are typically passed to the application using the command line, which could be spied on by attackers via `ps auxwww` (the command line is not considered a secret in unix'ish systems). 


## Note on Setting OS Environment Variables using Docker
Keep in mind that you able to set OS environment variables for the Docker container by using 
```bash
docker run --env MY_PASSWORD_OS_VARIABLE=mysecretPassword promregator/promregator:0.0.1
```

Further information on this can also be found in the official [Docker reference documentation](https://docs.docker.com/engine/reference/commandline/run/#set-environment-variables--e---env---env-file).

## Using Encrypted Password in Configuration

As an alternative for using environment variables for the password you can use encrypted password (values starting with `{cipher}`) in the configuration.
See also the documentation about [Encryption & Decryprtion](http://cloud.spring.io/spring-cloud-config/spring-cloud-config.html#_encryption_and_decryption) of the Spring Cloud Config Server.

Following example illustrates the usage:

```yaml
promregator:
  targetAuthenticators:
    - id: testAuthenticator
      type: basic
      basic: 
        username: username
        password: '{cipher}682bc583f4641835fa2db009355293665d2647dade3375c0ee201de2a49f7bda'
```

To create the encrypted password you have two options. 
* Use a Spring Cloud Config Server and POST the password to the `/encrypt` endpoint
```bash
curl localhost:8888/encrypt -d mysecret
682bc583f4641835fa2db009355293665d2647dade3375c0ee201de2a49f7bda
``` 
* Use the Spring Cloud CLI extension and encrypt the password on the command line.
```bash
spring encrypt mysecret --key foo
682bc583f4641835fa2db009355293665d2647dade3375c0ee201de2a49f7bda
```

You still need to provide the decryption key to your application for example as environment variable:
```bash
export ENCRYPT_KEY=mySecretKey
java -Dspring.config.location=file:/path/to/your/enrypted/myconfig.yaml -jar promregator-0.0.1-SNAPSHOT.jar
```