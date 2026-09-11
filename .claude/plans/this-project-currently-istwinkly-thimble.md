# Migration Plan: Spring Boot 3.5 → 4.1 + Jackson 3

## Context

This project is currently on Spring Boot 3.5.9 and needs to migrate to Spring Boot 4.1. Additionally, Jackson 2 needs to be migrated to Jackson 3. This plan analyzes the migration guides and the codebase to identify all required changes.

## Current Technology Stack

- **Spring Boot**: 3.5.9
- **Spring Cloud**: 2025.0.1 (needs to be compatible with SB 4.x)
- **Java**: 17
- **Kotlin**: Used (kotlin-stdlib-jdk8, kotlinx-coroutines-*)
- **Jackson**: 2.x (via spring-boot-starter-web)
- **Key dependencies**:
  - spring-boot-starter-web (deprecated in 4.0 → spring-boot-starter-webmvc)
  - spring-boot-starter-security
  - spring-boot-starter-thymeleaf
  - spring-boot-starter-test
  - spring-cloud-starter
  - cloudfoundry-client-reactor
  - Prometheus client (simpleclient)
  - Guava, Caffeine
  - Kotlin coroutines

## Codebase Analysis

### Jackson Usage
1. **InfoV3.java** - Uses `@JsonProperty` from `com.fasterxml.jackson.annotation`
2. **DiscoveryControllerV2.kt** - Uses `@JsonGetter`, `@JsonInclude` from `com.fasterxml.jackson.annotation`
3. **DiscoveryEndpoint.java** - Uses `@JsonGetter` from `com.fasterxml.jackson.annotation`
4. **No custom ObjectMapper, serializers, deserializers, or modules**

### Spring Boot Features Used
1. **Web MVC** - Controllers, RestController, @GetMapping, @RequestMapping
2. **Security** - @EnableWebSecurity, @EnableMethodSecurity, SecurityFilterChain, BasicAuthenticationFilter
3. **Configuration Properties** - @ConfigurationProperties on PromregatorConfiguration
4. **Actuator** - Prometheus metrics, health endpoints
5. **Test** - @SpringBootTest, MockMvc (implicit), Mockito
6. **Cloud Foundry** - Reactive CF client (cloudfoundry-client-reactor)
7. **Kotlin** - One controller in Kotlin
8. **Scheduling** - @EnableScheduling, @Scheduled
9. **Async** - @EnableAsync

### Test Patterns
- Uses @SpringBootTest with custom @Configuration classes
- Uses Mockito.mock() for mocking (NOT @MockBean/@SpyBean - these are not used!)
- Custom test property sources
- MockMvc via SpringBootTest (will need @AutoConfigureMockMvc in 4.0)

---

## Migration Requirements

### Phase 1: Spring Boot 4.0 Migration (Major Breaking Changes)

#### 1.1 Dependency Changes (POM) - HIGH PRIORITY

**Codebase Analysis - What's Actually Used:**
- `spring-boot-starter-web` → **USED** (controllers, REST endpoints)
- `spring-boot-starter-security` → **USED** (SecurityFilterChain, @EnableWebSecurity, method security)
- `spring-boot-starter-thymeleaf` → **USED** (error pages)
- `spring-boot-starter-test` → **USED** (test scope, 15+ @SpringBootTest classes)
- `spring-cloud-starter` → **UNUSED** (no Spring Cloud annotations/imports anywhere - REMOVE)
- `spring-boot-configuration-processor` → **USED** (@ConfigurationProperties)
- `spring-boot-starter-actuator` → **NOT USED** (uses io.prometheus:simpleclient directly)

| Current | Spring Boot 4.0 Replacement |
|---------|----------------------------|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-test` | Replace with modular test starters |
| `spring-cloud-starter` | **REMOVE** (unused dependency) |
| `spring-boot-starter-security` | Keep |
| `spring-boot-starter-thymeleaf` | Keep |
| `spring-boot-configuration-processor` | Keep (optional) |

**New starters needed (direct modular - no classic):**
- `spring-boot-starter-webmvc` (replaces spring-boot-starter-web)
- `spring-boot-starter-jackson` (NEW - for Jackson 3)
- `spring-boot-starter-security` (keep)
- `spring-boot-starter-thymeleaf` (keep)

**Test starters (modular):**
- `spring-boot-starter-security-test` (for future @WithMockUser, @WithAnonymousUser, SecurityMockMvcRequestPostProcessors - NOT used currently)
- `spring-boot-starter-jackson-test` (for future Jackson tests - NOT used currently)
- **NOT NEEDED**: `spring-boot-starter-webmvc-test` (tests don't use MockMvc, WebTestClient, or TestRestTemplate - they test controllers directly via @Autowired)

#### 1.2 Spring Cloud - MINIMAL KEEP (spring-cloud-config-client only)
- Current: 2025.0.1 declared in properties + spring-cloud-starter + spring-cloud-dependencies BOM
- **Action**: Replace with minimal `spring-cloud-config-client` dependency only
- **Rationale**: Tests in `SpringBootLoadPropertiesForTesting.java` and `SpringBootLoadPropertiesForTestingOtherKey.java` verify encrypted property decryption using Spring Cloud Config's `{cipher}` format with `encrypt.key` property. This feature requires `spring-cloud-config-client` which provides the `TextEncryptor` bean and environment post-processor for automatic decryption. The full `spring-cloud-starter` is NOT needed - no other Spring Cloud features (Discovery, Feign, LoadBalancer, CircuitBreaker, Config Server client) are used.
- **Verification done**: 
  - No `@EnableDiscoveryClient`, `@FeignClient`, `@LoadBalanced`, `@RefreshScope`, `@ServiceConnection`, `@EnableCircuitBreaker` anywhere
  - No `org.springframework.cloud.*` imports in main source files
  - CF client manually configured via explicit properties (`cf.api_host`, `cf.username`, `cf.password`, etc.) in `ReactiveCFAccessorImpl.java`
  - Only VCAP reference: `System.getenv("VCAP_APPLICATION")` for OOM restart detection (line 175 of ReactiveCFPaginatedRequestFetcher.java)
  - `cloudfoundry-client-reactor` and `cloudfoundry-operations` are standalone CF Java client libraries (NOT Spring Cloud)
- **Changes**:
  - Remove `spring-cloud-starter` dependency
  - Add `spring-cloud-config-client` dependency (for `{cipher}` decryption in tests)
  - Remove `spring-cloud.version` property
  - Remove `spring-cloud-dependencies` dependencyManagement import (SB 4.x BOM manages Spring Cloud 2025.1)

#### 1.3 Java Version
- Already on Java 17 ✓ (SB 4.0 requires Java 17+)
- Stay on Java 17 for migration; upgrade to Java 21 as post-migration step (Dockerfile already uses Java 21)

#### 1.4 Kotlin Version
- Current: managed by SB 3.5
- SB 4.0 requires Kotlin 2.2+
- Update kotlin.version property

#### 1.5 Removed Features (Check if used)
- ❌ Undertow (not used - using Tomcat)
- ❌ Pulsar Reactive (not used)
- ❌ Embedded launch scripts (not used)
- ❌ Spring Session Hazelcast/MongoDB (not used - no session dependencies)
- ❌ Spock (not used - using JUnit 5)
- ❌ Spring Data MongoDB (not used)
- ❌ Spring Data Redis (not used)
- ❌ Spring Boot Actuator (not used - uses Prometheus client directly)
- ✅ spring-boot-starter-web → deprecated, use webmvc
- ✅ spring-cloud-starter → deprecated, REMOVE (unused)

#### 1.6 Package/Import Changes (Applicable to this codebase)
- `Jackson2ObjectMapperBuilderCustomizer` → `JsonMapperBuilderCustomizer` (**NOT USED** in codebase)
- `@JsonComponent` → `@JacksonComponent` (**NOT USED** in codebase)
- `@JsonMixin` → `@JacksonMixin` (**NOT USED** in codebase)
- **Note**: No `EnvironmentPostProcessor`, `BootstrapRegistry`, or custom `ObjectMapper` beans found
- **Note**: PR #226 (Spring Cloud Config Server support) is NOT merged yet. Only current `{cipher}` decryption for tests needs support.

#### 1.7 Configuration Property Changes (Applicable to this codebase)
- `spring.jackson.read.*` → `spring.jackson.json.read.*` (**NOT USED** in codebase)
- `spring.jackson.write.*` → `spring.jackson.json.write.*` (**NOT USED** in codebase)
- `spring.jackson.parser.*` → `spring.jackson.json.read.*` (**NOT USED** in codebase)
- `spring.dao.exceptiontranslation.enabled` → `spring.persistence.exceptiontranslation.enabled` (**NOT USED**)
- `management.tracing.enabled` → `management.tracing.export.enabled` (**NOT USED** - no Actuator/tracing)
- **NOT APPLICABLE**: `spring.session.*`, `spring.data.mongodb.*`, `management.health.mongodb.*` (no Redis/MongoDB/Actuator)

#### 1.8 Web Changes
- `HttpMessageConverters` deprecated - **NOT USED** (no custom converters)
- `@SpringBootTest` no longer provides MockMvc - **NOT APPLICABLE** (tests don't use MockMvc, test controllers directly)
- `@SpringBootTest` no longer provides WebClient/TestRestTemplate - **NOT APPLICABLE** (no WebClient/RestTemplate in tests)

#### 1.9 Security Changes (Spring Security 7.0)
- `@MockBean` / `@SpyBean` removed - use `@MockitoBean` / `@MockitoSpyBean`
- **Good news**: Codebase doesn't use @MockBean/@SpyBean (uses Mockito.mock() directly) ✓
- `WebSecurityConfigurerAdapter` already not used (using SecurityFilterChain bean) ✓

#### 1.10 Build Plugin Changes
- Maven: Remove `<loaderImplementation>CLASSIC</loaderImplementation>` if present
- Optional dependencies no longer included in uber jars by default

#### 1.11 Properties Migrator
- Add `spring-boot-properties-migrator` as runtime dependency for automatic property migration

---

### Phase 2: Spring Boot 4.1 Migration (now included in Step 2 above)

**Merged into Step 2** since we're migrating directly to Spring Boot 4.1.1. Key 4.1 changes to be aware of:
- Deprecations from 4.0 removed (Derby, Layertools, Dynatrace V1, DevTools LiveReload)
- New properties for OpenTelemetry, Log4j rotation, gRPC
- `spring.data.jpa.repositories.bootstrap-mode` behavior changes
- Jackson 3 enhancements: `spring.jackson.read.*`, `spring.jackson.write.*` general features, factory customizers

---

### Phase 3: Jackson 3 Migration (verified minimal impact)

**Analysis Result**: The codebase uses ONLY Jackson annotations (`@JsonProperty`, `@JsonGetter`, `@JsonInclude`) from `com.fasterxml.jackson.annotation` package. NO other Jackson APIs are used (no custom serializers, deserializers, ObjectMapper, JsonNode, etc.).

#### 3.1 Package/Group ID Changes (for transitive dependencies)

| Old | New |
|-----|-----|
| `com.fasterxml.jackson.core:jackson-*` | `tools.jackson.core:jackson-*` |
| `com.fasterxml.jackson.dataformat:jackson-dataformat-*` | `tools.jackson.dataformat:jackson-dataformat-*` |
| `com.fasterxml.jackson.datatype:jackson-datatype-*` | `tools.jackson.datatype:jackson-datatype-*` |
| `com.fasterxml.jackson.module:jackson-module-*` | `tools.jackson.module:jackson-module-*` |
| **EXCEPTION**: `com.fasterxml.jackson.core:jackson-annotations` | Stays same |

#### 3.2 Import Changes
- Replace `com.fasterxml.jackson.` with `tools.jackson.` everywhere
- **EXCEPT**: `com.fasterxml.jackson.annotation` stays the same (no import changes needed for our 3 files)

#### 3.3 API Changes - NOT APPLICABLE (not used in codebase)

**Classes NOT USED:**
- `JsonSerializer` → `ValueSerializer`
- `JsonDeserializer` → `ValueDeserializer`
- `JsonSerializable` → `JacksonSerializable`
- `SerializerProvider` → `SerializationContext`
- `BeanSerializerModifier` → `ValueSerializerModifier`
- `BeanDeserializerModifier` → `ValueDeserializerModifier`
- `Module` → `JacksonModule`
- `TextNode` → `StringNode`

**Exceptions NOT USED:**
- `JsonProcessingException` → `JacksonException`
- `JsonMappingException` → `DatabindException`
- `JsonParseException` → `StreamReadException`
- `JsonGenerationException` → `StreamWriteException`
- `JsonEOFException` → `UnexpectedEndOfInputException`

**ObjectMapper/JsonFactory NOT USED:**
- No `ObjectMapper` or `JsonMapper` beans defined
- No builder pattern usage

**Features NOT USED:**
- `JsonParser.Feature` → `StreamReadFeature` / `JsonReadFeature`
- `JsonGenerator.Feature` → `StreamWriteFeature` / `JsonWriteFeature`
- `DeserializationFeature` / `SerializationFeature` not configured

**Default changes** - handled by Spring Boot auto-configuration

#### 3.4 Spring Boot Jackson Integration - NOT APPLICABLE
- `Jackson2ObjectMapperBuilderCustomizer` → `JsonMapperBuilderCustomizer` (NOT USED)
- `@JsonComponent` → `@JacksonComponent` (NOT USED)
- `@JsonMixin` → `@JacksonMixin` (NOT USED)
- No custom `ObjectMapper`/`JsonMapper` beans to replace

---

## Detailed Action Items

### POM.xml Changes (matching migration order)

1. **Update parent to Spring Boot 4.0.x**
2. **Replace Spring Cloud with minimal config-client:**
   - Remove `spring-cloud.version` property
   - Remove `spring-cloud-starter` dependency
   - Remove `spring-cloud-dependencies` from dependencyManagement
   - Add `spring-cloud-config-client` dependency (for `{cipher}` decryption in tests)
   - **Add comment in pom.xml** explaining why spring-cloud-config-client is kept (for encrypted property decryption in tests)
3. **Update Kotlin version** to 2.2+
4. **Replace starters (direct modular):**
   - `spring-boot-starter-web` → `spring-boot-starter-webmvc`
   - Keep `spring-boot-starter-security`
   - Keep `spring-boot-starter-thymeleaf`
   - Keep `spring-boot-configuration-processor` (optional)
   - Add `spring-boot-starter-jackson` (NEW - for Jackson 3)
5. **Update test dependencies:**
   - Remove `spring-boot-starter-test`
   - Add `spring-boot-starter-security-test` (for @WithMockUser etc. if used)
   - Add `spring-boot-starter-jackson-test` (for Jackson tests if needed)
6. **Update Jackson dependencies** to `tools.jackson` group IDs (except jackson-annotations)
7. **Add `spring-boot-properties-migrator`** (runtime scope)
8. **Update Kotlin plugins** for Kotlin 2.x
9. **Review cloudfoundry-client-reactor version** - ensure compatibility with Spring Boot 4.x

### Code Changes

#### Jackson Annotations (3 files)
- InfoV3.java: `@JsonProperty` - package stays `com.fasterxml.jackson.annotation` ✓
- DiscoveryControllerV2.kt: `@JsonGetter`, `@JsonInclude` - package stays same ✓
- DiscoveryEndpoint.java: `@JsonGetter` - package stays same ✓

**No import changes needed for annotations!** (jackson-annotations unchanged)

#### Spring Boot Configuration
- Update any `spring.jackson.*` properties to new paths (none currently used in application.yml)
- No actuator/health configuration to review (not using Actuator)

#### Test Changes
- **NO MockMvc changes needed** - tests don't use MockMvc (they test controllers directly via @Autowired)
- **NO WebClient/RestTemplate changes needed** - not used in tests
- Replace any `@MockBean`/`@SpyBean` with `@MockitoBean`/`@MockitoSpyBean` (not used currently ✓)
- If `@WithMockUser` or `@WithAnonymousUser` used → need `spring-boot-starter-security-test`

#### Security Config
- Review SecurityFilterChain bean (already using modern API ✓)
- Check for any deprecated Security APIs

### Configuration Files
- application.yml: Review for renamed properties
- Test property files: Update any deprecated properties

---

## Verification Strategy

### 1. Compile Check
```bash
mvn clean compile -PwithTests
```

### 2. Unit Tests
```bash
mvn test -PwithTests
```

### 3. Integration Tests
- Run Jenkins integration test stage
- Verify Docker image builds

### 4. Property Migration
- Add `spring-boot-properties-migrator` at runtime
- Check startup logs for migrated properties
- Update configuration files accordingly

### 5. Jackson Serialization Tests
- Verify discovery endpoint JSON output
- Verify any JSON serialization/deserialization

---

## Recommended Migration Order

### Step 1: Preparation (Safe, Non-breaking) - ON CURRENT 3.5.9
1. Run `mvn clean compile -PwithTests` to identify deprecation warnings
2. Fix any deprecation warnings in 3.5 codebase
3. Stay on Java 17 for now

### Step 2: Spring Boot 4.1.1 - Direct Modular Starters (NO classic)
1. Change parent to Spring Boot 4.1.1 (released - see https://mvnrepository.com/artifact/org.springframework.boot/spring-boot/4.1.1)
   - Note: 4.0.8 is also available but 4.1.1 includes all 4.1 features
2. **Remove Spring Cloud entirely:**
   - Remove `spring-cloud.version` property
   - Remove `spring-cloud-starter` dependency
   - Remove `spring-cloud-dependencies` from dependencyManagement
   - Spring Cloud 2025.1 is the first to support Spring Boot 4.x (managed by SB 4.x BOM if needed)
3. Update Kotlin to 2.2+ (update kotlin.version property)
4. Replace starters:
   - `spring-boot-starter-web` → `spring-boot-starter-webmvc`
   - Keep `spring-boot-starter-security`
   - Keep `spring-boot-starter-thymeleaf`
   - Keep `spring-boot-configuration-processor` (optional)
   - Add `spring-boot-starter-jackson` (NEW - for Jackson 3)
5. Update test dependencies:
   - Remove `spring-boot-starter-test`
   - Add `spring-boot-starter-security-test` (if @WithMockUser etc. used)
   - Add `spring-boot-starter-jackson-test` (if needed)
6. Add `spring-boot-properties-migrator` (runtime scope)
7. Update Jackson dependencies to `tools.jackson` group IDs (except jackson-annotations)
8. Fix compilation errors (imports, removed APIs, package changes)
9. Run tests

### Step 4: Post-Migration Cleanup (can be done incrementally)
1. Remove `spring-boot-properties-migrator`
2. Update configuration files with migrated properties (from migrator logs)
3. Upgrade to Java 21 (Dockerfile already uses Java 21)
4. Update Jenkinsfile: Spring Boot CLI to 3.x+, compatible Spring Cloud CLI for encryption test
5. Remove any unused dependencies
6. Update documentation

---

## Questions for Clarification - RESOLVED

1. **Spring Cloud Version**: Use Spring Cloud 2025.1 (managed by Spring Boot 4.x). Spring Cloud 2025.0 does NOT support Spring Boot 4.x.

2. **Jackson Migration Strategy**: Option A - Direct migration to Jackson 3 (minimal Jackson usage in codebase makes this feasible).

3. **Java Version**: Stay on Java 17 for migration; upgrade to Java 21 as post-migration step.

4. **Classic vs Modular Starters**: **Direct modular starters** (no classic) - Spring Boot 4.1.1 is stable and we've verified minimal dependencies. Classic starters not needed.

5. **Test Dependencies**: No MockMvc, no @WithMockUser, no @MockBean/@SpyBean. Tests use @SpringBootTest with custom configs and manual Mockito.mock(). No `spring-boot-starter-webmvc-test` needed. `spring-boot-starter-security-test` and `spring-boot-starter-jackson-test` only if needed in future.

6. **Integration Test / Dockerfile / Spring Cloud CLI**: 
   - Dockerfile uses `sapmachine:21.0.9-jdk-headless-ubuntu-noble` (Java 21) - already on Java 21 in container!
   - Jenkinsfile uses Spring Boot CLI 2.7.18 and Spring Cloud CLI 3.1.1 for password encryption testing
   - Spring Cloud CLI 3.1.1 is for Spring Boot 2.x/3.x - need to update for SB 4.x compatibility
   - The encryption format (`{cipher}...`) uses Spring Cloud Config's encrypt/decrypt - this should remain compatible as it's a separate library
   - **Action**: Update Jenkinsfile to use Spring Boot CLI 3.x+ and compatible Spring Cloud CLI version for SB 4.x

7. **spring-boot-starter-web → spring-boot-starter-webmvc**: This is purely a dependency rename. No code changes needed.

8. **Spring Boot 3.5 Deprecation Checks**: Yes, run `mvn clean compile -PwithTests` on current 3.5.9 first to identify any deprecation warnings before upgrading.