# Outbound Authentication

## Authentication Schemes Supported

Promregator is capable of authenticating towards scraping targets with different schemes. The supported types are

| Type identifier        | Description |
|------------------------|-------------|
| null                   | A null-authentication, i.e. no addition authentication takes place (may become handy in certain configuration cases) |
| basic                  | Basic Authentication scheme using plain-text-based Basic Authentication as defined in [RFC2617](https://www.ietf.org/rfc/rfc2617.txt) |
| OAuth2XSUAABasic       | OAuth2/JWT-based authentication scheme using grant type "Client Credentials" with basic authentication as used for XSUAA servers |
| OAuth2XSUAACertificate | OAuth2/JWT-based authentication scheme using grant type "Client Credentials" with certificate-based authentication as used for XSUAA servers |

Depending on the different authentication schemes, additional configurations may need to be added to enable them. 

## Authentication Schemes vs. Authentication Configuration

Promregator permits to use different authentication configurations at the same point in time. Each authentication configuration has to define which of the configuration scheme shall get used. There may only be exactly one authentication scheme used by a authentication configuration.

An authentication configuration has a unique ID by which it can be identified. This is the *authentication configuration identifier*.

You may picture the authentication configuration as something, which contains everything to properly perform an authentication (e.g. it includes the username, password, etc.), whilst an authentication scheme is the "abstract protocol", which describes how the authentication process needs to happen. 


## Global Authentication vs. Target-specific Authentication

Promregator supports to configure two types of authentication: 

* **Target-specific authentication**: It is possible to define for each target to scrape, which authentication configuration shall be used. You do so, by specifying an authentication configuration identifier in the corresponding configuration of the target. 
* **Global authentication**: If no authentication configuration is provided for a target, a global authentication configuration is being used as a fallback. There is a special configuration subgroup with which you may specify how this authentication configuration should look like. 

In general, the structure and the capabilities of authentication configurations used both a target-specific authentication and a global authentication are of equal power. 

## Defining an Authentication Configuration

An authentication configuration mainly looks like the following example of a YAML structure:
``` yaml
type: basic
basic: 
  username: myusername
  password: mypassword
```

The authentication configuration primarily consists of a type definition which specifies the identifier for the authentication scheme to be used (here `basic`). Depending on the authentication scheme used, additional configuration may be necessary. See below for a verbose description. 


### Authentication Configuration for Null Authentication

No additional configuration options are necessary for this authentication. 


### Authentication Configuration for Basic Authentication

The Basic Authentication scheme requires two additional configuration options, which must be supplied:

* `username`: specifies the username which shall be used when authenticating.
* `password`: specifies the password which shall be used when authenticating.

Note that specifying the password plain-text in the configuration file is highly discouraged due to security reasons. For an alternative solution, please refer to [this page](./passwords-in-config.md).

An example of a authentication configuration using basic authentication looks like this:

``` yaml
type: basic
basic: 
  username: myusername
  password: mypassword
```

### Authentication Configuration for OAuth2XSUAA Authentication

The OAuth2XSUAA Authentication type is deprecated and remains present due to backward compatibility. It is replaced by OAuth2XSUAABasic and behaves in the
same way like that authentication type.

### Authentication Configuration for OAuth2XSUAABasic Authentication

The OAuth2XSUAABasic Authentication scheme allows to set the following configuration options:

* `tokenServiceURL` (mandatory): specifies the URL of the OAuth2 server, which shall be used to retrieve the token.
* `client_id` (mandatory): specifies the client identifier which shall be used when authenticating at the OAuth2 server.
* `client_secret` (mandatory): specifies the client secret which shall be used when authenticating at the OAuth2 server.
* `scopes` (optional): specifies the scopes which shall be requested from the OAuth2 server during the call. If not specified, an empty string is assumed, which will suppress a dedicated request of scopes. Usually, OAuth2 servers then provide a JWT, which contains all scopes allowed for the set of credentials provided.

Note that specifying the secret plain-text in the configuration file is highly discouraged due to security reasons. For an alternative solution, please refer to [this page](./passwords-in-config.md).

An example of a authentication configuration using basic authentication looks like this:

``` yaml
type: oauth2xsuaaBasic
oauth2xsuaaBasic:
  tokenServiceURL: https://instance.subdomain.example.org/oauth/token
  client_id: myclientid
  client_secret: mysecret
  scopes: scopea,scopeb
```

### Authentication Configuration for OAuth2XSUAACertificate Authentication

The OAuth2XSUAACertificate Authentication scheme allows to set the following configuration options:

* `tokenServiceCertURL` (mandatory): specifies the URL of the OAuth2 server, which shall be used to retrieve the token.
* `client_id` (mandatory): specifies the client identifier which shall be used when authenticating at the OAuth2 server.
* `client_certificates` (mandatory): specifies the certificate (chain) which shall be used when authenticating at the OAuth2 server.
* `client_key` (mandatory): specifies the key which shall be used when authentication at the OAuth2 server. The key must fit to the certificate used above.
* `scopes` (optional): specifies the scopes which shall be requested from the OAuth2 server during the call. If not specified, an empty string is assumed, which will suppress a dedicated request of scopes. Usually, OAuth2 servers then provide a JWT, which contains all scopes allowed for the set of credentials provided.

An example of an authentication configuration using certificate-based authentication looks like this:

``` yaml
type: oauth2xsuaaCertificate
oauth2xsuaaCertificate:
  tokenServiceCertURL: https://instance.subdomain.example.org/oauth/token
  client_id: myclientid
  client_certificates: "-----BEGIN CERTIFICATE-----..."
  client_key: "-----BEGIN RSA PRIVATE KEY-----..."
  scopes: scopea,scopeb
```

## Full-blown Example of a Complex Configuration Scenario

To demonstrate a complex scenario for a configuration having both target-specific authentication and global authentication in place, let us have a look at the following example (see also the [configuration page](./config.md)):

```yaml

promregator:
  authenticator:
   type: basic
   basic: 
     username: globalUsername
     password: globalPassword

  targetAuthenticators:
   - id: targetAuthenticator1
     type: basic
     basic:
       username: targetUsername
       password: targetPassword

   - id: targetAuthenticatorNull
     type: null
  
  targets:
    - orgName: someOrg
      spaceName: someSpace
      appName: app1
      authenticatorId: targetAuthenticator1

    - orgName: someOrg
      spaceName: someSpace
      appName: app2
      authenticatorId: targetAuthenticatorNull
      
    - orgName: someOrg
      spaceName: someSpace
      appName: app3
```

The above example defines the following artifacts:
* A global authentication is configured, which uses the Basic Authentication scheme. This authentication configuration uses the username `globalUsername` and `globalPassword` as password. 
* Two further authentication configurations are defined:
  * Then authentication configuration with the identifier `targetAuthenticator1` specifies that the Basic Authentication scheme shall be used. However, in this case, the username used for authentication is `targetUsername` and the corresponding password is `targetPassword`.
  * The second authentication configuration is identified by its identifier called `targetAuthenticatorNull`. This authentification configuration specifies that the Null Authentication scheme shall be used, i.e. no special authentication takes place.
* Moreover, the example also specifies three targets to scrape:
  * The first application called `app1` is configured to refer to the authentication configuration `targetAuthenticator1`.
  * The second application called `app2` is configured to refer to the authentication configuration `targetAuthenticatorNull`.
  * The configuration for the third application called `app3` does not specify any reference to an authentication configuration (the attribute `authenticatorId` is omitted).

This leads the following runtime behavior when targets are being requested to be scraped:
* The application `app1` will be scraped using the Basic Authentication scheme using `targetUsername`/`targetPassword` as username and password.
* The application `app2` will be scraped using the Null Authentication scheme, i.e. no authentication scheme is being applied.
* The application `app3` will be scraped using the Basic Authentication scheme using `globalUsername`/`globalPassword` as username and password.
