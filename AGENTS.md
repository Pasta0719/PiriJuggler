# PiriJuggler — repository-wide correctness and change-scope rules

These rules apply to **every** task: gameplay, UI, data, simulation, packaging, documentation, debugging, GitHub operations and explanations. They are not UI-specific. They apply to Codex and all other contributors working in this repository.

## 1. The request is the contract
- Separate the user's **requested changes**, **must remain unchanged**, and **acceptance checks** before modifying anything. Do not silently add a feature, change a label/value, alter a filename, or redefine the user's request.
- If the user requests a presentation-only change, preserve the exact displayed data, counts, labels, history ordering, formatting semantics, game rules and API contracts. Change only presentation. The same isolation rule applies to any other narrowly scoped request.
- Preserve all out-of-scope machine families and behavior. Never "clean up" neighboring code without explicit authorization.
- If a requirement is genuinely ambiguous and changes the result, ask before choosing. Never treat assistant suggestions as user-approved requirements.

## 2. Trust the deployed baseline, not an assumed rebuild
- For JAR changes, identify the **actual current deployed Paper/Fabric artifacts** and record filenames and SHA-256 hashes. A GitHub source rebuild may not match them; do not replace a deployed JAR with a missing-feature rebuild.
- Inspect actual bytecode and resource entries where necessary. Preserve every original entry not explicitly approved for modification. Do not silently remove classes, sounds, assets, metadata, commands or features.
- Keep filenames unchanged unless the user explicitly requests a new name. Never claim that a source build and a deployed artifact are identical without a byte-level comparison.
- Before distributing a patched JAR, run `scripts/verify_jar_patch.py` against the **original supplied JAR** with an exact allowlist of permitted changed and added entries. A passing byte-level scope gate is required; it does not establish functional correctness.

## 3. Evidence, not confident-sounding claims
- Never say "tested", "verified", "all passed", "fully compatible", "unchanged" or "completed" unless the relevant operation was actually executed and the evidence exists.
- Report specifically: baseline source and SHA-256, modified/added/removed file list, commands/tests actually run with pass/fail counts, and what was **not** tested (especially Minecraft/Paper/Fabric runtime).
- A code inspection proves only what the inspected code does, not that a user's running server uses that code or that a behavior worked at runtime.
- When uncertain, distinguish observed evidence from hypotheses. Correct earlier unsupported claims explicitly; do not use invented test counts or inferred statistics as measured results.

## 4. Release/response acceptance
Before calling a task complete:
1. Check each original user requirement against the result, including unnamed preservation constraints retained from earlier requests.
2. Inspect the complete diff or JAR entry manifest; fail if any unapproved content changed or an original entry disappeared.
3. Run the smallest relevant focused tests and applicable regressions; report exactly which ran.
4. For gameplay changes, verify state transitions, persistence, client/server protocol and unrelated machine features to the extent affected; do not infer these tests passed from compilation alone.
5. For UI changes, compare the before/after content contract separately from the appearance. In particular, HYPER_DOG data-lamp counts/history/odds/GET-TOTAL must not change in a styling-only patch.
6. Preserve user-specified filenames and give only paths to artifacts that actually exist.
7. If any acceptance check remains unexecuted, state the limitation plainly instead of describing it as passed.

## 5. How to use automated guards
- The existing release gate and its approved policy are independent constraints. Do not change or weaken them to make a build pass.
- The generic JAR patch scope gate is `scripts/verify_jar_patch.py`. Supply **explicit** `--allow-changed` / `--allow-added` paths; deletion is never implicitly allowed. The script fails closed on unexpected changes and writes machine-readable evidence with `--report`.
- These tools prevent *some* unauthorized changes; they cannot prove user authorization, design correctness, runtime behavior, or the truth of prose. Human review against the actual request remains mandatory.
