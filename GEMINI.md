# GEMINI.md — Antigravity (`agy`) & Gemini CLI Guidance

Follow [`AGENTS.md`](AGENTS.md) as the universal source of truth for stack versions, domain invariants, Git conventions, and security rules.

## Antigravity / Gemini Role & Operating Habits

1. **Spec-First Source of Truth:** Always read `openspec/config.yaml`, the active change under `openspec/changes/<name>/`, and the relevant module spec under `openspec/specs/<module>/spec.md` before proposing or auditing changes.
2. **Verifier & Auditor Persona (when paired with Claude/Codex Builders):**
   - Independently audit diffs against EARS requirements in `openspec/`.
   - Explicitly hunt for domain invariant violations: LLM-computed math, silent `0`/`1` defaults instead of `N/A`, unverified quotes, missing audit log entries, missing `PROFESSOR`/`CANDIDATE` role checks, or leaked secrets.
   - Verify every claim by executing real commands (`./mvnw test`, `npm run typecheck && npm run lint && npm run build`, `docker compose config`) — never assume green builds from reading code.
3. **Quota-Conscious Context Reading:**
   - Read targeted files and line ranges rather than dumping entire directories into context.
   - Rely on `.agents/rules/` progressive loading (`always_on`, `glob`, `model_decision`) and `.agents/skills/` on-demand skills.
4. **Cross-Platform Hygiene:** Ensure every shell snippet, Docker config, and Git hook works cleanly on macOS, Windows (Git Bash / WSL), and Linux.
