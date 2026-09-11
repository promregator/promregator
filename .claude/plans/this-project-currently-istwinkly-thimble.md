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

| Current | Spring Boot 4.0 Replacement |
|---------|----------------------------|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` (classic: `spring-boot-starter-classic`) |
| `spring-boot-starter-test` | Remove - use technology-specific test starters |
| `spring-cloud-starter` | Update to Spring Cloud 2025.x compatible with SB 4.x |
| `spring-boot-starter-security` | Keep (but test → `spring-boot-starter-security-test`) |

**New starters needed based on usage:**
- `spring-boot-starter-webmvc` (replaces web)
- `spring-boot-starter-thymeleaf` (keep)
- `spring-boot-starter-actuator` (for metrics/health)
- `spring-boot-starter-security` (keep)
- `spring-boot-starter-jackson` (for Jackson 3 - NEW in 4.0)
- `spring-boot-starter-validation` (if using Jakarta Validation)

**Test starters:**
- `spring-boot-starter-webmvc-test` (replaces spring-boot-starter-test for web tests)
- `spring-boot-starter-security-test` (for @WithMockUser, etc.)
- `spring-boot-starter-jackson-test` (for Jackson tests)

**Classic starter option (easier initial migration):**
- Add `spring-boot-starter-classic` and `spring-boot-starter-test-classic` as interim step
- Then gradually migrate to modular starters

#### 1.2 Spring Cloud Version
- Current: 2025.0.1
- Need: Spring Cloud 2025.x compatible with Spring Boot 4.x
- Check https://github.com/spring-cloud/spring-cloud-release/wiki/Supported-Versions

#### 1.3 Java Version
- Already on Java 17 ✓ (SB 4.0 requires Java 17+)
- Consider upgrading to Java 21 (LTS)

#### 1.4 Kotlin Version
- Current: managed by SB 3.5
- SB 4.0 requires Kotlin 2.2+
- Update kotlin.version property

#### 1.5 Removed Features (Check if used)
- ❌ Undertow (not used - using Tomcat)
- ❌ Pulsar Reactive (not used)
- ❌ Embedded launch scripts (not used)
- ❌ Spring Session Hazelcast/MongoDB (not used)
- ❌ Spock (not used - using JUnit 5)
- ✅ spring-boot-starter-web → deprecated, use webmvc

#### 1.6 Package/Import Changes
- `EnvironmentPostProcessor`: `org.springframework.boot.env` → `org.springframework.boot`
- `BootstrapRegistry`: `org.springframework.boot` → `org.springframework.boot.bootstrap`
- `Jackson2ObjectMapperBuilderCustomizer` → `JsonMapperBuilderCustomizer`
- `@JsonComponent` → `@JacksonComponent`
- `@JsonMixin` → `@JacksonMixin`

#### 1.7 Configuration Property Changes
- `spring.jackson.read.*` → `spring.jackson.json.read.*`
- `spring.jackson.write.*` → `spring.jackson.json.write.*`
- `spring.jackson.parser.*` → `spring.jackson.json.read.*` (or JsonMapperBuilderCustomizer)
- `server.forward-headers-strategy` no longer works in WAR deployments
- `spring.session.redis.*` → `spring.session.data.redis.*`
- `spring.data.mongodb.*` (connection props) → `spring.mongodb.*`
- `management.health.mongodb.enabled` → `management.health.mongodb.enabled` (renamed)
- `spring.dao.exceptiontranslation.enabled` → `spring.persistence.exceptiontranslation.enabled`
- `management.tracing.enabled` → `management.tracing.export.enabled`

#### 1.8 Web Changes
- `HttpMessageConverters` deprecated - use `ClientHttpMessageConvertersCustomizer` / `ServerHttpMessageConvertersCustomizer`
- `@SpringBootTest` no longer provides MockMvc - need `@AutoConfigureMockMvc`
- `@SpringBootTest` no longer provides WebClient/TestRestTemplate - need `@AutoConfigureTestRestTemplate` or `@AutoConfigureRestTestClient`

#### 1.9 Security Changes (Spring Security 7.0)
- `@MockBean` / `@SpyBean` removed - use `@MockitoBean` / `@MockitoSpyBean`
- **Good news**: Codebase doesn't use @MockBean/@SpyBean (uses Mockito.mock() directly) ✓
- `WebSecurityConfigurerAdapter` already not used (using SecurityFilterChain bean) ✓

#### 1.10 Actuator/Health
- Liveness/readiness probes enabled by default
- `management.endpoint.health.probes.enabled` to disable if needed

#### 1.11 Build Plugin Changes
- Maven: Remove `<loaderImplementation>CLASSIC</loaderImplementation>` if present
- Optional dependencies no longer included in uber jars by default

#### 1.12 Properties Migrator
- Add `spring-boot-properties-migrator` as runtime dependency for automatic property migration

---

### Phase 2: Spring Boot 4.1 Migration (Incremental)

#### 2.1 Deprecations Removed
- Derby support deprecated
- Layertools jar mode removed
- Dynatrace V1 API properties removed
- DevTools LiveReload deprecated

#### 2.2 Configuration Changes
- New properties for OpenTelemetry, Log4j rotation, gRPC, etc.
- `spring.data.jpa.repositories.bootstrap-mode` behavior changes

#### 2.3 Jackson 3 Enhancements
- General read/write features: `spring.jackson.read.*`, `spring.jackson.write.*`
- Factory customizers: `JsonFactoryBuilderCustomizer`, etc.

---

### Phase 3: Jackson 3 Migration

#### 3.1 Package/Group ID Changes

| Old | New |
|-----|-----|
| `com.fasterxml.jackson.core:jackson-*` | `tools.jackson.core:jackson-*` |
| `com.fasterxml.jackson.dataformat:jackson-dataformat-*` | `tools.jackson.dataformat:jackson-dataformat-*` |
| `com.fasterxml.jackson.datatype:jackson-datatype-*` | `tools.jackson.datatype:jackson-datatype-*` |
| `com.fasterxml.jackson.module:jackson-module-*` | `tools.jackson.module:jackson-module-*` |
| **EXCEPTION**: `com.fasterxml.jackson.core:jackson-annotations` | Stays same |

#### 3.2 Import Changes
- Replace `com.fasterxml.jackson.` with `tools.jackson.` everywhere
- **EXCEPT**: `com.fasterxml.jackson.annotation` stays the same

#### 3.3 API Changes (Code)

**Classes renamed:**
- `JsonSerializer` → `ValueSerializer`
- `JsonDeserializer` → `ValueDeserializer`
- `JsonSerializable` → `JacksonSerializable`
- `SerializerProvider` → `SerializationContext`
- `BeanSerializerModifier` → `ValueSerializerModifier`
- `BeanDeserializerModifier` → `ValueDeserializerModifier`
- `Module` → `JacksonModule`
- `TextNode` → `StringNode`

**Exceptions renamed:**
- `JsonProcessingException` → `JacksonException`
- `JsonMappingException` → `DatabindException`
- `JsonParseException` → `StreamReadException`
- `JsonGenerationException` → `StreamWriteException`
- `JsonEOFException` → `UnexpectedEndOfInputException`

**ObjectMapper/JsonFactory changes:**
- Now immutable - use Builder pattern
- `JsonMapper.builder().build()` instead of `new ObjectMapper()`
- Format-specific mappers mandatory: `JsonMapper`, `XmlMapper`, etc.
- `copy()` method removed - use `rebuild().build()`

**Features renamed:**
- `JsonParser.Feature` → `StreamReadFeature` / `JsonReadFeature`
- `JsonGenerator.Feature` → `StreamWriteFeature` / `JsonWriteFeature`
- `DeserializationFeature` / `SerializationFeature` → some moved to `DateTimeFeature`, `EnumFeature`

**Default changes:**
- `FAIL_ON_TRAILING_TOKENS` enabled by default
- `WRITE_DATES_AS_TIMESTAMPS` disabled by default
- `SORT_PROPERTIES_ALPHABETICALLY` enabled by default
- `DEFAULT_VIEW_INCLUSION` disabled by default
- `FAIL_ON_NULL_FOR_PRIMITIVES` enabled by default

#### 3.4 Spring Boot Jackson Integration
- `Jackson2ObjectMapperBuilderCustomizer` → `JsonMapperBuilderCustomizer`
- `@JsonComponent` → `@JacksonComponent`
- `@JsonMixin` → `@JacksonMixin`
- Auto-configured mappers: `JsonMapper` (JSON), `XmlMapper` (XML)
- To replace: define `JsonMapper` bean, not `ObjectMapper`
- `spring.jackson.use-jackson2-defaults=true` for compatibility mode
- `spring-boot-jackson2` module available as deprecated stop-gap

---

## Detailed Action Items

### POM.xml Changes

1. **Update parent to Spring Boot 4.0.x** (4.1.x may not be released yet; start with 4.0.x)
2. **Update Spring Cloud version** to 2025.1 (managed by Spring Boot 4.x via BOM)
3. **Update Kotlin version** to 2.2+
4. **Replace starters (using classic initially):**
   - Add `spring-boot-starter-classic` (replaces spring-boot-starter-web, etc.)
   - Add `spring-boot-starter-test-classic` (replaces spring-boot-starter-test)
   - Keep `spring-boot-starter-thymeleaf`, `spring-boot-starter-security`
   - Add `spring-boot-starter-jackson` (NEW - for Jackson 3)
5. **Update test dependencies (when moving to modular):**
   - Remove `spring-boot-starter-test-classic`
   - Add `spring-boot-starter-webmvc-test`
   - Add `spring-boot-starter-security-test`
   - Add `spring-boot-starter-jackson-test`
6. **Update Jackson dependencies** to `tools.jackson` group IDs (except jackson-annotations)
7. **Add `spring-boot-properties-migrator`** (runtime scope)
8. **Update Kotlin plugins** for Kotlin 2.x
9. **Remove explicit Spring Cloud version** - let Spring Boot 4.x BOM manage it
10. **Review cloudfoundry-client-reactor version** - ensure compatibility with Spring Cloud 2025.1

### Code Changes

#### Jackson Annotations (3 files)
- InfoV3.java: `@JsonProperty` - package stays `com.fasterxml.jackson.annotation` ✓
- DiscoveryControllerV2.kt: `@JsonGetter`, `@JsonInclude` - package stays same ✓
- DiscoveryEndpoint.java: `@JsonGetter` - package stays same ✓

**No import changes needed for annotations!** (jackson-annotations unchanged)

#### Spring Boot Configuration
- Update any `spring.jackson.*` properties to new paths
- Review actuator/health configuration

#### Test Changes
- Add `@AutoConfigureMockMvc` to test classes using MockMvc
- Add `@AutoConfigureTestRestTemplate` or `@AutoConfigureRestTestClient` where needed
- Replace any `@MockBean`/`@SpyBean` with `@MockitoBean`/`@MockitoSpyBean` (not used currently ✓)

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
3. (Optional) Upgrade Spring Cloud to latest 2025.0.x patch
4. Stay on Java 17 for now

### Step 2: Spring Boot 4.0 with Classic Starters (Interim)
1. Change parent to Spring Boot 4.0.x
2. Add `spring-boot-starter-classic` and `spring-boot-starter-test-classic`
3. Remove explicit `spring-cloud.version` property - let SB 4.x BOM manage Spring Cloud 2025.1
4. Update Kotlin to 2.2+ (update kotlin.version property)
5. Add `spring-boot-starter-jackson` (for Jackson 3)
6. Add `spring-boot-properties-migrator` (runtime scope)
7. Fix compilation errors (imports, removed APIs, package changes)
8. Run tests with classic starters

### Step 3: Jackson 3 Migration (can be done with Step 2 or after)
1. Update Jackson dependencies to `tools.jackson` group IDs (except jackson-annotations)
2. Update any Jackson API usage (minimal in this codebase - only annotations used)
3. Test JSON serialization (discovery endpoints)

### Step 4: Spring Boot 4.1 Upgrade (when available)
1. Upgrade to Spring Boot 4.1.x
2. Address any 4.0 deprecations removed in 4.1
3. Run full test suite

### Step 5: Post-Migration Cleanup (can be done incrementally)
1. **Modular Starters Migration**: Replace classic starters with modular starters:
   - `spring-boot-starter-webmvc` (instead of classic)
   - `spring-boot-starter-actuator` 
   - `spring-boot-starter-validation` (if needed)
   - Test starters: `spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test`, `spring-boot-starter-jackson-test`
2. Remove `spring-boot-properties-migrator`
3. Update configuration files with migrated properties (from migrator logs)
4. Upgrade to Java 21 (Dockerfile already uses Java 21)
5. Update Jenkinsfile: Spring Boot CLI to 3.x+, compatible Spring Cloud CLI
6. Remove any unused dependencies
7. Update documentation

---

## Questions for Clarification - RESOLVED

1. **Spring Cloud Version**: Use Spring Cloud 2025.1 (managed by Spring Boot 4.x). Spring Cloud 2025.0 does NOT support Spring Boot 4.x.

2. **Jackson Migration Strategy**: Option A - Direct migration to Jackson 3 (minimal Jackson usage in codebase makes this feasible).

3. **Java Version**: Stay on Java 17 for migration; upgrade to Java 21 as post-migration step.

4. **Classic vs Modular Starters**: Use classic starters (`spring-boot-starter-classic`, `spring-boot-starter-test-classic`) initially for easier migration. Mark modular migration as post-migration activity.

5. **Test Dependencies - MockMvc**: After reviewing test classes, most tests use `@SpringBootTest` with custom configurations and Mockito.mock() for HttpServletRequest. They don't explicitly use MockMvc. However, any test that relies on MockMvc being auto-configured will need `@AutoConfigureMockMvc`. Current tests appear to test controllers directly via autowired beans, not via MockMvc. **Action**: Add `@AutoConfigureMockMvc` to test classes if they start failing due to missing MockMvc.

6. **Integration Test / Dockerfile / Spring Cloud CLI**: 
   - Dockerfile uses `sapmachine:21.0.9-jdk-headless-ubuntu-noble` (Java 21) - already on Java 21 in container!
   - Jenkinsfile uses Spring Boot CLI 2.7.18 and Spring Cloud CLI 3.1.1 for password encryption testing
   - Spring Cloud CLI 3.1.1 is for Spring Boot 2.x/3.x - need to update for SB 4.x compatibility
   - The encryption format (`{cipher}...`) uses Spring Cloud Config's encrypt/decrypt - this should remain compatible as it's a separate library
   - **Action**: Update Jenkinsfile to use Spring Boot CLI 3.x+ and compatible Spring Cloud CLI version for SB 4.x

7. **spring-boot-starter-web → spring-boot-starter-webmvc**: This is purely a dependency rename. No code changes needed - the web MVC APIs (controllers, @RestController, @GetMapping, etc.) remain the same. The "web" starter was renamed to "webmvc" for clarity in the modular structure.

8. **Test Starters Needed**: 
   - `spring-boot-starter-webmvc-test` - provides MockMvc, WebTestClient, TestRestTemplate for web layer tests
   - `spring-boot-starter-security-test` - provides @WithMockUser, @WithAnonymousUser, SecurityMockMvcRequestPostProcessors
   - `spring-boot-starter-jackson-test` - provides Jackson test utilities
   - Since tests use `@SpringBootTest` with custom configs and manual mocking (not MockMvc), `spring-boot-starter-webmvc-test` may not be strictly required but is recommended for any future MockMvc tests.

9. **Spring Boot 3.5 Deprecation Checks**: Yes, run `mvn clean compile -PwithTests` on current 3.5.9 first to identify any deprecation warnings before upgrading.