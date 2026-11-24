# Spring Boot 4

Version: 4.0.x (pinned 4.0.10 in backend/pom.xml)
Sources:
- [1] https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide
- [2] https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes
- [3] https://docs.spring.io/spring-boot/4.0/reference/testing/spring-boot-applications.html
- [4] https://docs.spring.io/spring-boot/4.0/reference/testing/testcontainers.html
- [5] https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
- [6] https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/bind/annotation/RestControllerAdvice.html
- [7] https://docs.spring.io/spring-boot/4.0/reference/web/servlet.html
- [8] https://docs.spring.io/spring-boot/4.0/reference/features/json.html
- [9] https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.0
- [10] https://java.testcontainers.org/modules/databases/postgres/
- [11] https://github.com/testcontainers/testcontainers-java/releases/tag/2.0.0
- [12] https://docs.spring.io/spring-framework/reference/testing/annotations/integration-spring/annotation-mockitobean.html
Retrieved: 2026-11-24

## Baseline

- Spring Boot 4.0 needs Spring Framework 7.x and Servlet 6.1. [1]
- Boot 4 splits its code into modules named `spring-boot-<technology>`. [1]
- The root package of a module is `org.springframework.boot.<technology>`. [1]

## Starters

- `spring-boot-starter-web` is deprecated. Use `spring-boot-starter-webmvc`. [1]
- backend/pom.xml still lists `spring-boot-starter-web`. Plan a rename to `spring-boot-starter-webmvc`.
- Use `spring-boot-starter-flyway` for Flyway. Do not use the bare Flyway dependency alone. [1]

## Test Starters

- Test starters follow the pattern `spring-boot-starter-<technology>-test`. [1]
- Examples: `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-security-test`. [1]
- The pom lists `spring-boot-starter-webmvc-test`. It lists no `spring-boot-starter-data-jpa-test`. Add it before you use `@DataJpaTest`.

| Annotation | Module | Import |
|---|---|---|
| `@WebMvcTest` | `spring-boot-webmvc-test` | `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` |
| `@AutoConfigureMockMvc` | package of the webmvc test module | `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` |
| `@DataJpaTest` | `spring-boot-data-jpa-test` | `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest` |
| `TestEntityManager` | JPA test module | `org.springframework.boot.jpa.test.autoconfigure.TestEntityManager` |
| `@AutoConfigureTestDatabase` | JDBC test module | `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase` |

Source for the table: [3]. The page does not name the module for `@AutoConfigureMockMvc`.

- `@SpringBootTest` no longer provides MockMvc, `WebClient`, or `TestRestTemplate`. [1]
- Add `@AutoConfigureMockMvc` to a `@SpringBootTest` class that needs MockMvc. [1][3]
- `@WebMvcTest` auto-configures `MockMvc`. It also auto-configures `MockMvcTester` when AssertJ is present. [3]
- `TestRestTemplate` moved to `org.springframework.boot.resttestclient`. It needs the `spring-boot-resttestclient` module. [1]
- `@DataJpaTest` uses an embedded database by default. Tests roll back by default. [3]
- Use `@AutoConfigureTestDatabase(replace = Replace.NONE)` to test against a real database. [3]

```java
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(UserVehicleController.class)
class MyControllerTests {
    @Autowired private MockMvcTester mvc;
    @MockitoBean private UserVehicleService service;
}
```

## Mockito Beans

- Spring Boot 4 removes `@MockBean` and `@SpyBean`. [1]
- Use `@MockitoBean` and `@MockitoSpyBean`. [1]
- Both live in `org.springframework.test.context.bean.override.mockito`. [3][12]
- Put them on non-static test fields or on test constructor parameters. [12]
- `@MockitoSpyBean` needs exactly one candidate bean. [12]
- Use `MockitoExtension` for `@Mock` and `@Captor`. Boot no longer registers a Mockito listener. [1]

## Problem Details (RFC 9457)

- `ProblemDetail` holds the RFC 9457 fields and extra properties. [5]
- `ErrorResponse` exposes status, headers, and body. All Spring MVC exceptions implement it. [5]
- `ErrorResponseException` is a base class for custom exceptions. [5]
- `ResponseEntityExceptionHandler` is a base class for an `@ControllerAdvice`. It handles all Spring MVC exceptions. [5]
- An `@ExceptionHandler` method can return `ProblemDetail`. The `status` property sets the HTTP status. [5]
- Jackson renders `ProblemDetail` as `application/problem+json`. [5]
- Add non-standard fields to the `properties` map of `ProblemDetail`. Jackson writes them as top-level JSON fields. [5]
- `@RestControllerAdvice` combines `@ControllerAdvice` and `@ResponseBody`. [6]
- Set `spring.mvc.problemdetails.enabled=true` to auto-configure a `ResponseEntityExceptionHandler`. [5][7]
- The Boot-configured handler has order 0. Order your own advice ahead of it to override a built-in exception. [5]

```java
@RestControllerAdvice
class ApiErrors extends ResponseEntityExceptionHandler {
    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
}
```

## Testcontainers and PostgreSQL

- Boot 4 uses Testcontainers 2.x. [2][4]
- Testcontainers 2 prefixes module artifacts with `testcontainers-`. Example: `testcontainers-mysql`. [11]
- Use `org.testcontainers:testcontainers-postgresql`. The PostgreSQL module lists version 2.0.5. [10]
- Container classes live in `org.testcontainers.<module>`. Use `org.testcontainers.postgresql.PostgreSQLContainer`. [4][11]
- Add `spring-boot-testcontainers` as a test dependency for `@ServiceConnection`. [4]
- `@ServiceConnection` is in `org.springframework.boot.testcontainers.service.connection`. [4]
- A `PostgreSQLContainer` creates `JdbcConnectionDetails`. It creates `R2dbcConnectionDetails` too. [4]
- JUnit annotations are `org.testcontainers.junit.jupiter.Testcontainers` and `Container`. The `testcontainers-junit-jupiter` artifact provides them. [4]
- Declare the container in a `static` field. Boot reads the instance from a static field. [4]
- Prefer a container `@Bean` or `@ImportTestcontainers` when the cached context must stay usable. [4]
- Use `@DynamicPropertySource` when you need more control than `@ServiceConnection`. [4]

```java
@Testcontainers
@SpringBootTest
class MyIntegrationTests {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");
}
```

The page [4] shows this pattern with Neo4j. The PostgreSQL form above swaps the container class.

## Jackson 3

- Boot 4 uses Jackson 3 by default. It is part of `spring-boot-starter-json`. [2][8]
- Maven groups and Java packages move from `com.fasterxml.jackson` to `tools.jackson`. [9]
- `jackson-annotations` keeps `com.fasterxml.jackson.annotation`. Do not change `@JsonProperty` imports. [1][9]
- Boot auto-configures a `JsonMapper` bean. [8]
- To replace the mapper, define a `JsonMapper` bean. Do not define an `ObjectMapper` bean. [1]
- Jackson 3 mappers are immutable. Build them with `JsonMapper.builder()`. [9]
- `JsonProcessingException` becomes the unchecked `JacksonException`. [9]
- `FAIL_ON_UNKNOWN_PROPERTIES` now defaults to `false`. [9]
- `WRITE_DATES_AS_TIMESTAMPS` now defaults to `false`. [9]
- `jackson-databind` includes the `java.time` support. Do not add `jackson-datatype-jsr310`. [9]
- Rename `@JsonComponent` to `@JacksonComponent`. Rename `@JsonMixin` to `@JacksonMixin`. [1]
- Rename `Jackson2ObjectMapperBuilderCustomizer` to `JsonMapperBuilderCustomizer`. [1]
- Rename `spring.jackson.read.*` to `spring.jackson.json.read.*`. [1]
- Rename `spring.jackson.write.*` to `spring.jackson.json.write.*`. [1]
- Boot registers all Jackson modules on the classpath. Set `spring.jackson.find-and-add-modules=false` to stop this. [1]
- Set `spring.jackson.use-jackson2-defaults=true` to get Jackson 2 defaults. [1]
- The `spring-boot-jackson2` module is deprecated. It uses `spring.jackson2.*` properties. [1][8]

## Other Breaking Changes

- `@EntityScan` moved to `org.springframework.boot.persistence.autoconfigure.EntityScan`. [1]
- `HttpMessageConverters` is deprecated. Use `ServerHttpMessageConvertersCustomizer`. [1]
- `spring.dao.exceptiontranslation.enabled` is now `spring.persistence.exceptiontranslation.enabled`. [1]
- Boot 4 adds JSpecify nullability annotations. Use `org.jspecify.annotations.Nullable`. [1]
- Actuator endpoint parameters no longer accept `org.springframework.lang.Nullable`. [1]
