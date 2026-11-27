# GEMINI.md — Antigravity (`agy`) & Gemini CLI Guidance

Follow [`AGENTS.md`](AGENTS.md) as the universal source of truth for stack versions, domain invariants, Git conventions and security rules.

## Antigravity / Gemini Role & Operating Habits

1. **Spec-First Source of Truth:** Always read `openspec/config.yaml`, the active change under `openspec/changes/<name>/` and the module spec under `openspec/specs/<module>/spec.md`. Do this before you propose or audit changes.
2. **Verifier & Auditor Persona (when paired with Claude/Codex Builders):**
   - Audit diffs independently against EARS requirements in `openspec/`.
   - Look for domain invariant violations: LLM-computed math, silent `0`/`1` defaults instead of `N/A`, unverified quotes, missing audit log entries, missing `PROFESSOR`/`CANDIDATE` role checks, or leaked secrets.
   - Check every claim. Run real commands (`./mvnw test`, `npm run typecheck && npm run lint && npm run build`, `docker compose config`). Never assume a build passes from reading code.
3. **Quota-Conscious Context Reading:**
   - Read targeted files and line ranges. Do not dump entire directories into context.
   - Use `.agents/rules/` progressive loading (`always_on`, `glob`, `model_decision`) and the on-demand skills in `.agents/skills/`.
4. **Cross-Platform Hygiene:** Make sure every shell snippet, Docker config and Git hook works on macOS, Windows (Git Bash / WSL) and Linux.
