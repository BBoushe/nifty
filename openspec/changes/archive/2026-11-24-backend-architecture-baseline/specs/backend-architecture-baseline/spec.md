## ADDED Requirements

### Requirement: REQ-BA-01 Feature-sliced package layout
**WHERE** backend production code exists, **THE SYSTEM SHALL** place it in `de.fhdortmund.nifty.shared.{config,exception}` or in `de.fhdortmund.nifty.<feature>.{web,service,repository}`, with only `NiftyApplication` in the root package.

#### Scenario: Controller outside web
- **WHEN** a `@RestController` class lives outside a `..web..` package
- **THEN** `ArchitectureTest` fails and names the class.

### Requirement: REQ-BA-02 Feature repositories are private
**THE SYSTEM SHALL** allow access to a feature's `repository` package only from classes of the same feature.

#### Scenario: Cross-feature repository access
- **WHEN** a class in feature `alpha` uses a class in `beta.repository`
- **THEN** `ArchitectureTest` fails and names both classes.

### Requirement: REQ-BA-03 Layer direction
**THE SYSTEM SHALL** keep `web` from accessing `repository`, keep `repository` from accessing `service` or `web`, and keep `shared` from depending on any feature package.

#### Scenario: Controller calls a repository
- **WHEN** a class in `alpha.web` uses a class in `alpha.repository`
- **THEN** `ArchitectureTest` fails.

### Requirement: REQ-BA-04 No entity exposure
**THE SYSTEM SHALL** keep `@Entity` types out of the parameter and return types of `@RestController` methods.

#### Scenario: Controller returns an entity
- **WHEN** a controller method returns a class annotated with `@Entity`
- **THEN** `ArchitectureTest` fails.

### Requirement: REQ-BA-05 Problem details for client errors
**WHEN** a request fails `@Valid` validation or a service throws `DomainException`, **THE SYSTEM SHALL** answer with media type `application/problem+json` and the fields `type`, `title`, `status`, `detail` and `instance`, and **SHALL NOT** include a stack trace or an exception class name.

#### Scenario: Invalid request body
- **WHEN** a client posts a body that violates a `@NotBlank` constraint
- **THEN** the response is 400 `application/problem+json` and names the invalid field.

#### Scenario: Domain error
- **WHEN** a service throws `new DomainException(HttpStatus.CONFLICT, "Config is already approved")`
- **THEN** the response is 409 with `detail` = `Config is already approved`.

### Requirement: REQ-BA-06 Generic server errors
**WHEN** an unexpected exception occurs, **THE SYSTEM SHALL** answer 500 `application/problem+json` with a generic `detail` that does not echo the exception message.

#### Scenario: Unexpected exception
- **WHEN** a controller throws `new IllegalStateException("secret internal state")`
- **THEN** the response body does not contain `secret internal state`, `trace` or `IllegalStateException`.

### Requirement: REQ-BA-07 Quiet test output
**WHEN** a developer or agent runs `./mvnw -B test` in `backend/`, **THE SYSTEM SHALL** print no Spring banner, no artifact transfer progress and no INFO framework logs.

#### Scenario: Successful test run
- **WHEN** `./mvnw -B test` passes
- **THEN** the output contains no `Downloading from` / `Downloaded from` lines and no Spring Boot banner.

### Requirement: REQ-BA-08 Credentials from the environment only
**THE SYSTEM SHALL** read the datasource password in `application.yml` from `SPRING_DATASOURCE_PASSWORD` without a committed default.

#### Scenario: Docker Compose still boots
- **WHEN** `docker compose config` runs with the values from `.env.example`
- **THEN** it exits 0 and the backend service receives `SPRING_DATASOURCE_PASSWORD`.
