In this session we will work on improving the current project setup (AI harness, tools and project setup). These instructions came from my professor today and they are suggestions, NOT mandatory changes. We already created a pretty close enough setup that includes KIRO features and SDD features. Therefore I will need you to take a role of a very grumpy and critical senior developer that one doesn't want to change the codebase just because someone told him to, but also a developer that really loves his project and genuinely would like to add things that would improve it or make it better. This balanced approach will ensure that we're thinking about the advice we're given and the gaps that are really present in our project setup. If our project already does something better then we don't need to do that - there is no correct answer, only critical thinking. 

 ## Optimization Paths vs. Solution Space
Optimization Paths vs. Solution Space
1. TOKEN OPTIMIZATION
• Input Tokens: Which files are actually sufficient? How can we represent
inputs (including file contents & logs) in a reduced form?
• Output Tokens: What is the core information needed? How do we prevent
unnecessary AI chatter and polite filler?
2. PROCESS OPTIMIZATION
• First-Time Quality (FTQ): How do we get working, verified code on the first
pass instead of 5 broken retry loops?
• Human Structure: How do we approach the problem in a structured way?
• AI Structure: How do we help the AI follow a deterministic engineering
process?
Our Solution Space (Tooling & Methods)
RTK (Rust Token Killer)
Filters noisy terminal/Maven logs -> cuts Input Tokens
Caveman Skill
Ultra-terse terminal replies -> cuts Output Tokens
ASD-STE100 Skill
Crisp, unambiguous docs (Output -> clean Input later!)
Context Text Files
CLAUDE.md, architecture.md, features_history.md
Skills (Superpowers)
Structured TDD, Debugging & Review loops -> higher FTQ
Spec-Driven Design
Blueprints & Plan Mode before coding -> higher FTQ

Disclaimer: An Exploration Toolkit, Not Dogma
Innovation Driven Software Engineering (MOD1-01) | FH Dortmund | Bootcamp Day 2
These tools are NOT "the one true Best Practice".
In fact, combining RTK + Caveman + ASD-STE100 + Superpowers + Spec-Driven Design is deliberately a bit
overloaded for a small 5-day student project.
1. See What Exists
Agentic tooling evolves monthly. You
should experience real-world FinOps and
workflow levers hands-on.
2. Feel the Trade-Offs
Only by trying log proxies, terse modes,
and strict skills can you judge where they
help—and where they add friction.
3. Curate Your Own Stack
In future industry projects, pick the 20%
of tools that solve your team's actual
bottleneck.

## RTK 
We already have rtk btw just chek if it's configured properly
```
RTK — Rust Token Killer
Innovation Driven Software Engineering (MOD1-01) | FH Dortmund | Bootcamp Day 2
• Purpose: A lightweight Rust CLI proxy sitting transparently between Claude Code and your terminal. It strips repetitive Maven, Docker, and Git
noise before the output hits the LLM context (60–90% fewer input tokens).
BEFORE: Raw `mvn test` (~180 lines / ~2,400 tokens)
[INFO] Scanning for projects...
[INFO] --- resources:3.3.1:resources ---
[INFO] Downloading from central: https://repo...
[INFO] --- compiler:3.13.0:compile ---
[INFO] Nothing to compile - all classes up to date
... (150 lines of Spring Banner & Hibernate logs) ...
[ERROR] SessionServiceTest.rejectsTurn7:42
expected: IllegalStateException but nothing thrown
AFTER: RTK Filtered Output (~5 lines / ~85 tokens)
mvn test: FAILED (1/8 tests)
FAIL SessionServiceTest#rejectsTurn7 (line 42)
Expected: java.lang.IllegalStateException
Actual: no exception thrown
[RTK: stripped 175 noise lines | saved ~96% tokens]
Setup & Prerequisites (Terminal)
# Prerequisites: Python 3 + RTK binary installed on PATH
python3 --version && rtk --version
# Initialize Claude Code hooks (transparent interception of shell commands)
rtk init
```

## Caveman + ASD-STE-100

I think avoid caveman and include the ask-ste-100

```
Caveman Skill + ASD-STE100
Innovation Driven Software Engineering (MOD1-01) | FH Dortmund | Bootcamp Day 2
CAVEMAN | TERMINAL CHAT
• Purpose: Compresses terminal replies by ~65–75% output tokens. Keeps
all technical facts, kills filler & politeness.
• Scope: Terminal chat ONLY — never for specs or code comments.
Example: Verbose vs. /caveman
Normal: "Great question! I carefully checked line 19
and noticed > allows 7 turns instead of 6..."
Caveman: "Bug: line 19 `>` allows turn 7. Fix: `>=`."
ASD-STE100 | MARKDOWN SPECS
• Purpose: Simplified Technical English (<=20 words/sentence, active
voice, 1 idea/sentence) + automated linter.
• Double Payoff: Clean docs as output today -> lean, zero-ambiguity input
for Claude tomorrow (overrides Caveman).
Example: Vague Spec vs. ASD-STE100
Vague: "The system shouldn't really let users send
more than six turns if they keep trying."
STE100: "The service rejects turn 7 with HTTP 400."
Local Skill Installation (Claude Code)
# 1. Install Caveman skill locally
npx skills add JuliusBrussee/caveman -a claude-code
# 2. Install ASD-STE100 skill (incl. linter) locally
npx skills add danyuchn/asd-ste100-skill -a claude-code
```

## Superpowers

We already have superpowers

```
Jesse Vincent's Superpowers Framework
Innovation Driven Software Engineering (MOD1-01) | FH Dortmund | Bootcamp Day 2
Plan Mode (Safety Gate) + Superpowers (Engineering Methodology)
Superpowers are reusable procedural skills loaded into Claude Code. Instead of guessing how to code or debug, the agent is forced into strict,
repeatable software engineering workflows — dramatically boosting First-Time Quality (FTQ).
/brainstorming — Socratic Requirements Exploration
Forces Claude to ask clarifying questions, surface edge cases, and
propose architectural options BEFORE writing a single line of code.
/test-driven-development — Strict Red-Green-Refactor Loop
Writes failing JUnit 5 tests first, runs Maven to prove RED,
implements minimal Java code for GREEN, then refactors safely.
/systematic-debugging — Root-Cause Hunting Over Guessing
Stops trial-and-error patching. Traces callers, forms a hypothesis,
writes a failing reproduction test, and fixes the true root cause.
/code-review — Pre-PR Enterprise Audit
Audits your Git diff for missing @Transactional boundaries, leaked
JPA entities, hardcoded secrets, and Clean Code violations.
```

I also think we should automate some of this tool calling:

```
Superpowers Loops & Transition to Practice
Innovation Driven Software Engineering (MOD1-01) | FH Dortmund | Bootcamp Day 2
/test-driven-development
• 1. RED: Claude writes a failing JUnit 5 test asserting the exact
business spec and runs `mvn test` to verify failure.
• 2. GREEN: Only AFTER seeing RED does Claude write the minimal
Java code required to pass.
• 3. REFACTOR: Claude cleans up structure/duplication and re-runs
`mvn test` to confirm all tests stay green.
• Your Role: Engineering Manager — verify Claude never skips RED!
/systematic-debugging
• 1. Investigate: Read the stacktrace & trace callers across services to
locate the true origin.
• 2. Hypothesize: Explain WHY the bug happens before touching any
production file.
• 3. Reproduce: Write a failing JUnit test that proves the bug on
demand.
• 4. Fix & Verify: Apply the targeted root-cause patch and verify the
full test suite.
UP NEXT: HANDS-ON SUPERPOWERS LAB -> SPEC-DRIVEN DESIGN
1) Practice /systematic-debugging, /test-driven-development & /code-review on the AssessmentSession sandbox.
2) Then we assemble everything in Spec-Driven Design.
```

# Architecture Best Practices
Package-by-Feature, Data Flow & Security, Cross-Cutting Concerns, and
Layered Testing

This are new things that we didn't know up to now and is what I want you to especially focus on. 

```
ENTERPRISE ENGINEERING STANDARDS
Architecture Best Practices
Package-by-Feature, Data Flow & Security, Cross-Cutting Concerns, and
Layered Testing
01 · STRUCTURAL ORGANIZATION
Why Organize Around Features?
Package-by-Layer (Avoid) Package-by-Feature (Standard)
Scattered Changes: One feature touches three
distant folders.
Forced Visibility: Classes become public across the
app.
Hidden Coupling: Services freely grab any repository.
Vertical Slices: Domain code lives in one folder.
Clear Ownership: Teams work without merge
collisions.
Modular Growth: Features extract into services
cleanly.
01 · PACKAGE LAYOUT
Feature-Centric Directory Structure
de.fh.dortmund.app
├── shared/
│ ├── exception/ # GlobalExceptionHandler
│ └── config/ # App & LLM config
└── <feature>/ # e.g., interview, evaluation
├── web/ # @RestController, Record DTOs
├── service/ # Logic, @Transactional, Events
└── repository/ # @Entity, JpaRepository
shared/
Cross-cutting infrastructure with zero domain logic.
web / service / repository
Strict 3-layer separation inside every feature slice.
Predictable Navigation
Folder names immediately communicate system capabilities.
01 · ENGINEERING PAYOFF
Advantages of Feature Packaging
High Cohesion
Related classes stay tightly grouped.
Safe Refactoring
Blast radius stays locally contained.
AI Context Focus
Agents read one feature folder.
02 · LAYER & CROSS-FEATURE BOUNDARIES
Allowed vs. Forbidden Dependencies
SECTION 03
Data Flow, Persistence & Security
03 · DATA FLOW, PERSISTENCE & SECURITY BY DESIGN
Four Non-Negotiable Data Rules
Shift-Left Validation
Validate inputs at web boundary via @Valid.
Strict DTO Separation
Controllers use Java record DTOs exclusively.
Explicit JPA Fetching
Enforce open-in-view=false; use JOIN FETCH.
Secrets Management
Inject credentials via environment variables only.
03 · REFERENCE PATTERN
Preventing Entity Leaks & N+1 Queries
No Entity Exposure: Blocks mass-assignment attacks
and schema coupling.
OSIV Disabled: Stops hidden database queries during
JSON serialization.
Zero Hardcoded Keys: Keeps API tokens out of Git
history.
04 · CROSS-CUTTING CONCERNS
Error Handling & Domain Events
CENTRALIZED EXCEPTIONS
RFC 7807 ProblemDetail
DECOUPLED SIDE EFFECTS
Async & Domain Events
Domain Errors: Throw custom RuntimeException in service.
Single Mapper: Catch centrally in GlobalExceptionHandler.
Clean Contracts: Never leak raw Java stack traces.
Publish Inside Service: Emit events via
ApplicationEventPublisher.
Commit Safety: Use @TransactionalEventListener.
AFTER_COMMIT: Trigger async jobs only after DB commit.
Test Type Target Layer & Tools Infrastructure
Unit Tests service logic (JUnit 5, Mockito, AssertJ) Pure JVM (no Spring context)
Web Slice web controllers, validation, JSON (@WebMvcTest) Mocked service beans
Persistence Slice JPA mappings & queries (@DataJpaTest) PostgreSQL (Testcontainers / Docker)
05 · VERIFICATION PYRAMID
Layered Testing Strategy
Speed Principle: Keep 80% of tests in fast Pure JVM unit slices; reserve PostgreSQL containers for real persistence verification.
ARCHITECTURE SUMMARY
Clear Boundaries Enable Speed
Feature Slices · Strict DTOs · RFC 7807 Errors · Commit-Safe Events · Layered Tests
```

## Important
The ideas of feature_history.md, feature_backlog.md, ai_docs/ need to be in place. If we don't already solve this then we have to add then directly. 



