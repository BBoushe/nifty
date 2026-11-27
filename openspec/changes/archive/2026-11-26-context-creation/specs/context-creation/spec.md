# Spec Delta: Context Creation (`context-creation`)

## ADDED Requirements

### Requirement: REQ-CC-09 Extensible Builder Strategy for Future Fresh-Context Prompts
**THE SYSTEM SHALL** structure internal prompt builders around a common `PromptContextBuilder<I>` interface and `AgentRole` enum so future fresh-context builders (UX-10 advice, Team Mode B) plug in without modifying Agent 1 or Agent 2 builders (Decision 1.18).

#### Scenario: Adding a new agent role leaves existing builders intact
- **WHEN** `InterviewerContextBuilder` and `JudgeContextBuilder` implement `PromptContextBuilder<I>`
- **THEN** `ContextCreationServiceImpl` dispatches cleanly by role with zero shared mutable state between builders.
