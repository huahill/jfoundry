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
4. For a shared contract or runtime lifecycle behavior, inventory every supported runtime with an equivalent capability before editing. Today that normally means Spring, Quarkus, and Helidon; do not infer that an optional capability exists in all three.
5. Make the smallest change that preserves framework-neutral core contracts and explicit runtime integration.
6. Add or update focused tests next to each affected runtime module. A successful test in one runtime does not validate the other runtime adapters.
7. Run the narrowest Maven verification first, then broader verification when public APIs, starters, auto-configuration, or cross-module behavior changed.
8. Call out compatibility impact when changing public APIs, starter dependencies, configuration properties, table schemas, event routing, or state transitions.
9. For runtime, starter, dependency-scope, Native Image, or infrastructure-adapter changes, select and run the matching local CI stage from `references/testing.md`; `mvn test` alone does not validate AOT or Native Image classpaths.
10. Submit framework changes through a short-lived branch and pull request. The server-side `Merge gate` is authoritative; integrate only after it succeeds, using `Rebase and merge` to preserve linear history.

## Cross-Runtime Consistency

Before changing a framework-neutral contract or a behavior implemented by runtime adapters, make an explicit coverage table for the affected capability:

| Check | Required action |
| --- | --- |
| Runtime inventory | Identify the Spring, Quarkus, and Helidon modules that implement the behavior, including corresponding deployment or auto-configuration modules. |
| Semantic contract | State the externally observable behavior that must be equal across supported runtimes, such as transaction phase, event ordering, error mapping, context propagation, or retry semantics. |
| Deliberate exception | When a runtime intentionally does not offer the capability, record the limitation and its reason in the relevant documentation or issue; do not silently omit it or add a pretend implementation. |
| Verification | Run focused tests for every affected runtime adapter. Add parity tests for commit/rollback, ordering, failure, or lifecycle behavior when those semantics are shared. |

Apply runtime-specific APIs only inside their runtime adapters. Put a shared semantic marker or contract in the framework-neutral core only when it expresses behavior needed by more than one runtime. Do not move lifecycle APIs such as Spring transaction synchronization or JTA callbacks into core merely to make the implementations look uniform.

## Java Platform Baseline

Treat the root POM's `maven.compiler.release` as the source of truth for Java language and JDK API choices. Before adding or modifying Java code:

1. Read the configured release and use APIs that are stable in that release; do not introduce preview features into framework code without an explicit project decision.
2. Prefer a stable, semantically better API available in the target release over a legacy alternative retained only by habit. For example, use `ScopedValue` for dynamically scoped contextual state when lexical binding is the intended model.
3. Do not mechanically replace every older API. `ThreadLocal` remains appropriate when mutable, thread-owned state is genuinely required; record the reason when retaining it is non-obvious.
4. Verify the affected module with the configured Java baseline, including a focused regression test when the choice prevents a return to an unsuitable API.

## Non-Negotiable Boundaries

For every module-placement decision, apply Onion Simple as defined in `references/module-boundaries.md`.

- Keep `jfoundry-domain`, `jfoundry-architecture`, and `jfoundry-application` modules independent of Spring, Spring Boot, web frameworks, broker clients, persistence framework details, CDI, and Jakarta runtime APIs.
- Keep Spring Framework runtime adapters under `jfoundry-runtime/jfoundry-spring/runtime`.
- Keep Spring Boot auto-configuration only in capability-specific modules under `jfoundry-runtime/jfoundry-spring/autoconfigure`.
- Keep Spring Boot starters as dependency entry points. Do not put Java runtime logic in starter modules.
- Keep framework-neutral technical adapters under `jfoundry-core/jfoundry-infrastructure`.
- Keep reusable architecture tests under `jfoundry-core/jfoundry-architecture/jfoundry-architecture-test`.
- Keep runtime-specific integration verification in the direct `jfoundry-runtime/<runtime>/jfoundry-<runtime>-integration-tests` module; keep framework-neutral tests beside their core or infrastructure implementation.
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
