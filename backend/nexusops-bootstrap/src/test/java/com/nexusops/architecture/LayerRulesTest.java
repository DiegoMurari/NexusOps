package com.nexusops.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

/** Relocated from nexusops-shared-kernel - see ModuleBoundaryRulesTest for why. */
class LayerRulesTest {

    private static final String BASE_PACKAGE = "com.nexusops";
    private static final JavaClasses ALL_CLASSES = new ClassFileImporter().importPackages(BASE_PACKAGE);

    @Test
    void controllersShouldNotAccessRepositoriesDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..controller..")
            .should().accessClassesThat().resideInAPackage("..repository..");
        rule.check(ALL_CLASSES);
    }

    @Test
    void servicesShouldNotAccessControllers() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..service..")
            .should().accessClassesThat().resideInAPackage("..controller..");
        rule.check(ALL_CLASSES);
    }

    @Test
    void domainEntitiesShouldNotHaveDependenciesOnUpperLayers() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..domain..")
            .should().accessClassesThat().resideInAnyPackage("..service..", "..controller..", "..repository..");
        rule.check(ALL_CLASSES);
    }

    @Test
    void repositoriesShouldNotAccessServicesOrControllers() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..repository..")
            .should().accessClassesThat().resideInAnyPackage("..service..", "..controller..");
        rule.check(ALL_CLASSES);
    }

    @Test
    void dtoClassesShouldNotContainBusinessLogic() {
        // Enums do domínio são tipos-valor do contrato (status, prioridade...) e podem aparecer nos DTOs;
        // entidades, serviços e repositórios não.
        ArchRule rule = noClasses()
            .that().resideInAPackage("..dto..")
            .should().accessClassesThat(resideInAnyPackage("..service..", "..repository..", "..domain..")
                .and(not(assignableTo(Enum.class))));
        rule.check(ALL_CLASSES);
    }

    @Test
    void mappersShouldOnlyMapBetweenLayers() {
        ArchRule rule = classes()
            .that().resideInAPackage("..mapper..")
            .should().onlyDependOnClassesThat(resideInAnyPackage("..dto..", "..domain..", "..mapper..")
                .or(resideOutsideOfPackage("com.nexusops..")));
        rule.check(ALL_CLASSES);
    }

    @Test
    void eventsShouldNotDependOnUpperLayers() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..event..")
            .should().accessClassesThat().resideInAnyPackage("..service..", "..controller..", "..repository..");
        rule.check(ALL_CLASSES);
    }

    @Test
    void noDirectDatabaseAccessInServices() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..service..")
            .should().accessClassesThat()
            .areAssignableTo(jakarta.persistence.EntityManager.class);
        rule.check(ALL_CLASSES);
    }

    @Test
    void springAnnotationsOnlyInAppropriateLayers() {
        ArchRule rule = classes()
            .that().areAnnotatedWith(org.springframework.stereotype.Service.class)
            .should().resideInAPackage("..service..");
        rule.check(ALL_CLASSES);

        ArchRule rule2 = classes()
            .that().areAnnotatedWith(org.springframework.stereotype.Repository.class)
            .should().resideInAPackage("..repository..");
        rule2.check(ALL_CLASSES);

        ArchRule rule3 = classes()
            .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .should().resideInAPackage("..controller..");
        rule3.check(ALL_CLASSES);
    }
}
