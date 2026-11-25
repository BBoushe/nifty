# Design: Rewrite Existing Docs in ASD-STE100

## 1. Method

- Use the `asd-ste100` skill installed by `ai-context-docs`. If that change rejected the skill, use rule 6 in `.agents/rules/spec-driven.md` and skip the linter step (REQ-SR-02 then uses the written rule).
- One file group per task and one commit per task, so a reviewer can reject one group and accept the others.
- Keep headings, anchors (other files link to them), tables, code blocks, EARS keywords and `// Decision X.Y` references unchanged. Rewrite the sentences around them.
- Prefer deleting filler over rephrasing it.

## 2. Obligation Check (REQ-SR-01)

Run before and after each file and compare:

```sh
grep -oE "MUST NOT|MUST|SHALL|NEVER|SHOULD|REQ-[A-Z]+-[0-9]+|[0-9]+(\.[0-9]+)*" <file> | sort | uniq -c
```

The two outputs must be identical. Record "obligations equal" per file in the Handoff Log.

## 3. Review

The cold reviewer gets the diff in word-diff form (`git diff --word-diff origin/dev...HEAD`) and checks meaning only. Style findings are Convention Candidates.
