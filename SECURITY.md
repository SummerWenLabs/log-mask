# Security Policy

## Supported versions

Security fixes are provided for the latest released version of `log-mask`.
Pre-release snapshots are development builds and do not receive separate
security support.

## Reporting a vulnerability

Do not disclose a suspected vulnerability in a public issue, discussion, or
pull request. Use GitHub's private vulnerability reporting for this repository
instead. Include the affected version, impact, reproduction steps, and any
suggested mitigation. Remove production credentials, personal data, and other
sensitive values from the report.

If private vulnerability reporting is unavailable, contact a repository owner
through their GitHub profile and ask for a private reporting channel without
including vulnerability details in the initial public message.

The maintainers will coordinate validation, remediation, and disclosure with
the reporter. Response and release timing depends on severity, reproducibility,
and the availability of a safe fix.

## Security model

`log-mask` applies only explicitly configured governance rules. It does not
maintain a default list of sensitive fields, query parameters, or headers.
Values without a matching rule, including credentials and tokens, are logged
unchanged. Review the governance configuration and emitted events before using
the library with production traffic.

Logging and governance operate beside the business path: they do not modify
HTTP messages, application objects, transport settings, MDC values, or business
exceptions. The library also does not manage connection pools, timeouts, TLS,
proxies, or request factories.
