# Extending Promgregator

Promregator supports extending it. Starting with 0.10.0, Promregator not just provides runnable artifacts like the `promregator.jar` file or the Docker image, but also `promregator-library` (available through Maven repositories) is delivered. You may include this library as a dependency to your own project, for example by using the following dependency declaration in your `pom.xml`:

```xml
<dependency>
	<groupId>com.github.promregator</groupId>
	<artifactId>promregator-library</artifactId>
	<version>0.10.0</version> <!-- or any other higher version number -->
</dependency>
```

The artifact using `promregator-library` in such a way is called "Promregator extension".

## Public Solution Model
From a technical point of view Promregator extensions may refer to every artifact provided by Promregator (in particular classes). *However, by default Promregator does not give any guarantee on whether the interfaces, signatures and types will stay the same!* To say it even more explicitly: *Promregator reserves the right to alter, modify, remove existing, add new and/or refactor existing classes by default*. This might lead to significant adoption effort for Promregator extensions as soon as newer versions are released by Promregator.

To lower the effort and risk of high-effort activities, Promregator uses the annotation `org.cloudfoundry.promregator.meta.Released` to explicitly indicate artifacts which are released for usage by Promregator extensions. The semantics of this annotation is that Promregator tries to keep these interfaces as stable as possible even across future versions with the intention to not harm existing extensions.
This does not imply that no changes are allowed to be made to these entities - however the changes are attempted to be kept compatible.

Currently, the following interfaces are `@Released`:
* `org.cloudfoundry.promregator.cfaccessor.CFApiCredentials`

For further details please refer to the JavaDoc documentation of the corresponding interfaces.

The list may be extended upon clarification of the corresponding scenario. If you have a new scenario which you want to get supported, please create a [discussion at Promregator project](https://github.com/promregator/promregator/discussions).
