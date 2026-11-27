# nifty – Feature list from Nov 23 meeting

Nov 24, 2026 · @Rashi Jain

This is the complete feature list from the brainstorm, based on the Nov 23 team meeting, the discovery canvas and the decision registry. Items marked **(my call)** are proposals that still need team confirmation. **(open)** means still undecided.

## New features

### 1. Attempt limit

- The professor sets how many times a student may take the assessment.
- **(open)** Which attempt counts: best, latest, or chosen by the professor per assessment. *Suggestion: latest, so a retake is a real second try rather than a lottery.*

### 2. Confidence threshold per question

- For each question, the professor sets the minimum confidence the AI's score must reach to be trusted.
- Confidence is calculated by code from measurable signals (as in the canvas), never by asking the AI how sure it is.

### 3. Minimum coverage per assessment

- The professor sets how much of the assessment must be answered for the overall result to count as reliable, e.g. at least 70% of the weighted questions. **(my call, reusing canvas §11.2)**

### 4. Automatic release or review

- **All questions above their threshold, and coverage high enough:** results go straight to the student.
- **Any question below its threshold, or coverage too low:** the assessment goes to the reviewer.
- The reviewer checks the flagged questions and the overall assessment, edits if needed, and approves. Only then does the student see the results.

### 5. Minimum and maximum time per question (replacing AI detectors)

- The professor sets both for each question.
- **Too fast** (under the minimum): the answer is marked for the reviewer as possibly pasted. **(my call)**
- **Time runs out** (over the maximum): the question counts as unanswered (`N/A`), not as wrong. Several timeouts lower coverage, which sends the assessment to the reviewer. **(my call)**

### 6. Full conversation log

- Every student answer and every AI reply is recorded with a timestamp and can't be changed afterwards.
- Professors and reviewers see the full log. Students see their own interview after their results are released.

### 7. Guardrails for the AI interviewer

- It stays on the assessment topics, doesn't reveal answers or scores, doesn't judge the student as a person, and can't be talked out of its interviewer role. **(to confirm)**

### 8. Human gates kept in place **(my call)**

- The professor approves the configuration (questions, thresholds, time limits, attempts) before any interview runs.
- The professor approves teams before they're published.

## Changes to existing features

| Feature | Change |
| --- | --- |
| **UX-09** Assessment chat | "Pause and resume" is removed. Each question has the professor's min/max time, with a visible countdown. The "questions left" indicator stays. |
| **UX-10** Results page | Adds friendly, encouraging advice: strongest skills and where to improve next. |
| **Decision 2.8** Candidate transparency | Students see results once released, either automatically (high confidence) or after reviewer approval (low confidence). |
| **Core rule** "a human approves every score" | Becomes "the professor sets the trust thresholds, and a human approves everything below them". |

## Removed from the meeting ideas

- Personality-test slider
- AI detectors

## Still open from the meeting

- Matching teams to projects from a pool of case studies.
- Letting the professor write questions or have the LLM suggest them. This is already covered by Interviewer Studio (UX-01), so it may need no change.
