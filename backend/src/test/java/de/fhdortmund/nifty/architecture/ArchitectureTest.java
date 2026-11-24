package de.fhdortmund.nifty.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ArchitectureTest {

    private static final String ROOT = "de.fhdortmund.nifty";
    // Fixtures live outside ROOT so @SpringBootApplication does not component- or entity-scan them.
    private static final String FIXTURES = "de.fhdortmund.archfixture";

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    static Stream<Arguments> rules() {
        return Stream.of(
                Arguments.of("layout", (Function<String, ArchRule>) ArchitectureRules::layout),
                Arguments.of("repositoryprivacy", (Function<String, ArchRule>) ArchitectureRules::featureRepositoryIsPrivate),
                Arguments.of("layerdirection", (Function<String, ArchRule>) ArchitectureRules::layerDirection),
                Arguments.of("sharedban", (Function<String, ArchRule>) ArchitectureRules::sharedIsFeatureFree),
                Arguments.of("entityexposure", (Function<String, ArchRule>) ArchitectureRules::noEntityInControllers));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    void productionCodeFollowsRule(String name, Function<String, ArchRule> rule) {
        rule.apply(ROOT).allowEmptyShould(true).check(PRODUCTION);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    void ruleDetectsViolatingFixture(String fixture, Function<String, ArchRule> rule) {
        assertThat(evaluateFixture(rule, fixture).hasViolation())
                .as(fixture + " fixture must violate its rule").isTrue();
    }

    @Test
    void layoutViolationNamesTheMisplacedController() {
        assertThat(violations(ArchitectureRules::layout, "layout"))
                .anyMatch(line -> line.contains("layout.alpha.service.MisplacedController"));
    }

    @Test
    void repositoryViolationNamesBothClasses() {
        assertThat(violations(ArchitectureRules::featureRepositoryIsPrivate, "repositoryprivacy"))
                .anyMatch(line -> line.contains("alpha.service.AlphaService")
                        && line.contains("beta.repository.BetaRepository"));
    }

    private static List<String> violations(Function<String, ArchRule> rule, String fixture) {
        return evaluateFixture(rule, fixture).getFailureReport().getDetails();
    }

    private static EvaluationResult evaluateFixture(Function<String, ArchRule> rule, String fixture) {
        String root = FIXTURES + "." + fixture;
        return rule.apply(root).allowEmptyShould(true).evaluate(new ClassFileImporter().importPackages(root));
    }
}
