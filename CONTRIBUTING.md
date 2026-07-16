# Contributing

Thank you for helping improve `log-mask`. Keep changes focused, preserve the
library's business and exception transparency guarantees, and run the relevant
module tests before opening a change.

## Source documentation

Treat documentation as part of the code change, not as release-preparation debt.
Add or update the public contract and any necessary implementation comment in
the same change as the code it describes.

- Give every production top-level Java type English class-level Javadoc that
  explains its real responsibility and boundary. Do not merely restate its name.
- Add `@author SummerWen` followed by `@since 0.1` to current production
  top-level types. Keep an existing `@since` value when the project reaches
  `1.0`; new public or protected APIs use the version in which they first appear.
- Document caller-visible contracts on public APIs, public nested types, and
  protected extension points. Record meaningful null behavior, exceptions,
  state/value pairings, immutability, ordering, and resource lifecycles.
- Do not copy inherited documentation onto routine overrides. Use inherited
  documentation plus an implementation-specific note only when the override
  changes or strengthens a contract.
- Start method descriptions with an imperative verb such as `Return`, `Create`,
  or `Determine`. Wrap Javadoc at about 80 characters and use `{@code}` for code
  values, including `null`.
- Order member tags as `@param`, `@return`, `@throws`, `@since`, `@see`, and
  `@deprecated`. Order type tags as `@author`, `@since`, `@param`, `@see`, and
  `@deprecated`. Include a tag only when its description adds useful contract
  information.
- Use `<p>` to begin additional Javadoc paragraphs. Keep the description and
  member tags together without an empty line.
- Use inline or block comments only for reasons, invariants, safety boundaries,
  framework traps, and compatibility limits that the code cannot express. A
  comment derived from private design material must be self-contained; do not
  leave references that require access to a private ADR.
- Do not add narration to simple accessors, obvious branches, or data holders.
  Remove or rewrite existing comments that only translate the code.

The project deliberately uses the single-token author value `SummerWen`; it does
not adopt Spring Framework's multi-word author-format check. This is an explicit
project convention, not a claim of exact Spring checkstyle compatibility.

## Tests and benchmarks

Use descriptive test names instead of method-level Javadoc. Do not add
`@author` or `@since` to test classes. Add a short comment only when a scenario's
purpose, fixture construction, regression history, or assertion would otherwise
be non-obvious.

Benchmark comments should state what a scenario measures and identify setup
that is intentionally outside the timed path. Keep benchmark transport and
logging fixtures deterministic.

## Completion checks

Run tests for each module you change. Before completing a repository-wide
change, run:

```bash
mvn verify
mvn -Pbenchmarks -pl log-mask-benchmarks -am test-compile
git diff --check
```

Changes to Java source documentation should contain only Javadoc and comments.
Track any design or API issue discovered during a documentation audit as
separate work; do not hide it behind a comment or leave an unowned `TODO`.
