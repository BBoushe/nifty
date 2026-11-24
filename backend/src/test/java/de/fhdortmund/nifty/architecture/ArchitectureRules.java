package de.fhdortmund.nifty.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * Architecture rules of backend-java.md §8 (Decision 1.10). Each factory takes the root package so
 * ArchitectureTest can apply it to production code and to a violating fixture.
 */
final class ArchitectureRules {

    private static final String SHARED = "shared";

    private ArchitectureRules() {
    }

    /** REQ-BA-01: shared.{config,exception} or &lt;feature&gt;.{web,service,repository}; stereotypes in their layer. */
    static ArchRule layout(String root) {
        ArchRule packages = classes()
                .that().resideOutsideOfPackages(root + ".shared.config", root + ".shared.exception")
                .and().doNotHaveFullyQualifiedName(root + ".NiftyApplication")
                .should().resideInAnyPackage(root + ".*.web..", root + ".*.service..", root + ".*.repository..")
                .andShould().resideOutsideOfPackage(root + ".shared..");
        ArchRule controllers = classes()
                .that().areAnnotatedWith(RestController.class).or().areAnnotatedWith(Controller.class)
                .should().resideInAPackage(root + ".*.web..");
        ArchRule persistence = classes()
                .that().areAnnotatedWith(Entity.class).or().areAssignableTo(JpaRepository.class)
                .should().resideInAPackage(root + ".*.repository..");
        ArchRule services = classes()
                .that().areAnnotatedWith(Service.class)
                .should().resideInAPackage(root + ".*.service..");
        return CompositeArchRule.of(packages).and(controllers).and(persistence).and(services);
    }

    /** REQ-BA-02: a feature's repository package is used only by the same feature. */
    static ArchRule featureRepositoryIsPrivate(String root) {
        return classes().that().resideInAPackage(root + "..")
                .should(notUseRepositoriesOfOtherFeatures(root));
    }

    /** REQ-BA-03: web never touches repository; repository never touches service or web. */
    static ArchRule layerDirection(String root) {
        ArchRule webToRepository = noClasses().that().resideInAPackage(root + ".*.web..")
                .should().dependOnClassesThat().resideInAPackage(root + ".*.repository..");
        ArchRule repositoryUpwards = noClasses().that().resideInAPackage(root + ".*.repository..")
                .should().dependOnClassesThat().resideInAnyPackage(root + ".*.service..", root + ".*.web..");
        return CompositeArchRule.of(webToRepository).and(repositoryUpwards);
    }

    /** REQ-BA-03: shared never depends on a feature package. */
    static ArchRule sharedIsFeatureFree(String root) {
        return noClasses().that().resideInAPackage(root + ".shared..")
                .should().dependOnClassesThat(resideInAPackage(root + ".*..")
                        .and(resideInAPackage(root + ".shared..").negate())
                        .as("reside in a feature package"));
    }

    /** REQ-BA-04: no @Entity in @RestController method signatures (including generic arguments). */
    static ArchRule noEntityInControllers(String root) {
        return methods().that().areDeclaredInClassesThat().resideInAPackage(root + "..")
                .and().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .should(notExposeEntities());
    }

    private static ArchCondition<JavaClass> notUseRepositoriesOfOtherFeatures(String root) {
        return new ArchCondition<>("not use repositories of other features") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String ownFeature = featureOf(root, origin.getPackageName());
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    String target = dependency.getTargetClass().getBaseComponentType().getPackageName();
                    String feature = featureOf(root, target);
                    if (feature == null || SHARED.equals(feature) || feature.equals(ownFeature)) {
                        continue;
                    }
                    if (isInPackage(target, root + "." + feature + ".repository")) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> notExposeEntities() {
        return new ArchCondition<>("not use @Entity types in parameters or return types") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                reportEntities(method, method.getReturnType(), events);
                for (JavaType parameter : method.getParameterTypes()) {
                    reportEntities(method, parameter, events);
                }
            }
        };
    }

    private static void reportEntities(JavaMethod method, JavaType type, ConditionEvents events) {
        for (JavaClass raw : type.getAllInvolvedRawTypes()) {
            if (raw.isAnnotatedWith(Entity.class)) {
                events.add(SimpleConditionEvent.violated(method,
                        method.getFullName() + " exposes @Entity " + raw.getName()));
            }
        }
    }

    /** First package segment after root, or null when the package is root itself or outside it. */
    static String featureOf(String root, String packageName) {
        if (!packageName.startsWith(root + ".")) {
            return null;
        }
        int start = root.length() + 1;
        int end = packageName.indexOf('.', start);
        return end < 0 ? packageName.substring(start) : packageName.substring(start, end);
    }

    private static boolean isInPackage(String packageName, String parent) {
        return packageName.equals(parent) || packageName.startsWith(parent + ".");
    }
}
