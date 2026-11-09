# Automatic Module Names

Status: **pending**. Do not implement this until `codex/hua-migration-plan` is on `main` and the identity rename is complete. This branch only reserves the work and records how to do it.

The change is a stable `Automatic-Module-Name` manifest entry on each published jar. It does not add `module-info.java`, and it does not change classpath behavior.

## Why this waits

`codex/hua-migration-plan` (`docs/release/hua-migration-plan.md` on that branch) replaces the published identity:

| Concern | After migration |
| --- | --- |
| Maven groupId | `io.github.huahill` |
| Java packages | `io.huahill.*` |
| Artifact and directory prefix | `hua-*` |
| First release | `0.1.0` |

An automatic module name is permanent once a jar is published. Writing `org.jfoundry.*` names now, or writing names derived from `jfoundry-*` filenames, would have to be thrown away in that migration. Implement against the renamed packages and artifact IDs.

Land the manifest in the same `0.1.0` release when that release has not been published yet. The first Central jars then never expose a filename-derived module name. If `0.1.0` is already published, ship the manifest in the next release and record that any filename-derived name observed from `0.1.0` changes.

Do not retrofit `Automatic-Module-Name` onto already published `io.github.xfoundries:jfoundry-*` artifacts.

## Resume checklist

- [ ] Confirm `origin/main` contains `io.huahill.*` packages and `hua-*` artifact IDs.
- [ ] Rebase `codex/automatic-module-names` onto that `origin/main`. Keep this document.
- [ ] Re-read the reactor after the rename. The examples below were measured on `bd092890`, before the rename.
- [ ] Implement the steps in this document. Do not reopen Inbox SQL, MyBatis claim behavior, or JPMS encapsulation.

## Decision

Worth doing:

- One stable `Automatic-Module-Name` per published main jar.
- A build check that rejects a missing, illegal, or duplicate name.

Not in this change:

- `module-info.java`, `exports`, `opens`, `requires`, or qualified exports.
- Hiding `InboxMessageStore` or any other SPI. JPMS cannot stop classpath injection, and the store remains a documented extension point.
- `--add-opens`, Multi-Release jars, or a module path for Spring, Quarkus, or Helidon.
- Package moves, artifact renames, or split-package cleanup. Unique packages were done in #254. The identity rename belongs to the migration plan.

Classpath consumers ignore the manifest entry. There is no public API or configuration change.

## Scope

Apply the entry to every reactor module that produces a published main jar.

Include:

- Framework, infrastructure, Jakarta, Spring, Quarkus, and Helidon runtime jars, including jars with classes and classless Spring Boot starters.
- Quarkus deployment jars. They are published even though they are build-time artifacts.

Exclude:

- `pom` packaging: BOMs, aggregators, and the parent.
- Modules with `maven.deploy.skip` set. On `bd092890` that is the Spring and Quarkus integration-test modules.
- Classifier jars (`sources`, `javadoc`), including the `attach-empty-javadoc` execution already used by starters and Quarkus deployment modules.
- POMs under `src/test`, such as `single-parent-consumer`. Discover modules from the Maven reactor, not from a recursive search for every `pom.xml`.

## Names

Use the JDK's rule, not a hand-rolled pattern. `ModuleDescriptor.newAutomaticModule(String)` accepts a legal name and rejects keywords, empty segments, and leading digits.

### Jars that contain classes

1. Collect exact `src/main/java` packages that contain a type other than `package-info.java`.
2. Prefer the longest common package prefix that is itself one of those packages, after rewriting `org.jfoundry` to `io.huahill`.
3. Keep that name only when no other in-scope jar uses it.
4. Otherwise pick one unique package that jar actually contains, and record the choice in the module POM. Do not invent a package the jar does not contain just to make the name look shorter.

Do not stop at a shared prefix. Measured on `bd092890`:

- No exact package was owned by two deployable jars. Re-check this after the rename; a regression means the jars cannot sit together on the module path, and this manifest work must stop until that split is fixed.
- `jfoundry-web` contains both `org.jfoundry.http` and `org.jfoundry.problem`, so the common prefix `org.jfoundry` is too coarse. The Helidon, Spring, and Quarkus web jars have the same shape. After migration their names must still be unique, for example `io.huahill.http` only if that exact name is not used by another jar.
- `jfoundry-domain-event-core` owns `org.jfoundry.application.event`. `jfoundry-domain-event-outbox-core` owns only child packages (`externalization` and `outbox`). The parent name belongs to the core jar. The outbox jar needs its own name, such as `io.huahill.application.event.outbox`.

A module name does not have to be a prefix of every package in the jar. `io.huahill.http` may name a jar that also contains `io.huahill.problem`.

Representative mappings from `bd092890`, with the migration prefix applied:

| Current artifact | Post-migration module name |
| --- | --- |
| `jfoundry-domain` | `io.huahill.domain` |
| `jfoundry-application-core` | `io.huahill.application` |
| `jfoundry-inbox-core` | `io.huahill.application.inbox` |
| `jfoundry-inbox-jpa` | `io.huahill.infrastructure.inbox.jpa` |
| `jfoundry-outbox-jpa` | `io.huahill.infrastructure.outbox.jpa` |

### Jars with no classes

Starters are published and have no main types. On `bd092890` the classless published jars are the Spring Boot starters (`jfoundry-spring-boot-starter`, `jfoundry-inbox-spring-boot-starter`, `jfoundry-inbox-jpa-spring-boot-starter`, and the other starter modules).

Derive those names from the post-migration artifact ID:

1. Remove the project prefix `hua-`.
2. Replace `-` with `.`.
3. Prepend `io.huahill.`.

`hua-inbox-spring-boot-starter` becomes `io.huahill.inbox.spring.boot.starter`. The result still has to be unique and legal. If it collides with a package-derived name, keep the package-derived name for the code jar and choose a different explicit starter name.

## Build change

The root POM's `maven-jar-plugin` `3.5.1` management currently sets only the version. Add the manifest entry there, fed by one property on each in-scope module:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-jar-plugin</artifactId>
    <version>${maven-jar-plugin.version}</version>
    <configuration>
        <archive>
            <manifestEntries>
                <Automatic-Module-Name>${hua.automatic.module.name}</Automatic-Module-Name>
            </manifestEntries>
        </archive>
    </configuration>
</plugin>
```

Use `hua.automatic.module.name` unless the migrated build has a different internal property prefix; follow that prefix and record it here. Each in-scope module sets the property in its own POM. Do not put a root default value in place of a missing module property, or a forgotten module silently inherits the wrong name.

Child modules that only add the `attach-empty-javadoc` execution inherit plugin-level configuration for the main jar. Where a module replaces the plugin's top-level configuration, append this manifest entry instead of dropping it. Do not require the entry on the javadoc or sources classifier.

## Verification

Add `scripts/verify-automatic-module-names.sh`, plus a small Java check if the shell script would otherwise reimplement JDK name rules.

The check fails when any in-scope module:

- omits `hua.automatic.module.name`;
- has a name `ModuleDescriptor.newAutomaticModule` rejects;
- repeats a name used by another in-scope module;
- contains classes whose selected name is not one of that jar's own packages, except for the explicit classless-starter rule.

After `package`, read `META-INF/MANIFEST.MF` from one main jar and assert `Automatic-Module-Name` is the POM value. Ignore `*-sources.jar` and `*-javadoc.jar`. One renamed module is enough for that manifest probe; the script covers the reactor.

Run `git diff --check` and the new script. A POM change is full validation on the server. This metadata change does not need a local Native Image run.

## Explicit non-goals

- Do not add `module-info.java` to domain, application, infrastructure, or runtime adapters.
- Do not use the module system to hide injectable stores or SPIs.
- Do not change SQL claim strategies, transaction boundaries, or runtime wiring.
- Do not rename packages or artifacts in this change.
- Do not describe historical package names as the shipped module names. Update this document's examples if the migration adjusts them.
