package com.nirvaankar.marketplace.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * These rules are what keep the monolith modular. If they stay green, pulling
 * a module out into its own service later is a deployment change. If they go
 * red and someone suppresses them, it becomes a rewrite.
 */
@AnalyzeClasses(
        packages = "com.nirvaankar.marketplace",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    private static final String[] MODULES = {
            "identity", "seller", "catalog", "inventory", "cart", "promotion",
            "ordering", "payment", "fulfilment", "ledger", "review", "wishlist", "sdui",
            "flags", "platform"
    };

    /**
     * A module's entities are its own business. Reaching into another module's
     * domain package is the single change that would weld the modules together.
     */
    @ArchTest
    static final ArchRule domainPackagesAreModulePrivate =
            noClasses()
                    .that().resideOutsideOfPackage("..identity..")
                    .should().accessClassesThat().resideInAPackage("..identity.domain..")
                    .because("cross-module access goes through the service layer, never the entities");

    @ArchTest
    static final ArchRule repositoriesAreModulePrivate =
            noClasses()
                    .that().resideOutsideOfPackage("..identity..")
                    .should().accessClassesThat().resideInAPackage("..identity.repository..")
                    .because("a repository is an implementation detail of its own module");

    /** common is the foundation. If it depends upwards, the layering is gone. */
    @ArchTest
    static final ArchRule commonDependsOnNoModule =
            noClasses()
                    .that().resideInAPackage("..common..")
                    .should().dependOnClassesThat().resideInAnyPackage(modulePackages())
                    .because("common is shared infrastructure and must not know about business modules");

    /** Entities must not escape through the API. */
    @ArchTest
    static final ArchRule controllersDoNotTouchRepositories =
            noClasses()
                    .that().resideInAPackage("..api..")
                    .should().dependOnClassesThat().resideInAPackage("..repository..")
                    .because("controllers call services; a controller holding a repository has business logic in it");

    @ArchTest
    static final ArchRule controllersDoNotTouchEntities =
            noClasses()
                    .that().resideInAPackage("..api..")
                    .should().dependOnClassesThat().resideInAPackage("..domain..")
                    .because("API layer speaks in DTOs only, so an entity change never breaks a client");

    private static String[] modulePackages() {
        String[] packages = new String[MODULES.length];
        for (int i = 0; i < MODULES.length; i++) {
            packages[i] = "com.nirvaankar.marketplace." + MODULES[i] + "..";
        }
        return packages;
    }
}
