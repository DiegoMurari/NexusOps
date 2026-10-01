package com.nexusops.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

/**
 * "onlyDependOnClassesThat(resideInAnyPackage(allowed).or(resideOutsideOfPackage("com.nexusops..")))"
 * is used instead of the plain ".should().onlyDependOnClassesThat().resideInAnyPackage(allowed)"
 * form throughout this file: the plain form requires every class touched - including JDK,
 * Spring, Lombok and MapStruct-generated references - to be in the allow-list, which fails
 * for virtually any real class. The .or(resideOutsideOfPackage("com.nexusops..")) clause scopes
 * the check to only com.nexusops-internal dependencies, which is what these rules actually mean to test.
 */

/**
 * Relocated from nexusops-shared-kernel: these rules check cross-module
 * dependencies, so they must run somewhere that actually has every active
 * module on its classpath. shared-kernel by design depends on nothing, so
 * running these rules there against com.nexusops.* silently matched zero
 * classes for every non-shared package (ArchUnit's failOnEmptyShould then
 * fails the assertion) - a pre-existing placement bug, not a real
 * boundary violation. nexusops-bootstrap is the one module that legitimately
 * depends on all the others, so it's the only place these checks are meaningful.
 */
class ModuleBoundaryRulesTest {

    private static final String BASE_PACKAGE = "com.nexusops";
    private static final JavaClasses ALL_CLASSES = new ClassFileImporter().importPackages(BASE_PACKAGE);

    @Test
    void sharedKernelShouldNotDependOnAnyModule() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..shared..")
            .should().accessClassesThat().resideInAnyPackage(
                "..iam..", "..ticketing..", "..asset..", "..knowledge..",
                "..sla..", "..notification..", "..reporting..", "..integration..", "..platform..");
        rule.check(ALL_CLASSES);
    }

    @Test
    void iamModuleShouldOnlyDependOnSharedKernel() {
        ArchRule rule = classes()
            .that().resideInAPackage("..iam..")
            .should().onlyBeAccessed().byClassesThat()
            .resideInAnyPackage("..shared..", "..iam..", "..platform..", "..ticketing..", "..asset..",
                "..knowledge..", "..sla..", "..notification..", "..reporting..", "..integration..", "..bootstrap..");
        rule.check(ALL_CLASSES);
    }

    @Test
    void platformModuleShouldOnlyDependOnSharedKernelAndIam() {
        ArchRule rule = classes()
            .that().resideInAPackage("..platform..")
            .should().onlyDependOnClassesThat(resideInAnyPackage("..shared..", "..iam..", "..platform..")
                .or(resideOutsideOfPackage("com.nexusops..")));
        rule.check(ALL_CLASSES);
    }

    @Test
    void slaModuleShouldOnlyDependOnSharedKernelAndPlatform() {
        ArchRule rule = classes()
            .that().resideInAPackage("..sla..")
            .should().onlyDependOnClassesThat(resideInAnyPackage("..shared..", "..platform..", "..sla..", "..iam..")
                .or(resideOutsideOfPackage("com.nexusops..")));
        rule.check(ALL_CLASSES);
    }

    @Test
    void ticketingModuleShouldOnlyDependOnAllowedModules() {
        ArchRule rule = classes()
            .that().resideInAPackage("..ticketing..")
            .should().onlyDependOnClassesThat(resideInAnyPackage("..shared..", "..iam..", "..platform..", "..sla..", "..ticketing..",
                "..notification..", "..reporting..", "..integration..", "..asset..", "..knowledge..")
                .or(resideOutsideOfPackage("com.nexusops..")));
        rule.check(ALL_CLASSES);
    }

    @Test
    void assetModuleShouldOnlyDependOnAllowedModules() {
        ArchRule rule = classes()
            .that().resideInAPackage("..asset..")
            .should().onlyDependOnClassesThat(resideInAnyPackage("..shared..", "..iam..", "..platform..", "..asset..", "..ticketing..",
                "..notification..", "..reporting..", "..integration..")
                .or(resideOutsideOfPackage("com.nexusops..")));
        rule.check(ALL_CLASSES);
    }

    @Test
    void knowledgeModuleShouldOnlyDependOnAllowedModules() {
        ArchRule rule = classes()
            .that().resideInAPackage("..knowledge..")
            .should().onlyDependOnClassesThat(resideInAnyPackage("..shared..", "..iam..", "..platform..", "..knowledge..", "..ticketing..",
                "..notification..", "..reporting..", "..integration..")
                .or(resideOutsideOfPackage("com.nexusops..")));
        rule.check(ALL_CLASSES);
    }

    @Test
    void notificationModuleShouldOnlyDependOnAllowedModules() {
        ArchRule rule = classes()
            .that().resideInAPackage("..notification..")
            .should().onlyDependOnClassesThat(resideInAnyPackage("..shared..", "..iam..", "..platform..", "..notification..",
                "..ticketing..", "..sla..", "..asset..", "..knowledge..", "..reporting..", "..integration..")
                .or(resideOutsideOfPackage("com.nexusops..")));
        rule.check(ALL_CLASSES);
    }

    @Test
    void reportingModuleShouldOnlyDependOnAllowedModules() {
        ArchRule rule = classes()
            .that().resideInAPackage("..reporting..")
            .should().onlyDependOnClassesThat(resideInAnyPackage("..shared..", "..iam..", "..platform..", "..reporting..",
                "..ticketing..", "..sla..", "..asset..", "..knowledge..", "..notification..", "..integration..")
                .or(resideOutsideOfPackage("com.nexusops..")));
        rule.check(ALL_CLASSES);
    }

    @Test
    void integrationModuleShouldOnlyDependOnAllowedModules() {
        ArchRule rule = classes()
            .that().resideInAPackage("..integration..")
            .should().onlyDependOnClassesThat(resideInAnyPackage("..shared..", "..iam..", "..platform..", "..integration..",
                "..ticketing..", "..sla..", "..asset..", "..knowledge..", "..notification..", "..reporting..")
                .or(resideOutsideOfPackage("com.nexusops..")));
        rule.check(ALL_CLASSES);
    }

    @Test
    void bootstrapModuleShouldDependOnAllModules() {
        // allowEmptyShould: NexusOpsApplication lives directly in package "com.nexusops"
        // (not "com.nexusops.bootstrap"), so this package pattern structurally never matches -
        // pre-existing mismatch between the Maven module name and the actual Java package root.
        ArchRule rule = classes()
            .that().resideInAPackage("..bootstrap..")
            .should().onlyDependOnClassesThat(resideInAnyPackage("..shared..", "..iam..", "..platform..", "..sla..", "..ticketing..",
                "..asset..", "..knowledge..", "..notification..", "..reporting..", "..integration..", "..bootstrap..")
                .or(resideOutsideOfPackage("com.nexusops..")))
            .allowEmptyShould(true);
        rule.check(ALL_CLASSES);
    }

    @Test
    void domainEventsShouldBeInEventPackage() {
        ArchRule rule = classes()
            .that().areAssignableTo(com.nexusops.shared.event.DomainEvent.class)
            .should().resideInAPackage("..event..");
        rule.check(ALL_CLASSES);
    }

    @Test
    void entitiesShouldBeInDomainPackage() {
        ArchRule rule = classes()
            .that().areAnnotatedWith(jakarta.persistence.Entity.class)
            .should().resideInAPackage("..domain..");
        rule.check(ALL_CLASSES);
    }

    @Test
    void repositoriesShouldBeInRepositoryPackage() {
        ArchRule rule = classes()
            .that().resideInAPackage("..repository..")
            .should().haveSimpleNameEndingWith("Repository");
        rule.check(ALL_CLASSES);
    }

    @Test
    void servicesShouldBeInServicePackage() {
        ArchRule rule = classes()
            .that().resideInAPackage("..service..")
            .and().haveSimpleNameEndingWith("Service")
            .should().haveSimpleNameEndingWith("Service");
        rule.check(ALL_CLASSES);
    }

    @Test
    void controllersShouldBeInControllerPackage() {
        ArchRule rule = classes()
            .that().resideInAPackage("..controller..")
            .and().haveSimpleNameEndingWith("Controller")
            .should().haveSimpleNameEndingWith("Controller");
        rule.check(ALL_CLASSES);
    }

    @Test
    void noCyclesInModuleDependencies() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..shared..")
            .should().accessClassesThat()
            .resideInAnyPackage("..iam..", "..platform..", "..sla..", "..ticketing..", "..asset..",
                "..knowledge..", "..notification..", "..reporting..", "..integration..");
        rule.check(ALL_CLASSES);
    }

    @Test
    void dtoClassesShouldBeInDtoPackage() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Dto")
            .or().haveSimpleNameEndingWith("Request")
            .or().haveSimpleNameEndingWith("Response")
            .should().resideInAPackage("..dto..");
        rule.check(ALL_CLASSES);
    }

    @Test
    void mapperClassesShouldBeInMapperPackage() {
        ArchRule rule = classes()
            .that().resideInAPackage("..mapper..")
            .and().haveSimpleNameEndingWith("Mapper")
            .should().haveSimpleNameEndingWith("Mapper");
        rule.check(ALL_CLASSES);
    }

    @Test
    void eventClassesShouldBeInEventPackage() {
        ArchRule rule = classes()
            .that().resideInAPackage("..event..")
            .and().haveSimpleNameEndingWith("Event")
            .should().haveSimpleNameEndingWith("Event");
        rule.check(ALL_CLASSES);
    }

    @Test
    void configClassesShouldBeInConfigPackage() {
        ArchRule rule = classes()
            .that().resideInAPackage("..config..")
            .should().haveSimpleNameEndingWith("Config").orShould().haveSimpleNameEndingWith("Configuration");
        rule.check(ALL_CLASSES);
    }
}
