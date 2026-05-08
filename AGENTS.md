# Copilot instructions for `tupson`

## Build, test, and lint commands

This repository uses **Deder** (`deder.pkl`) for contributor workflows.

- Install Deder: `brew install sake92/tap/deder`
- Compile everything: `deder exec -t compile`
- Run the full test matrix (JVM, Scala.js, Scala Native, and `tupson-config`): `deder exec -t test`
- Run one JVM suite: `deder exec -m tupson-jvm-test-3.7.3 -t test ba.sake.tupson.ParseSuite`
- Run one JVM test: `deder exec -m tupson-jvm-test-3.7.3 -t test 'ba.sake.tupson.ParseSuite#parse primitives'`
- Fast JVM-only loop: `deder exec -m tupson-jvm-test-3.7.3 -t testInMemory`
- Check rewrite/lint rules: `deder exec -t fixCheck`
- Apply rewrites: `deder exec -t fix`
- Format sources: `deder exec -t runMvnApp fmt`
- Publish locally for downstream testing: `deder exec -t publishLocal`
- Run examples: `deder exec -m examples -t runMain parse` (swap `parse` for `write`, `roundtrip`, or `backwards`)
- Release: `./scripts/release.sh <version>`
- If you change docs, the site is built with Flatmark: `flatmark build -i docs`

## High-level architecture

- `deder.pkl` is the build/source-of-truth for module structure. The main `tupson` library is cross-built to **JVM**, **Scala.js**, and **Scala Native**, with matching generated test modules per platform. `tupson-config` is a separate JVM-only module that depends on the JVM build of `tupson`.
- `tupson/src/ba/sake/tupson/package.scala` is the public entry point. It exposes the `toJson` / `parseJson` extension methods, exception types, and the `@discriminator` annotation used for sum-type encoding.
- `tupson/src/ba/sake/tupson/JsonRW.scala` is the core of the library. `JsonRW[T]` is a combined writer/parser typeclass, and its companion owns the built-in givens plus macro derivation for product types, sum types, enums, and union types.
- Derived parsing is not just field decoding: it also handles missing-key behavior. The generated parser first looks for the JSON field, then constructor default arguments, then `JsonRW.default`, and otherwise accumulates `ParseError`s into a `ParsingException`.
- `tupson/src/ba/sake/tupson/instances.scala` contains lower-priority/shared instances and helpers that should not outrank the main givens in `JsonRW.scala` during implicit search. That file also owns path-preserving error aggregation for sequences.
- `tupson-config/src/ba/sake/tupson/config/package.scala` adapts Typesafe Config by rendering `Config` to JSON, normalizing numeric-looking strings, then delegating back into Tupson parsing with `parseJson[T]`.
- `examples/` shows the intended public API shape. `docs/` is the published documentation site and the quickstart source of truth for consumer setup.

## Key conventions

- Prefer `derives JsonRW` on case classes and enums. Add a manual `given JsonRW[...]` only when derivation is not enough.
- Keep `-Yretain-trees` enabled anywhere Tupson is compiled. The build config and consumer docs both preserve it because derived parsing relies on retained trees for constructor default arguments.
- Sum types use `@type` as the default discriminator field. Override it with `@discriminator("...")`; tests rely on that behavior.
- Preserve Tupson's path-aware error style: parsing starts at `$`, nested keys append to that path, and multi-field failures are reported as aggregated `ParseError`s inside `ParsingException`.
- Examples and tests use `import ba.sake.tupson.{*, given}` so extension methods and givens are both in scope. Follow that style in new examples/tests unless there is a reason not to.
- `tupson-config` tests depend on build-time JVM/test environment setup from `deder.pkl` (`-Dconfig.override_with_env_vars=true` and `CONFIG_FORCE_envvar_port`). Keep those assumptions in mind when changing config parsing behavior or tests.
