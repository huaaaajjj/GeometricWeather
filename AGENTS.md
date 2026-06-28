# AGENTS.md

Canonical **coding guide** for this repository — general principles, workflow, review, and build/test expectations. Project-specific structure, commands, and history live elsewhere:

- **`CLAUDE.md`** — Claude Code instructions: build commands, Android structure, flavors, release workflow.
- **`AI_CONTEXT.md`** — project memory: goals, status, completed work, known issues, TODOs, version matrix.

## Working principles (Ponytail)

Lazy = efficient, not careless. Understand the whole flow first, *then* take the highest rung that holds:

1. **Does it need to exist?** Speculative need → skip it, say so in one line. (YAGNI)
2. **Already in this repo?** Reuse the helper/util/pattern. Re-implementing what's a few files over is the most common slop.
3. **Stdlib / platform / an installed dependency does it?** Use it. Never add a dependency for what a few lines cover.
4. **One line?** One line. Only then the minimum code that works.

- **Bug fix = root cause, not symptom.** Grep every caller of the function you touch; one guard in the shared function beats a guard in each caller — and patching only the path the ticket names leaves siblings broken.
- **Deletion over addition.** Shortest working diff wins — but the smallest change in the wrong place is a second bug. Read fully, then be lazy.
- **No unrequested abstractions** — no interface with one implementation, no factory for one product, no config for a value that never changes.
- **Boring over clever.** Clever is what someone decodes at 3am.
- Mark deliberate shortcuts with a `// ponytail:` comment naming the ceiling and the upgrade path.

## Coding workflow

1. **Read before writing.** Trace every file the change touches and the real flow end to end. Laziness that skips comprehension ships a confident wrong fix.
2. **Climb the ladder**, take the highest rung that works, stop there.
3. **Smallest correct diff.** Change it once, where all callers route through.
4. **New code in Kotlin.** Fixing a `.java` bug? Opportunistically migrate that file — incremental, never bulk rewrites.
5. **Preserve existing style** — match the surrounding code's naming, idiom, and comment density.
6. **Leave one runnable check** for non-trivial logic (a branch, loop, parser): an `assert`-based self-check or one small unit test. No frameworks/fixtures unless asked. Trivial one-liners need none.
7. **Build before claiming done** (see expectations below). Report outcomes faithfully — if a step was skipped or a test failed, say so.

## Code review checklist

Complexity (cut it):

- [ ] Anything that doesn't need to exist — dead code, unused flexibility, speculative features.
- [ ] Hand-rolled work the stdlib or platform already ships.
- [ ] A new dependency for what a few lines cover.
- [ ] Single-implementation interface / one-product factory / one-caller layer.
- [ ] Duplicated logic that should be one shared function.

Correctness (keep it):

- [ ] Inputs validated at trust boundaries; provider/external fields null-guarded.
- [ ] Error handling that prevents data loss is intact.
- [ ] No blocking I/O or DB access on the main/UI thread.
- [ ] Security and accessibility basics preserved.
- [ ] The fix addresses the root cause, and sibling callers are covered.

**Never simplify away:** input validation, error handling that prevents data loss, security, accessibility, or anything explicitly requested.

## Build / test expectations

- **JDK 17** is required to build.
- Run the relevant unit tests and a build for the affected variant before declaring a change done — exact commands are in `CLAUDE.md`.
- **Build and verify the release artifact locally before publishing** — CI is unreliable here.
- Don't hardcode secrets in source; route them through the build-config mechanism (see `CLAUDE.md`).
- Bump version metadata when shipping a release (policy in `CLAUDE.md`).
