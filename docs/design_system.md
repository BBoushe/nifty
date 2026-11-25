# nifty

Skill assessment for professors and recruiters, scored by AI and decided by people.

nifty uses an LLM to evaluate student work (code, case studies, written answers) against a rubric. It then turns the results into skill scores, evidence-backed insights and comparable profiles. Professors use it to understand a class. Recruiters use it to find candidates on demonstrated skill rather than CVs. The design has to make AI output fast to scan, easy to verify and impossible to confuse with a human judgement.

## Principles

1. **Evidence over adjectives.** Every score and every AI claim links back to the submission it came from.
2. **Always label AI.** Model output sits on `ai-soft` or carries the AI tag and sparkle icon. `ai-blue` means "the model said this" and nothing else.
3. **Humans decide.** Reviewers can accept, edit or flag any insight. Flagged insights do not count towards scores. The system cannot publish results while flags are open.
4. **Skills, not people.** No photos or demographic data on cards. Summaries describe work, never personality or "potential".
5. **Bright, not playful.** The yellow and green keep it approachable for students. The layout stays calm and dense enough for a reviewer who works through 200 profiles.

## Content fundamentals

- **Voice:** clear, neutral, specific. Address the reviewer as "you". Name the evidence: "Solved 9 of 10 tasks", not "Great coder!".
- **Case:** sentence case for buttons and headings ("Start assessment"). Uppercase only for `micro` labels ("AI SUMMARY", "IN PROGRESS").
- **Numbers:** always show a score with its scale and level: "86/100 · Proficient". Confidence is a percentage. Time limits are in minutes.
- **AI copy:** two sentences at most, in the present tense, one claim per sentence, with no stacked hedging words. The meter shows low confidence, not vaguer wording.
- **Errors:** say how to fix it ("Lower one so they total 100%").

## Visual foundations

**Colour.** `brand-yellow` is the identity: avatars, onboarding and the Invite CTA. It always carries `on-yellow` text. `brand-green` is for action and achievement: primary buttons, selected filters, progress and the Expert level. Use `ai-blue`, `ai-soft` and `ai-ink` only for LLM output. The score levels step down in strength: Expert (solid `brand-green`), Proficient (`green-soft`), Developing (`brand-yellow-soft` with `warning-ink`), Needs work (`danger-soft` with `danger`). Each level always carries its word, so colour is never the only signal. Every text pair meets 4.5:1 in light and dark.

**Type.** Plus Jakarta Sans (Google Fonts) at 400–800. Headings are heavy and compact. `metric` (32px, weight 800, tabular figures) is for scores. Body text is 14px. Use only the nine styles in Typography.

**Spacing.** The spacing uses a 4px grid. Page margins are `space-6`, card padding is `space-4`, and the gutter between cards is `space-4`. Dashboards are desktop-first (1280px), with cards in a 3–4 column grid that reflows to one column on phones for students.

**Shape and elevation.** Use `radius-md` on controls. Use `radius-lg` on cards. Use `radius-pill` on chips, meters and avatars. Use `shadow-card` for cards on `surface-sunken`. Use `shadow-float` only for things above the page: menus, side sheets, the compare tray and sticky search.

## Iconography

Use 2px-stroke line icons on a 20px grid with round caps. The four-point sparkle is nifty's AI mark. It appears only beside model output and AI actions. Fill it in `ai-blue` or `ai-ink`. Initials avatars on `brand-yellow` replace photos. There is no logo yet, so set "nifty" in lowercase Plus Jakarta Sans 800.

## Components

- **Button:** primary, outline, AI, yellow, ghost, danger. Also sm and block.
- **ScoreBadge:** the four skill levels as a badge, and skill meters.
- **StudentCard:** a student profile with score ring, top skills and AI summary.
- **InsightCard:** an AI claim with evidence, confidence and review actions.
- **SkillChip:** filter, removable and skill-with-level chips.
- **Input:** a labelled field, affix group, textarea and error state for assessment setup.
- **SearchBar:** keyword search with an Ask AI mode.

Components are plain HTML and CSS. Include `tokens.css` and `components/bundle.css`. Wrap screens in `.nf`. Use the `nf-` classes that each preview shows.

## Source note

nifty derived its visual language (yellow and green, heavy rounded type) from the Blinkit UI community kit and repurposed it for assessment. All values are nifty's own.
