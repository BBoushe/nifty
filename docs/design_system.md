# nifty

Skill assessment for professors, scored with AI help and decided by people.

nifty runs a conversational AI interview for each student. It turns verified quotes from the interview into skill scores on a 0–4 scale and builds balanced teams. Professors use it to understand a class and to form teams. Recruiters are future scope. The design must make AI output fast to scan, easy to verify, and impossible to confuse with a human judgement.

> Decisions 1.14 (PrimeVue, 0–4 + hatched `N/A`), 2.10 (threshold-gated release), 2.14 (timers) and 2.16 (advice) define this file. Where this file and [`docs/decisions/index.md`](decisions/index.md) disagree, the decisions win.

## Principles

1. **Evidence over adjectives.** Every score and every AI claim links back to the interview message it came from.
2. **Always label AI.** Model output sits on `ai-soft` or carries the AI tag and sparkle icon. `ai-blue` means "the model said this" and nothing else.
3. **Humans set the rules.** The professor sets the confidence thresholds and the minimum coverage. Results above them release automatically. Flagged results wait for the professor, and flagged scores never count until the professor resolves them.
4. **Skills, not people.** No photos or demographic data on cards. Summaries describe work, never "potential". BFI-10 trait scores show only to the professor and to the student they belong to, in neutral words.
5. **Bright, not playful.** The yellow and green keep it approachable for students. The layout stays calm and dense enough for a professor who works through 200 profiles.

## Content fundamentals

- **Voice:** clear, neutral, specific. Address the reader as "you". Name the evidence: "Explained 3 of 4 transaction cases", not "Great coder!".
- **Advice on the results page:** encouraging and specific. Name the strongest skill and the next skill to improve, with the evidence. Code selects both skills (Decision 2.16).
- **Case:** sentence case for buttons and headings ("Start assessment"). Uppercase only for `micro` labels ("AI SUMMARY", "IN PROGRESS").
- **Numbers:** always show a score with its scale and its rubric label: "3/4 · <label>". Show `N/A` as "N/A · not enough evidence", never as 0. Show fit and coverage as two separate numbers. Confidence is a percentage.
- **Time:** the countdown shows `mm:ss`. The professor enters minimum and maximum times in minutes and seconds.
- **AI copy:** two sentences at most, in the present tense, one claim per sentence, with no stacked hedging words. The meter shows low confidence, not vaguer wording.
- **Errors:** say how to fix it ("Set the minimum time below the maximum time").

## Visual foundations

**Colour.** `brand-yellow` is the identity: avatars, onboarding and the Invite CTA. It always carries `on-yellow` text. `brand-green` is for action and achievement: primary buttons, selected filters, progress and level 4. Use `ai-blue`, `ai-soft` and `ai-ink` only for LLM output. The score levels step down in strength:

| Level | Fill | Text |
|---|---|---|
| 4 | solid `brand-green` | `on-green` |
| 3 | `green-soft` | `ink` |
| 2 | `brand-yellow-soft` | `warning-ink` |
| 0–1 | `danger-soft` | `danger` |
| `N/A` | diagonal hatch on `surface-sunken` | `ink-muted` |

Each level always carries its number and label, so colour is never the only signal. A flagged cell gets a `danger` border. Opacity shows confidence. Every text pair meets 4.5:1 in light and dark.

**Type.** Plus Jakarta Sans (Google Fonts) at 400–800. Headings are heavy and compact. `metric` (32px, weight 800, tabular figures) is for scores and the countdown. Body text is 14px. Use only the nine styles in Typography.

**Spacing.** The spacing uses a 4px grid. Page margins are `space-6`, card padding is `space-4`, and the gutter between cards is `space-4`. Dashboards are desktop-first (1280px), with cards in a 3–4 column grid. The Assessment View reflows to one column on phones.

**Shape and elevation.** Use `radius-md` on controls. Use `radius-lg` on cards. Use `radius-pill` on chips, meters and avatars. Use `shadow-card` for cards on `surface-sunken`. Use `shadow-float` only for things above the page: menus, side sheets, the compare tray and sticky search.

## Iconography

Use 2px-stroke line icons on a 20px grid with round caps. The four-point sparkle is nifty's AI mark. It appears only beside model output and AI actions. Fill it in `ai-blue` or `ai-ink`. Initials avatars on `brand-yellow` replace photos. There is no logo yet, so set "nifty" in lowercase Plus Jakarta Sans 800.

## Components

Build every component from PrimeVue, themed from these tokens on Tailwind v4 (Decision 1.14). Define the tokens once as Tailwind `@theme` variables and map them into one PrimeVue preset. Do not add a second component kit or a separate CSS bundle.

- **Button:** primary, outline, AI, yellow, ghost, danger. Also sm and block.
- **ScoreBadge:** the five levels and `N/A` as a badge, and skill meters.
- **StudentCard:** a student profile with fit, coverage, top skills and an AI summary.
- **InsightCard:** an AI claim with evidence, confidence and review actions.
- **SkillChip:** filter, removable and skill-with-level chips.
- **Input:** a labelled field, affix group, textarea and error state for the assessment form.
- **Countdown:** `mm:ss` to the server's `expiresAt`, with a questions-left indicator. No pause control.
- **ConversationLog:** the timestamped interview, with `timed out` and `paste suspected` markers.
- **LikertScale:** the 5-point BFI-10 item ("Disagree strongly" to "Agree strongly").
- **SearchBar:** keyword search. The Ask AI mode is Post-MVP.

## Source note

nifty derived its visual language (yellow and green, heavy rounded type) from the Blinkit UI community kit and repurposed it for assessment. All values are nifty's own.
