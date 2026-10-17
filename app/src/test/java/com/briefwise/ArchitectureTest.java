package com.briefwise;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;

/**
 * The four architecture rules from CLAUDE.md / DESIGN 6.2. Never weaken a rule to make it pass; ask first.
 * Only production classes are analysed (generated MapStruct implementations included).
 *
 * <p>Wording of rule 4: "only facades and ports are public" is read as: the public top-level classes of a context
 * are its facade, its ports ({@code domain.port}) and its domain types ({@code domain.model}, {@code domain.event}).
 * Domain types must be public because the infrastructure adapters live in other packages; they are still not
 * importable by other contexts (rule 2). Everything in {@code application} and {@code infrastructure} is not public.
 */
@AnalyzeClasses(packages = "com.briefwise", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** Contexts from DESIGN 6.2 other than {@code shared}, which every context may use. */
    private static final List<String> CONTEXTS = List.of(
            "portfolio", "market", "news", "research", "risk", "brief", "compliance", "aiops", "access", "jobs",
            "mcp");

    /** {@code com.briefwise.<context>.<Context>Facade}, including its nested view types. */
    private static final String FACADE_NAME = "com\\.briefwise\\.[a-z]+\\.[A-Z]\\w*Facade(\\$\\w+)*";

    /** Rule 1: the domain is pure Java; it may only depend on the JDK and on other domain packages. */
    @ArchTest
    static final ArchRule domain_is_pure =
            classes().that().resideInAPackage("com.briefwise..domain..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "com.briefwise..domain..")
                    .as("domain packages depend only on java.* and domain packages (no Spring, JPA or libraries)");

    /** Rule 2: the facade is the only way into a context. Shared kernel excepted. */
    @ArchTest
    static void contexts_are_accessed_only_through_their_facade(JavaClasses classes) {
        DescribedPredicate<JavaClass> notAFacade =
                DescribedPredicate.describe("is not a facade", javaClass -> !javaClass.getName().matches(FACADE_NAME));
        for (String context : CONTEXTS) {
            String contextPackage = "com.briefwise." + context + "..";
            noClasses().that().resideOutsideOfPackage(contextPackage)
                    .should().dependOnClassesThat(
                            JavaClass.Predicates.resideInAPackage(contextPackage).and(notAFacade))
                    .as("classes outside '" + context + "' use it only through its facade")
                    .check(classes);
        }
    }

    /** Rule 3: application services are package-private; callers go through the inbound port or the facade. */
    @ArchTest
    static final ArchRule application_services_are_package_private =
            classes().that().resideInAPackage("com.briefwise..application..")
                    .and().haveSimpleNameEndingWith("Service")
                    .should().bePackagePrivate();

    /** Rule 4: public top-level classes of a context are facades, ports or domain types only. */
    @ArchTest
    static final ArchRule only_facades_ports_and_domain_types_are_public =
            classes().that().resideInAPackage("com.briefwise..")
                    .and().resideOutsideOfPackages("com.briefwise", "com.briefwise.shared..")
                    .and().arePublic()
                    .and().areTopLevelClasses()
                    .should().haveNameMatching(FACADE_NAME)
                    .orShould().resideInAnyPackage("..domain.port..", "..domain.model..", "..domain.event..");
}
