# Spring Boot Parent 1.0.0 Remediation Design

## Goal

Publish the previously absent `io.github.xfoundries:jfoundry-spring-boot-parent:1.0.0`
without modifying, redeploying, or relabeling any existing `1.0.0` artifact.

## Scope

Add a one-time, manually dispatched GitHub Actions workflow. It is intentionally separate from the
normal release workflow, which treats the existing `jfoundry-parent:1.0.0` publication as proof that
the full release is already complete.

The workflow runs only from `main` and requires the exact confirmation value
`PUBLISH_JFOUNDRY_SPRING_BOOT_PARENT_1_0_0`. It checks that the new Parent POM is absent from Maven
Central before publishing, then builds, signs, and deploys only
`jfoundry-boms/jfoundry-spring-boot-parent` with Maven 3.9.16. It polls Central for the new POM and
uploads deployment evidence and the signed POM. It does not create or change a Git tag or GitHub
Release.

## Safety And Compatibility

The workflow fails when Central returns `200` for the Parent POM, making a rerun non-destructive. It
also requires the already released core and Spring BOM POMs at `1.0.0` to be present before building
the Parent. Existing consumers remain on their unchanged `1.0.0` coordinates; consumers that want
the new parent can use only `jfoundry-spring-boot-parent:1.0.0` after this remediation is published.

This is a release-process exception. Remove the workflow and its matching verifier after Central
publication has been confirmed and its Actions artifacts retained. Future changes release from a new
version line.

## Verification

The repository includes a focused shell verifier that checks the workflow's manual dispatch,
main-branch guard, confirmation gate, coordinate-specific Central checks, module-scoped Maven
commands, and evidence upload. The existing release and supply-chain workflow verifiers continue to
cover the standard release path.
