# Timefold Solver

Version: 2.8.x (pinned 2.8.0 in backend/pom.xml)
Sources:
- [1] https://docs.timefold.ai/timefold-solver/latest/upgrading-timefold-solver/upgrade-from-v1
- [2] https://docs.timefold.ai/timefold-solver/latest/running-timefold-solver/library/spring-boot
- [3] https://docs.timefold.ai/timefold-solver/latest/running-timefold-solver/solver-diagnostics
- [4] https://github.com/TimefoldAI/timefold-solver/tree/v2.8.0/docs/src/modules/ROOT/pages (score-calculation.adoc, load-balancing-and-fairness.adoc, `_config-properties.adoc`)
- [5] https://github.com/TimefoldAI/timefold-solver/tree/v2.8.0/core/src/main/java/ai/timefold/solver/core (API source and Javadoc)
- [6] https://github.com/TimefoldAI/timefold-solver/tree/v2.8.0/spring-integration/spring-boot-autoconfigure (properties classes)
- [7] https://github.com/TimefoldAI/timefold-solver/blob/v2.8.0/build/build-parent/pom.xml (Spring Boot 4.1.1 build)
- [8] https://github.com/TimefoldAI/timefold-quickstarts/tree/main/getting-started/spring-boot-integration
- [9] https://github.com/TimefoldAI/timefold-solver/blob/v2.8.0/build/bom/pom.xml (BOM contents)
Retrieved: 2026-11-24

## Dependencies and Spring Boot support
- The artifact is `ai.timefold.solver:timefold-solver-spring-boot-starter`. [2]
- The docs state: the starter "only supports Spring Boot version 4.x". [2]
- Timefold 2.8.0 builds the starter against Spring Boot 4.1.1. [7]
- The starter and `timefold-solver-jackson` use Jackson 3 (`tools.jackson`). [1]
- The project pom declares only `timefold-solver-core`. Auto-configuration needs the starter.
- 2.x requires Java 21 or later. [1]
- No `timefold-solver-test` artifact exists in 2.x. The BOM does not list it. The testing API is in core. [1] [9]

## Injection
- The starter injects `SolverConfig`, `SolverFactory`, `SolverManager`, `SolutionManager`, `ConstraintMetaModel`, and `ConstraintVerifier`. [2]
- The starter finds the `@PlanningSolution` class and the `ConstraintProvider` class on the classpath. [6]
- If more than one `ConstraintProvider` exists, startup fails unless you configure it. [6]
- `SolverManager` has one type parameter: `SolverManager<Solution_>`. [5]
- Page [2] still shows `SolverManager<Timetable, String>` in its snippets. That form is 1.x. Do not copy it.
- The quickstart injects through the constructor. [8]

```java
SolverManager<TeamPlan> solverManager;            // from the starter
SolutionManager<TeamPlan, HardSoftScore> solutions; // score without solving

solverManager.solveBuilder()
    .withProblemId(jobId)
    .withProblemFinder(id -> store.get(id))
    .withBestSolutionEventConsumer(e -> store.put(jobId, e.solution()))
    .withExceptionHandler((id, ex) -> log.error("solve {}", id, ex))
    .run();
```
[8] [5]

- `SolutionManager.update(solution)` recalculates the score without solving. [5]
- `SolutionManager.analyze(...)` needs Timefold Enterprise Edition. It fails with the community edition. [1] [5]
- `ScoreAnalysis`, solution diff, and recommended assignment are Enterprise only. [1]

## Domain model
- Use these packages. [5]
  - `ai.timefold.solver.core.api.domain.solution`: `@PlanningSolution`, `@PlanningScore`, `@ProblemFactCollectionProperty`, `@PlanningEntityCollectionProperty`.
  - `ai.timefold.solver.core.api.domain.entity.PlanningEntity`.
  - `ai.timefold.solver.core.api.domain.variable.PlanningVariable`.
  - `ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider`.
  - `ai.timefold.solver.core.api.domain.common.PlanningId`.
  - `ai.timefold.solver.core.api.score.HardSoftScore`.
- `@PlanningId` moved from `...domain.lookup` to `...domain.common`. [1]
- Score classes moved from `...score.buildin.<type>` to `...api.score`. [1]
- Score getters lost the `get` prefix: `hardScore()`. `*LongScore` types merged into `HardSoftScore` and peers. [1]
- `@PlanningVariable(nullable = ...)` is now `allowsUnassigned = ...`. [1] [5]
- A solution class needs a public no-arg constructor and at least one problem fact collection. [5]
- Annotations on a getter need a public getter and a public setter. [1]
- `@ConstraintConfiguration` and `@ConstraintWeight` do not exist. Use `ConstraintWeightOverrides`. [1]

```java
@PlanningSolution
public class TeamPlan {
    @ProblemFactCollectionProperty @ValueRangeProvider
    private List<Team> teams;
    @PlanningEntityCollectionProperty
    private List<Candidate> candidates;
    @PlanningScore
    private HardSoftScore score;
    public TeamPlan() {}
}

@PlanningEntity
public class Candidate {
    @PlanningId private String id;
    @PlanningVariable private Team team;
    public Candidate() {}
}
```
[8]

## Constraint Streams
- Implement `ConstraintProvider.defineConstraints(ConstraintFactory)`. It returns `Constraint[]`. [8]
- `forEach(Class)` replaces `from(Class)`. [1]
- `forEach` skips entities that have a null planning variable. [5]
- `forEachIncludingUnassigned` replaces `fromUnfiltered`. [1]
- Write `penalize(score[, weigher]).asConstraint("id")`. The `penalize("name", score)` form is gone. [1]
- `asConstraint` takes one id. Do not use slashes or special characters in the id. [1]
- `penalizeLong` is now `penalize`. The weigher returns `long`. [1] [5]
- `groupBy(keyFn, collector)` yields a bi stream. Use `filter` on the result. [4]
- Group keys need a stable `hashCode`. Do not compute it from planning variables. [4]
- `ConstraintCollectors.loadBalance(fn)` and `LoadBalance::unfairness` measure fairness. The docs advise a `BigDecimal` score type with it. [4]

```java
Constraint teamTooLarge(ConstraintFactory f) {
    return f.forEach(Candidate.class)
        .groupBy(Candidate::getTeam, ConstraintCollectors.count())
        .filter((team, size) -> size > 5)
        .penalize(HardSoftScore.ONE_HARD, (team, size) -> size - 5)
        .asConstraint("Team too large");
}
```
[4]

## ConstraintVerifier tests
- The class is `ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier`. [1] [5]
- Build it with `ConstraintVerifier.build(provider, TeamPlan.class, Candidate.class)`. [8]
- `verifyThat(Provider::method).given(facts...)` takes entities and facts. [5]
- `verifyThat(...).givenSolution(solution)` takes a whole solution. [5]
- Assert with `penalizesBy(n)`, `penalizes(times)`, `rewardsWith(n)`, or `hasNoImpact()`. [5]
- The message argument comes first: `penalizesBy("msg", 1)`. In 1.x it came last. [1]

```java
verifier.verifyThat(TeamConstraints::teamTooLarge)
    .given(c1, c2, c3, c4, c5, c6)   // all six in the same team
    .penalizesBy(1);
```
[8]

## Solver run configuration
- Spring properties: `timefold.solver.termination.spent-limit` (a `Duration`, for example `5s` or `PT5S`). [4] [6]
- Other termination properties: `unimproved-spent-limit` and `best-score-limit` (for example `0hard/*soft`). [4]
- Other solver properties: `timefold.solver.random-seed` (a `Long`) and `timefold.solver.environment-mode`. [4] [6]
- Spring has no step-count termination property. Use `TerminationConfig.withStepCountLimit(int)` in Java. [5] [6]
- `timefold.solver.solve-length` is now `timefold.solver.solve.duration`. [1]
- Java equivalent: `new SolverConfig().withTerminationSpentLimit(Duration.ofSeconds(5)).withEnvironmentMode(...).withRandomSeed(42L)`. [5]
- The default environment mode is `PHASE_ASSERT`. All modes except `NON_REPRODUCIBLE` are reproducible. [3] [5]
- Time-based termination can still give different results when CPU time differs. [5]
- Do not store planning entities or values in `HashSet` or `HashMap`. Use `ArrayList` or `LinkedHashSet`. [3]
- Simulated annealing with time termination breaks reproducibility. Use step-count termination or Late Acceptance. [3]

```properties
timefold.solver.termination.spent-limit=5s
timefold.solver.random-seed=42
timefold.solver.environment-mode=PHASE_ASSERT
```
[4] [6]

## 1.x to 2.x traps
- `EnvironmentMode.FAST_ASSERT` is now `STEP_ASSERT`. `REPRODUCIBLE` is now `NO_ASSERT`. [5]
- `SolverManager<Solution_, ProblemId_>` lost its second type parameter. [1]
- The overloaded `solve(id, finder, consumer)` methods are removed. Use `solveBuilder()`. [1]
- Consumers receive event objects: `withBestSolutionEventConsumer`, then `event.solution()`. [5] [8]
- `ConstraintRef.of(pkg, name)` is now `ConstraintRef.of(id)`. [1]
