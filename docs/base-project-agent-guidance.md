# AGENTS.md

## Purpose

This file defines project-agnostic operating rules for AI coding agents. Repository-specific architecture, conventions, and domain rules should live in project-local instructions and override generic preferences where explicitly stated.

The goal is reliable engineering behavior with minimal unnecessary context.

## Operating Model

Use four distinct kinds of guidance:

- **Engineering policy**: how work should be investigated, changed, verified, and reported.
- **Project doctrine**: stable repository-specific architectural choices and conventions.
- **Domain rules**: business invariants, protocols, contracts, and semantics the software must enforce.
- **Evidence**: current source, tests, schemas, configuration, runtime behavior, logs, generated artifacts, and other observable repository facts.

Domain rules define correctness. Project doctrine defines the chosen architecture. Engineering policy defines how changes are made. Evidence determines what the system actually does.

Do not turn project-specific choices into universal engineering rules.

## Authority and Conflicts

1. Inspect relevant repository evidence before making assumptions.
2. Prefer current, observable repository truth over memory, stale prose, or confident guesses.
3. Treat explicit domain rules and authoritative specifications as constraints, not suggestions.
4. Treat project doctrine as stable by default. Do not bypass it for convenience.
5. If implementation conflicts with established rules, fix the implementation when the intended rule is clear.
6. If evidence shows that project doctrine cannot satisfy domain rules or operational reality, stop and report the conflict instead of adding compensating hacks.
7. If a requested change would knowingly violate an authoritative domain rule or explicit project constraint, stop and explain the conflict.
8. When sources disagree and authority is unclear, surface the disagreement rather than silently choosing the easiest interpretation.

## Investigation

- Understand the relevant execution path before changing it.
- Read only the files and documentation needed for the task.
- Trace ownership, callers, boundaries, state transitions, and failure paths where they matter.
- Reproduce or observe unclear behavior when practical.
- Use focused instrumentation or logging when runtime behavior cannot otherwise be established.
- Reduce uncertainty before expanding the scope of a change.

Audits are first-class engineering work. A focused audit may legitimately identify incorrect assumptions, boundary violations, missing invariants, lifecycle problems, scalability risks, or doctrine that no longer matches reality.

Functional success establishes feasibility, not architectural fitness. New architecture should be treated as provisional until it survives meaningful review of boundaries, failure modes, lifecycle behavior, scale assumptions, and runtime evidence.

## Change Scope

Make the smallest **complete and coherent** change that solves the problem.

- Avoid unrelated cleanup and opportunistic refactors.
- Do not optimize for the smallest textual diff when that would leave awkward wrappers, duplicated ownership, partial migrations, or compatibility debris.
- Update directly related callers, tests, schemas, migrations, and documentation when they are part of the same conceptual change.
- Prefer small implementation slices that leave the repository internally coherent.
- Avoid speculative abstractions and infrastructure for hypothetical future needs.
- Do not add compatibility shims, aliases, fallbacks, or migration layers unless compatibility is an actual requirement.
- Treat new frameworks, infrastructure services, and significant dependencies as architectural decisions, not incidental conveniences.
- Prefer mature, well-supported libraries for solved infrastructure problems when they fit the project's constraints; do not reinvent commodity infrastructure without a concrete reason.

## Ownership, Naming, and Structure

Repository structure is persistent architectural context. It should help a human or coding agent infer ownership, responsibility, and likely collaboration before opening a file.

- Put behavior at the narrowest correct ownership boundary.
- Preserve established separation of responsibilities.
- Do not bypass a boundary merely because doing so produces a shorter patch.
- Keep external representation, adaptation, and translation at system boundaries.
- Keep policy and invariants close to their authoritative enforcement point.
- Prefer explicit dependencies and local composition over hidden coupling or "magic".
- Make file and folder names describe the capability, domain, or role they own. Prefer specific responsibility-bearing names over vague names such as `utils`, `helpers`, or `misc` when a clearer owner exists.
- Follow the language and framework's conventional naming forms unless project doctrine specifies a stronger convention. Within those forms, make the primary responsibility discoverable from the path and filename.
- Use folders when they create useful reasoning isolation: grouping multiple files with one owner, separating interleaved responsibilities, isolating a dependency cluster, clarifying future placement, or making a crowded directory easier to navigate.
- Do not create folders or files merely to satisfy aesthetic symmetry or reduce line count. A split should create a boundary that can be named and reasoned about independently.
- Prefer a topology in which files mostly collaborate with siblings and their owning parent/coordinator. Repeated imports into unrelated sibling internals, duplicated ownership, or bidirectional dependency patterns are design evidence worth investigating.
- Use **hierarchical ownership** for cross-sibling behavior. When one sibling needs behavior owned by another, prefer an explicit contract mediated by their nearest common parent/coordinator rather than a direct behavioral dependency on the sibling's internals.
- Direct sibling imports are acceptable for genuinely shared, dependency-neutral artifacts such as stable types, constants, or pure value definitions. They should not become a shortcut for hidden workflow coupling.
- Parent/coordinator layers should own orchestration across child boundaries: sequencing, aggregation, routing, lifecycle handoff, and the contract that connects independently owned capabilities. Child layers should expose focused capabilities without needing knowledge of peer internals.
- Keep coordination boundaries thin. A common parent should understand the workflow contract and required sequencing, but should not absorb the internal domain logic of either participant. Push behavior to the component that owns it; keep the coordinator responsible for composition and handoff.
- Data and capabilities may flow downward from an orchestrator after it gathers or resolves what a workflow requires. When independently owned descendants need to coordinate, propagate intent upward to the nearest common owner and back down through explicit contracts rather than creating hidden peer-to-peer behavioral coupling.
- A coordinator may legitimately act as a workflow touch point without understanding the internal implementation of the participants. Prefer contract routing over centralizing domain knowledge.
- This creates service-like contract discipline inside a monolithic codebase: local calls and simple deployment are preserved while cross-domain workflows remain explicit, reviewable, and easier to extract later if operational boundaries change.
- Dependencies should follow ownership rather than convenience. If a new dependency requires reaching sideways into another owned area, first ask whether the common owner should define the collaboration contract.
- File "spaghetti" is not inherently a problem if ownership remains clear. Conversely, a tidy-looking directory is not good structure if it obscures responsibility or forces readers to reconstruct boundaries from implementation details.

### File Size as a Reasoning Signal

Line count is a review signal, not a hard limit. Large files increase the amount of context an agent must ingest, make localized edits harder to reason about, and raise the risk of accidental rewrites or missed interactions.

Use these rough pressures:

- Around 100 lines: generally comfortable for a focused source file.
- Around 150-220 lines: commonly reasonable when responsibility remains cohesive.
- Around 250 lines: actively look for separable responsibilities, especially before adding more behavior.
- Around 300 lines: treat size as an architectural smell that requires an explicit keep-or-split decision.
- Far beyond 300 lines: retain a single file only when its cohesion, generated/declarative nature, atomic workflow, or other concrete reason makes the larger unit easier to understand than the alternatives.

These thresholds are heuristics, not quotas. Do not create micro-files solely to satisfy them. A 400-line cohesive state machine may be preferable to five arbitrary fragments; a 120-line file with three unrelated responsibilities may already need to be split.

When touching a large file, do not silently accept its size. Ask whether the requested change exposes a stable sub-responsibility that can be extracted without broadening the task unnecessarily.

## Implementation Style

- Prefer simple, explicit, understandable code.
- Generalize only after a real repeated need exists.
- Preserve existing local conventions unless the task intentionally changes them.
- Do not introduce abstractions that obscure lifecycle, ownership, control flow, or failure behavior.
- Keep temporary implementation state local to the layer that creates it.
- Resolve or clean up transient state before handing durable intent to downstream layers.
- Do not leak transport, UI staging, parser, retry, or framework-specific details into domain state unless they are part of the domain contract.

## Lifecycle and Resource Safety

Treat lifecycle behavior and cleanup as correctness.

For stateful or expensive systems, determine:

- what creates identity;
- what causes initialization or reinitialization;
- what state may change without rebuilding the system;
- what resources are acquired;
- how success, failure, cancellation, replacement, and teardown behave.

Avoid coupling lifecycle identity to incidental changing state.

Explicitly consider cleanup for resources such as subscriptions, workers, processes, sockets, transactions, temporary files, caches, renderers, editors, media engines, and external clients.

## Tests and Behavioral Witnesses

Tests and other behavioral witnesses are independent evidence, not implementation accessories.

Protect meaningful:

- runtime behavior;
- invariants;
- external and internal contracts;
- important workflows;
- regressions;
- material failure modes.

Do not add tests merely because implementation structure changed.

When an existing test fails after a change:

1. Determine what behavior, contract, or invariant the test is intended to protect.
2. Determine whether that intended behavior actually changed.
3. Decide whether the implementation is wrong, the contract intentionally changed, or the test is no longer the right witness.
4. Change the test only when the intended expectation has genuinely changed.

Do not weaken, delete, or rewrite a test solely to make the suite green.

Do not add production behavior solely to satisfy a test when that behavior has no product, contract, or domain justification.

The same implementation pass must not casually rewrite its own witnesses. Tests, schemas, fixtures, protocol examples, migrations, production artifacts, and reproducible prior behavior should be treated as evidence to evaluate before being changed.

## Verification

Use an expanding verification funnel.

1. Run the narrowest check that can meaningfully falsify the change.
2. Investigate focused failures before broadening scope.
3. Run appropriate subsystem, type, static, lint, build, or consistency checks for the touched area.
4. Broaden verification when the change's scope, risk, or failures justify it.
5. Run the full suite only when proportionate or explicitly required.

Prefer high-signal verification over test volume or coverage percentage.

Do not claim checks ran when they did not.

## Observability

Use observability to reduce uncertainty, not as noise or as a substitute for reasoning.

- Log meaningful lifecycle events, conflicts, retries, cleanup failures, and unexpected failures with bounded context.
- Never log secrets, credentials, tokens, sensitive payloads, or unnecessary personal data.
- Temporary debug instrumentation should be scoped and searchable.
- Remove temporary instrumentation or gate it behind an explicit debug mechanism before production-quality completion.

## Reporting

Report the result precisely and proportionately.

Include, when relevant:

- what changed;
- why the change belongs where it was made;
- important assumptions or conflicts discovered;
- tests or checks run and their results;
- verification intentionally not run;
- new or changed tests and the behavior they protect;
- pre-existing failures or unresolved uncertainty.

Distinguish clearly between what was verified, inferred, skipped, or could not be tested.

Never overstate confidence.

## Final Principle

Prefer evidence over intuition, explicit ownership over convenience, coherent slices over broad rewrites, meaningful verification over ritual, and durable repository clarity over short-term implementation speed.
