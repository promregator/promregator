## Spring Cloud Support Policy
Spring Cloud follows the [VMware Tanzu OSS support policy](https://tanzu.vmware.com/support/oss) for critical bugs and security issues.

* Major versions are supported for at least 3 years from the release date (but you must run a supported minor version).
* Minor versions are supported for at least 12 months.

Commercial support is also available from VMware, which offers an extended support period.

All Spring Cloud releases are publicly available from Maven Central and https://repo.spring.io.
We do not have a private repository reserved only for paying customers.

We recommend that all users migrate to the latest GA release.

## Releases
Release Trains are a collection of individual projects. As such, release trains do not have a support timeline of their own but, rather, have a cumulative support timeline based on each included project.

While release trains do not have a support timeline, you can assume all the projects within a given release train are still supported if the latest Spring Boot version supported by a given release train is still supported.  In general projects within a given Spring Cloud release train will no longer be supported within 3 months after Spring Boot ends its support.  Spring Boot support timelines can be found [here](https://spring.io/projects/spring-boot#support).

For example, Spring Cloud 2023.0 (Leyton) supports Spring Boot 3.3.x and 3.2.x.  You can assume that all the projects within the Spring cloud 2023.0 release train are still supported as long as Spring Boot 3.3.x is still supported.

### Supported Releases
The following table is a list of Spring Cloud Release Trains, the supported Spring Boot versions, and the included projects. Each project has a link to the official support timelines on https://spring.io. The header row is the Release Train name. For Release Trains with CALVER, the codename is included in parenthesis. The first column is the project name with a link to the support page. This table may include Release Trains that are under current development. The Release Train for these are in *italics*. For convenience, Release Trains outside of the OSS support policy are marked with strikethrough (please see the project page for up-to-date information).

|     | _2025.1 (Oakwood)_ | 2025.0 (Northfields)   | ~~2024.0 (Moorgate)~~ | ~~2023.0 (Leyton)~~ | ~~2022.0 (Kilburn)~~ |
| --- | ---------------- | ---------------- | ---------------------- | --------------- | -------------------- |
[spring-boot](https://spring.io/projects/spring-boot#support) | _4.0.x_ | 3.5.x | 3.4.x | 3.3.x/3.2.x | 3.1.x/3.0.x |
 |  |  |  |  |
[spring-cloud-bus](https://spring.io/projects/spring-cloud-bus#support) | _5.0.x_ | 4.3.x  | 4.2.x | 4.1.x | 4.0.x |
[spring-cloud-circuitbreaker](https://spring.io/projects/spring-cloud-circuitbreaker#support) | _5.0.x_ | 3.3.x  | 3.2.x  | 3.1.x | 3.0.x
[spring-cloud-commons](https://spring.io/projects/spring-cloud-commons#support) | _5.0.x_ | 4.3.x | 4.2.x | 4.1.x | 4.0.x  |
[spring-cloud-config](https://spring.io/projects/spring-cloud-config#support) | _5.0.x_ | 4.3.x  | 4.2.x | 4.1.x | 4.0.x  |
[spring-cloud-consul](https://spring.io/projects/spring-cloud-consul#support) | _5.0.x_ | 4.3.x  | 4.2.x | 4.1.x | 4.0.x  |
[spring-cloud-contract](https://spring.io/projects/spring-cloud-contract#support) | _5.0.x_ | 4.3.x  | 4.2.x | 4.1.x | 4.0.x  |
[spring-cloud-function](https://spring.io/projects/spring-cloud-function#support) | _5.0.x_ | 4.3.x  | 4.2.x | 4.1.x | 4.0.x  |
[spring-cloud-gateway](https://spring.io/projects/spring-cloud-gateway#support) | _5.0.x_ | 4.3.x  | 4.2.x | 4.1.x | 4.0.x  |
[spring-cloud-kubernetes](https://spring.io/projects/spring-cloud-kubernetes#support) | _5.0.x_ | 3.3.x  | 3.2.x | 3.1.x | 3.0.x  |
[spring-cloud-netflix](https://spring.io/projects/spring-cloud-netflix#support) | _5.0.x_ | 4.3.x  | 4.2.x | 4.1.x | 4.0.x  |
[spring-cloud-openfeign](https://spring.io/projects/spring-cloud-openfeign#support) | _5.0.x_ | 4.3.x  | 4.2.x | 4.1.x | 4.0.x  |
[spring-cloud-stream](https://spring.io/projects/spring-cloud-stream#support) | _5.0.x_ | 4.3.x  | 4.2.x | 4.1.x | 4.0.x |
[spring-cloud-task](https://spring.io/projects/spring-cloud-task#support) | _5.0.x_ | 3.3.x  | 3.2.x | 3.1.x | 3.0.x |
[spring-cloud-vault](https://spring.io/projects/spring-cloud-vault#support) | _5.0.x_ | 4.3.x  | 4.2.x | 4.1.x | 4.0.x  |
[spring-cloud-zookeeper](https://spring.io/projects/spring-cloud-zookeeper#support) | _5.0.x_ | 4.3.x  | 4.2.x | 4.1.x | 4.0.x  |

### Historical Versions

See [Historical Versions](https://github.com/spring-cloud/spring-cloud-release/wiki/Historical-Versions) for the full list of End of Life Release trains.

### Release Train Naming

In early 2020, the [release train versioning scheme changed](https://spring.io/blog/2020/04/17/spring-cloud-2020-0-0-m1-released). We now follow [Calendar Versioning](https://calver.org/) or calver for short. We follow the `YYYY.MINOR.MICRO` [scheme](https://calver.org/#scheme), where `MINOR` is an incrementing number that starts at zero each year. The `MICRO` segment corresponds to suffixes previously used: `.0` is analogous to `.RELEASE` and `.2` is analogous to `.SR2`. Pre-release suffixes also change from using a `.` to a `-` for the separator, for example `2020.0.0-M1` and `2020.0.0-RC2`. We will also stop prefixing snapshots with `BUILD-` -- for example, `2020.0.0-SNAPSHOT`. A Release Train generation is defined by the first two parts of the version, so `2020.0` (codename `Ilford`) is the Release Train generation, and `2020.0.0` is the first release. In other words, the first two parts of the version **are not MAJOR.MINOR**.

The previous scheme was based on London Tube Station names. These will be continued to be used for code names, but these names will no longer be used in versions published to Maven repositories.

## Third-party dependencies
Spring Cloud provides managed dependencies for some third-party libraries. These libraries are typically upgraded at the patch level for any given Spring Cloud release. Since we typically do not upgrade the minor or major versions of third-party libraries with our patch releases, you should check the EOL policies of projects that you depend on, since you may find that you use a supported version of Spring Cloud against an unsupported third-party library.

## Enterprise Support
Enterprise support for Spring Cloud is available.
Please see https://spring.io/support for details.
