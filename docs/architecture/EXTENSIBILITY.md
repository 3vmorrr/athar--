# Adding a feature
1. Add a feature specification with source/license, privacy, input/state and acceptance decisions.
2. Implement pure domain rules and tests before HTTP/UI coupling.
3. Add additive migrations with indexes and forward-recovery notes; test on an isolated schema.
4. Add authenticated/authorized versioned routes, bounded DTOs and API contract examples.
5. Add repository/state/UI packages with string resources, offline rules and accessible states.
6. Add independent admin controls only where authorized; never make private data visible by default.
7. Add domain/integration/navigation tests, update migration matrix/tracker and build/deployment documentation.

Use a simple module registry/navigation model; no dynamic plugin runtime. Avoid generic SQL dashboards or arbitrary remote proxy endpoints. Release a changed API contract within v1 only when backward compatible; otherwise create v2.
