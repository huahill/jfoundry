# Repository Guidelines

## Project Structure & Module Organization

This is a Java 25 multi-module Maven project for a jMolecules-based, runtime-neutral DDD framework. Top-level groups are declared in `pom.xml`: `jfoundry-core` contains domain, architecture, application, and infrastructure modules; `jfoundry-runtime` contains Spring, Quarkus, and Helidon; `jfoundry-dependencies` provides release dependency management. Production code uses standard Maven paths such as `src/main/java`; tests live under `src/test/java`; module resources live under `src/main/resources` or `src/test/resources`. SQL files shipped by jfoundry are copyable templates, not auto-run migrations. Documentation is organized by language under `docs/i18n/en/` and `docs/i18n/zh/`, with the default English overview in `README.md` and the Chinese overview in `README_ZH.md`.

## Build, Test, and Development Commands

- `mvn validate` checks the Maven reactor and module structure.
- `mvn test` compiles and runs all unit, integration, and ArchUnit tests.
- `mvn clean install` performs a full local build and installs artifacts into the local Maven repository.
- `mvn -pl jfoundry-domain test` runs tests for one module; add `-am` when dependencies must also be built.
- `scripts/verify-ci-matrix.sh` runs the local Java 25 release-baseline test using `JAVA_25_HOME`.
- `mvn clean install -DskipTests` builds artifacts without executing tests; use only for local iteration.

## Coding Style & Naming Conventions

Use Java 25 features where they simplify the model, especially records for immutable value objects. Follow the existing package root `org.jfoundry.*` and standard Maven layout. Keep domain modules free of Spring and persistence dependencies; place Spring auto-configuration in capability-specific modules under `jfoundry-runtime/jfoundry-spring/autoconfigure`, Spring runtime adapters under `jfoundry-runtime/jfoundry-spring/runtime`, persistence and broker adapters under `jfoundry-core/jfoundry-infrastructure`, reusable architecture test rules under `jfoundry-core/jfoundry-architecture/jfoundry-architecture-test`, and runtime-specific integration verification in the direct `jfoundry-<runtime>-integration-tests` module. Name tests with a `*Test` suffix. No formatter plugin is configured, so match the surrounding Java style: four-space indentation, clear method names, and concise English Javadocs/comments only where API intent or non-obvious behavior needs explanation.

## Architecture Boundaries

JFoundry framework internals use Onion Simple: `domain`, `application`, and `infrastructure` are the dependency
rings. Runtime integrations are outer adapters; Hexagonal is an optional architecture language for downstream
projects, not the framework's internal module-placement model.

The core framework must remain independent of runtime frameworks such as Spring, Spring Boot, Quarkus, Helidon, Micronaut, CDI, and Jakarta EE runtime APIs. Keep these boundaries explicit:

- `jfoundry-domain` contains domain modeling primitives and must not depend on application, infrastructure, persistence, messaging, or runtime integration modules.
- `jfoundry-application` contains application-layer contracts, transaction abstractions, domain event orchestration, event externalization rules, Outbox/Inbox SPI, messaging SPI, and serialization SPI. It must not depend on Spring, MyBatis-Plus, broker clients, or concrete databases.
- `jfoundry-infrastructure` contains concrete adapters for persistence, messaging, serialization, and job execution. Infrastructure adapters may depend on native clients such as MyBatis-Plus, Kafka clients, RabbitMQ Java client, RocketMQ client, Jackson, or JobRunr, but they must not depend on Spring Framework or Spring Boot unless they are deliberately placed under `jfoundry-spring`.
- `jfoundry-spring` is the Spring runtime integration layer. Put Spring Framework adapters under `jfoundry-runtime/jfoundry-spring/runtime`, Spring Boot auto-configuration in capability-specific modules under `jfoundry-runtime/jfoundry-spring/autoconfigure`, Spring Boot starters under `jfoundry-runtime/jfoundry-spring/starters`, and Spring runtime integration tests under `jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests`. Spring-side wrappers may adapt Spring clients such as `KafkaTemplate` or `RabbitTemplate` to core SPI interfaces, but core and infrastructure modules must not require those Spring clients.
- `jfoundry-runtime/jfoundry-spring/starters` should assemble existing capabilities; avoid placing domain logic, persistence logic, or broker-specific behavior directly in starters.
- Runtime-specific smoke, middleware, Testcontainers, and Native Image integration tests belong in the direct `jfoundry-<runtime>-integration-tests` module; framework-neutral tests stay next to their core or infrastructure implementation.

## Language Policy

As an open-source framework, source-level artifacts must be friendly to the wider Java ecosystem:

- Source comments must be written in English. This includes Java Javadocs, `package-info.java`, inline comments, test documentation comments, configuration property comments, architecture rule explanations, SQL comments, XML/POM comments, YAML/properties comments, and other resource comments shipped in jars.
- Use `jMolecules` as the prose spelling for the upstream project. Keep lowercase forms only when they are exact technical identifiers, such as `org.jmolecules`, `jmolecules-*` artifact IDs, property names, package names, class names, method names, URLs, or string literals.
- Public documentation may be localized, but languages must not be mixed in the same document. `README.md` is the default English overview; `README_ZH.md` is the Chinese overview. Detailed documentation should use matching language-specific paths under `docs/i18n/en/` and `docs/i18n/zh/`, keeping the same conceptual structure when practical.
- Chinese documentation uses Chinese explanatory prose. Retain English only for exact technical identifiers,
  artifact coordinates, class and annotation names, configuration keys, code, and established architecture
  role names when translating them would reduce precision. Prefer terms such as "运行时", "适配器", "启动器",
  "存储", "派发器", "配置档", "使用方", "原生镜像", and "CI 验证任务" in prose.
- Commit messages, release notes intended for repository history, Maven metadata, generated documentation text, and PR descriptions should be written in English.
- When editing existing Chinese comments in source files, translate them to English instead of adding new Chinese comments nearby. Do not translate user-facing Chinese documentation unless the file is meant to be English.

## Testing Guidelines

Tests use JUnit Jupiter, Spring Boot test support where needed, and ArchUnit for architecture rules. Add focused tests near the module being changed, especially for outbox state transitions, auto-configuration conditions, persistence behavior, and architecture constraints.

Mockito's Java agent is opt-in per module. The root POM keeps the common Surefire/Failsafe `argLine` template with an empty `mockito.javaagent.argLine`; only modules whose tests directly use Mockito or whose test framework loads Mockito should override it with `-javaagent:${org.mockito:mockito-core:jar}` and have a test dependency that resolves `mockito-core`.

For changes involving build logic, dependency management, test infrastructure, CI workflows, Maven plugin configuration, Java baseline compatibility, or runtime compatibility, run `scripts/verify-ci-matrix.sh` before committing or pushing when Java 25 is available. If Java 25 is unavailable, do not claim release-baseline verification.

Runtime integration verification must remain aligned: each supported runtime needs a runtime-local JVM
middleware check that exercises real wiring and a Native Image consumer startup check. Document an
upstream Native limitation explicitly instead of treating it as supported; Helidon Native JTA and
JPA are currently accepted exceptions, tracked with reproducible evidence in
[Helidon issue #8863](https://github.com/helidon-io/helidon/issues/8863#issuecomment-5078931015).

## Documentation Sync

When changing framework behavior, public APIs, module boundaries, starter dependencies, auto-configuration, configuration properties, SQL templates, architecture rules, compatibility baselines, or user-facing workflows, check whether README, `docs/i18n/en/`, `docs/i18n/zh/`, and `skills/maintain-jfoundry-framework` need matching updates. Documentation updates should describe the current behavior, not historical implementation details. If an English user-facing doc is updated and a corresponding Chinese doc exists, update both or state why only one language is affected.

## SQL Templates

jfoundry ships SQL only as copyable templates. Do not place framework SQL templates under Flyway's default `db/migration` path, and do not make framework jars auto-create business tables. Business applications should copy templates into their own Flyway/Liquibase migrations or execute DDL manually through their operational process.

- Outbox templates live under `jfoundry/sql/outbox/{database}/create_outbox_event.sql`. Official templates are maintained only for databases this project chooses to support directly, currently MySQL and PostgreSQL.
- Inbox currently uses a portable template under `jfoundry/sql/inbox/common/create_inbox_message.sql`. Add database-specific Inbox templates only when a supported database requires dialect-specific DDL.
- Proprietary or domestic database templates are not maintained as official built-in templates in this repository. They should be supplied by vendors, third-party integration packages, or downstream applications.

## Commit & Pull Request Guidelines

Recent history follows Conventional Commits, for example `fix(outbox): ...`, `test(archunit): ...`, `refactor(ddd-framework): ...`, and `docs: ...`. Keep commits scoped and use the module or concern as the scope when helpful. Follow the Language Policy for commit and PR text: keep the Conventional Commits type and optional scope, and write the subject and body in English, for example `refactor(application): split application core module` or `fix(outbox): update retry state consistently`. Do not add `Co-Authored-By` trailers for AI coding tools or agents. Pull requests should describe the behavior change, list validation commands run, link related issues, and call out migration, configuration, or compatibility impact.

Keep `main` history linear. Do not use `git merge` to integrate completed work into `main`; rebase a feature branch onto the current `main` or cherry-pick its ordered commits instead. Do not rewrite already-pushed history unless the user explicitly authorizes it; when authorized, use `git push --force-with-lease`, not `--force`.

### Branch Workflow

Before editing or committing, confirm that the checkout is not `main`. Fetch the current remote refs and create a short-lived branch from `origin/main`, using the `codex/<scope>` naming convention unless a different branch name is explicitly requested. Keep all repository changes on that branch; never commit directly on `main`. On the first push, set the upstream explicitly with `git push -u origin <branch>` so later pushes target the feature branch.

## Merge Gate

Do not push directly to `main`. Create a short-lived branch and pull request for every repository change.
The GitHub `Merge gate` status check must pass before integration. Use GitHub's `Rebase and merge` strategy
to preserve the linear `main` history. Local verification accelerates feedback but does not replace the
server-side merge gate.

Documentation-only changes are explicitly limited to `README.md`, `README_ZH.md`, `AGENTS.md`, and
`docs/**`. These paths still run documentation and dependency checks, but skip the full Java, runtime,
Native Image, Maven compatibility, and CodeQL matrix. Changes to workflows, scripts, POMs, source code,
or maintenance skills remain full-validation changes.

## Documentation Comments

Javadocs and documentation comments in source code must follow the Language Policy. There is no Javadoc i18n mechanism for comment bodies; generated documentation uses the text from source comments. Keep comments concise and focused on API intent; avoid restating obvious implementation details.

Use Java 23 Markdown documentation comments (`///`) for all new or modified Java API documentation. Do not introduce traditional `/** ... */` block comments. Spring Boot configuration metadata must not rely on documentation comments for property descriptions. For `@ConfigurationProperties` IDE descriptions, add or update `META-INF/additional-spring-configuration-metadata.json` instead.

## Project Skills

- This repository owns a local framework-maintenance skill at `skills/maintain-jfoundry-framework`. When modifying jfoundry framework internals, use `$maintain-jfoundry-framework` if the agent runtime exposes it. If it is not auto-loaded, read `skills/maintain-jfoundry-framework/SKILL.md` and the relevant files under `skills/maintain-jfoundry-framework/references/` directly before editing.
- Use `maintain-jfoundry-framework` for changes to module boundaries, public APIs, jMolecules architecture annotations, ArchUnit rules, Maven BOMs, starters, Spring Boot auto-configuration, runtime adapters, persistence adapters, messaging adapters, Outbox/Inbox internals, release compatibility, and framework documentation.
- Treat this file, the local maintenance skill, and repository documentation as the project contract. For framework-internal changes, cross-check the relevant local docs before editing: `docs/i18n/en/framework/framework-boundaries.md` for module placement, `docs/i18n/en/framework/architecture-styles.md` and `docs/i18n/en/framework/archunit-rules.md` for architecture semantics, `docs/i18n/en/capabilities/reliable-messaging.md` for Outbox behavior, and `docs/release/compatibility.md` for platform baselines.
- Keep framework maintenance guidance separate from downstream business-project guidance. Do not apply `maintain-jfoundry-framework` rules to downstream business projects that merely consume jfoundry, and do not use downstream business-project guidance as authority for changing jfoundry internals.
- Do not add instructions that depend on unavailable private repositories or local-only skill names outside this repository. If a useful external tool or plugin is unavailable, continue from this repository's docs and state the assumption explicitly.
