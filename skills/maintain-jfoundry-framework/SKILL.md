---
name: maintain-jfoundry-framework
description: Use when modifying the jfoundry framework repository itself, including module boundaries, public APIs, jMolecules architecture annotations, ArchUnit rules, Maven BOMs, starters, Spring Boot auto-configuration, runtime or infrastructure adapters, Outbox/Inbox internals, release compatibility, or framework documentation.
---

# Maintain JFoundry Framework

## Purpose

Use this skill when changing this repository as a framework. It protects jfoundry's module boundaries, dependency direction, starter semantics, public API compatibility, and verification discipline.

Do not apply this skill to downstream business projects that merely consume jfoundry. Consumer-facing guidance is outside this repository's framework-maintenance scope.

When framework docs, examples, or test fixtures mention DDD modeling concepts, keep the wording source-aware: distinguish DDD concepts from JFoundry conventions, and avoid presenting project recommendations as universal DDD rules.

## Maintenance Workflow

1. Classify the task: domain API, architecture annotation/rule, application SPI, transaction contract, infrastructure adapter, Spring runtime adapter, Web MVC adapter, Boot auto-configuration, starter, BOM, SQL template, verification, docs, release compatibility, or integration test.
2. Read the matching reference:
   - `references/module-boundaries.md` for dependency direction and module roles.
   - `references/feature-placement.md` before adding or moving code.
   - `references/starters-and-boms.md` before changing dependency management or starter modules.
   - `references/testing.md` before choosing verification commands.
   - `references/common-change-recipes.md` for recurring framework changes.
3. Inspect existing modules and tests that already implement the same pattern.
4. Make the smallest change that preserves framework-neutral core contracts and explicit runtime integration.
5. Add or update focused tests next to the changed module.
6. Run the narrowest Maven verification first, then broader verification when public APIs, starters, auto-configuration, or cross-module behavior changed.
7. Call out compatibility impact when changing public APIs, starter dependencies, configuration properties, table schemas, event routing, or state transitions.

## Non-Negotiable Boundaries

For every module-placement decision, apply Onion Simple as defined in `references/module-boundaries.md`.

- Keep `jfoundry-domain`, `jfoundry-architecture`, and `jfoundry-application` modules independent of Spring, Spring Boot, web frameworks, broker clients, persistence framework details, CDI, and Jakarta runtime APIs.
- Keep Spring Framework runtime adapters under `jfoundry-runtime-integrations/jfoundry-spring/runtime`.
- Keep Spring Boot auto-configuration only under `jfoundry-runtime-integrations/jfoundry-spring/autoconfigure/jfoundry-spring-boot-autoconfigure`.
- Keep Spring Boot starters as dependency entry points. Do not put Java runtime logic in starter modules.
- Keep framework-neutral technical adapters under `jfoundry-core/jfoundry-infrastructure`.
- Keep reusable architecture tests under `jfoundry-core/jfoundry-architecture/jfoundry-architecture-test`.
- Keep runtime-specific middleware integration verification under `jfoundry-runtime-integrations/<runtime>/integration-tests`; keep framework-neutral tests beside their core or infrastructure implementation.
- Do not make default starters heavy. Outbox, Inbox, broker adapters, JobRunr, and MyBatis-Plus store adapters must remain explicit capability choices.

## Source Documents

Prefer current repository documents and code over memory:

- `../../docs/i18n/en/framework/framework-boundaries.md`
- `../../docs/i18n/en/framework/architecture-styles.md`
- `../../docs/i18n/en/framework/archunit-rules.md`
- `../../docs/i18n/en/capabilities/reliable-messaging.md`
- `../../docs/i18n/en/modeling/repository-vs-read-contracts.md`
- `docs/release/compatibility.md`
- `AGENTS.md` for repository-wide language, SQL template, and project skill policy
- top-level `pom.xml` and module POMs
- nearby tests in the module being changed

## Output Discipline

When reporting a framework change, include:

- modules touched
- boundary decision made
- tests or verification command run
- compatibility or migration impact
