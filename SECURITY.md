# Security Policy

## Supported Versions

Security fixes are provided for the latest published JFoundry release in the current `1.x`
line. Pre-release versions, including release candidates, are not production support lines.

## Reporting A Vulnerability

Do not open a public issue for a suspected vulnerability. Report it through a
[GitHub private security advisory](https://github.com/xfoundries/jfoundry/security/advisories/new).
Include a minimal reproduction, affected artifacts and versions, impact, and any known mitigation.

The maintainers target acknowledgement within three business days and an initial triage decision
within ten business days. Remediation timing depends on severity, exploitability, affected users,
and the availability of a safe fix. Reports receive status updates at least every ten business days
until disclosure is agreed.

## Disclosure And Remediation

Maintainers validate the report, assess scope and severity, prepare a fix and tests, publish a
patched release, and coordinate disclosure with the reporter. A GitHub Security Advisory is used
when it is appropriate to publish a CVE or a public advisory.

JFoundry does not operate application identity providers, authorization policies, deployment
infrastructure, or customer data. Vulnerabilities in an application's security integration,
configuration, secrets, or operational environment must be handled by that application owner.
