package com.shineunsigan.snsg;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;

/**
 * 도메인 의존 규칙(.claude/rules/package-structure.md)을 검사한다. 프로덕션 클래스만 본다.
 * <p>
 * 도메인은 base package 바로 아래 패키지 중 {@code common}을 뺀 것이다. 목록을 적어두지 않으므로
 * 도메인이 늘어도 이 테스트를 고치지 않는다. base package 밖으로 향하는 의존(라이브러리)은 보지 않는다.
 */
class ArchitectureTest {

    private static final String BASE = "com.shineunsigan.snsg";
    private static final String COMMON = "common";

    /** 다른 도메인이 참조할 수 있는 계약. 도메인 패키지 기준 상대 경로다. */
    private static final List<String> CONTRACTS = List.of(
            "application.port.in", "application.dto", "application.event", "domain.constants");

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    @Test
    @DisplayName("domain은 같은 도메인의 application·presentation·infrastructure에 의존하지 않는다")
    void domainDoesNotDependOnOuterLayers() {
        layerRule("domain", Set.of("application", "presentation", "infrastructure")).check(CLASSES);
    }

    @Test
    @DisplayName("application은 같은 도메인의 presentation·infrastructure에 의존하지 않는다")
    void applicationDoesNotDependOnPresentationOrInfrastructure() {
        layerRule("application", Set.of("presentation", "infrastructure")).check(CLASSES);
    }

    @Test
    @DisplayName("presentation은 같은 도메인의 infrastructure에 의존하지 않는다")
    void presentationDoesNotDependOnInfrastructure() {
        layerRule("presentation", Set.of("infrastructure")).check(CLASSES);
    }

    @Test
    @DisplayName("다른 도메인에 의존하는 클래스는 infrastructure에 있다")
    void onlyInfrastructureDependsOnOtherDomains() {
        classes().that(inAnyDomain())
                .should(dependOnOtherDomainsOnlyIf(
                        "only depend on other domains from infrastructure",
                        (source, target) -> "infrastructure".equals(layerOf(source))))
                .check(CLASSES);
    }

    @Test
    @DisplayName("다른 도메인에 대한 의존은 그 도메인의 계약(port.in, dto, event, constants)만 향한다")
    void otherDomainDependenciesTargetOnlyContracts() {
        classes().that(inAnyDomain())
                .should(dependOnOtherDomainsOnlyIf(
                        "only depend on other domains' " + CONTRACTS,
                        (source, target) -> isContract(target)))
                .check(CLASSES);
    }

    @Test
    @DisplayName("도메인 사이에 순환이 없다")
    void noCyclesBetweenDomains() {
        slices().assignedFrom(new SliceAssignment() {
                    @Override
                    public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
                        String domain = domainOf(javaClass.getPackageName());
                        return domain == null ? SliceIdentifier.ignore() : SliceIdentifier.of(domain);
                    }

                    @Override
                    public String getDescription() {
                        return "domains under " + BASE;
                    }
                })
                .should().beFreeOfCycles()
                .check(CLASSES);
    }

    @Test
    @DisplayName("common은 어떤 도메인에도 의존하지 않는다")
    void commonDoesNotDependOnDomains() {
        classes().that().resideInAPackage(BASE + "." + COMMON + "..")
                .should(new ArchCondition<>("not depend on any domain") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                            if (domainOf(targetPackage(dependency)) != null) {
                                events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                            }
                        }
                    }
                })
                .check(CLASSES);
    }

    /** 도메인의 {@code layer} 계층이 같은 도메인의 {@code forbidden} 계층에 의존하지 않는다. */
    private static ArchRule layerRule(String layer, Set<String> forbidden) {
        return classes().that(inLayer(layer))
                .should(new ArchCondition<>("not depend on " + forbidden + " of the same domain") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        String domain = domainOf(javaClass.getPackageName());
                        for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                            String target = targetPackage(dependency);
                            if (domain.equals(domainOf(target)) && forbidden.contains(layerOf(target))) {
                                events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                            }
                        }
                    }
                });
    }

    /** 다른 도메인으로 향하는 의존마다 {@code allowed}(출발 패키지, 도착 패키지)가 참이어야 한다. */
    private static ArchCondition<JavaClass> dependOnOtherDomainsOnlyIf(
            String description, BiPredicate<String, String> allowed) {
        return new ArchCondition<>(description) {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String source = javaClass.getPackageName();
                String domain = domainOf(source);
                for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                    String target = targetPackage(dependency);
                    String targetDomain = domainOf(target);
                    if (targetDomain != null && !targetDomain.equals(domain) && !allowed.test(source, target)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static DescribedPredicate<JavaClass> inAnyDomain() {
        return DescribedPredicate.describe("reside in a domain",
                javaClass -> domainOf(javaClass.getPackageName()) != null);
    }

    private static DescribedPredicate<JavaClass> inLayer(String layer) {
        return DescribedPredicate.describe("reside in the " + layer + " layer of a domain",
                javaClass -> domainOf(javaClass.getPackageName()) != null
                        && layer.equals(layerOf(javaClass.getPackageName())));
    }

    private static boolean isContract(String targetPackage) {
        String[] segments = segmentsAfterBase(targetPackage);
        String relative = String.join(".", List.of(segments).subList(1, segments.length));
        return CONTRACTS.stream()
                .anyMatch(contract -> relative.equals(contract) || relative.startsWith(contract + "."));
    }

    private static String targetPackage(Dependency dependency) {
        return dependency.getTargetClass().getBaseComponentType().getPackageName();
    }

    /** base package 바로 아래 세그먼트. base 밖, base 바로 아래 클래스, common이면 null. */
    private static String domainOf(String packageName) {
        String[] segments = segmentsAfterBase(packageName);
        if (segments.length == 0 || COMMON.equals(segments[0])) {
            return null;
        }
        return segments[0];
    }

    /** 도메인 다음 세그먼트(presentation·application·domain·infrastructure). 없으면 null. */
    private static String layerOf(String packageName) {
        String[] segments = segmentsAfterBase(packageName);
        return segments.length < 2 ? null : segments[1];
    }

    private static String[] segmentsAfterBase(String packageName) {
        if (!packageName.startsWith(BASE + ".")) {
            return new String[0];
        }
        return packageName.substring(BASE.length() + 1).split("\\.");
    }
}
