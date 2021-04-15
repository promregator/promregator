# SSL Contexts in Promregator

Promregator supports using system properties to specify SSL Contexts used for
outgoing communications to scrape targets. The SSL Context is specified on the JVM
level and thus cannot differ between targets. It will be applied to all targets.

The ability to configure an SSL Context allows Promregator to negotiate mTLS to targets
and verify their certificates if signed by an alternate CA.

## Prerequisites

- You have a keystore containing the client certificate for Promregator to use for
outgoing communications
  
- You have a Java trust store containing your CA

## Specifying an SSL Context

To start Promregator with an SSL Context you simply pass arguments to the JVM.

### Supported Properties:
- `javax.net.ssl.keyStore` The path to the Java keystore file containing the processes
own certificate and private key.
  
- `javax.net.ssl.keyStorePassword` Password to access the keystore

- `javax.net.ssl.trustStore` The path to the Java trust store file containing the CA
certificates that Promregator should trust
  
- `javax.net.ssl.trustStorePassword` Password to access the trust store

- `javax.net.debug` Optionally set this to `ssl` to enable logging for the SSL/TLS layer

### Example

```shell
java -Djavax.net.ssl.keyStore=/home/example/key.jks -Djavax.net.ssl.keyStorePassword=example -Djavax.net.ssl.trustStore=/home/example/trust.jks -Djavax.net.ssl.trustStorePassword=example
```

If using the docker container you can specify these options using the `JAVA_OPTS` environment variable
```shell
docker run -e JAVA_OPTS='-Djavax.net.ssl.keyStore...' ...
```
