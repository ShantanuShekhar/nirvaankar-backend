package com.nirvaankar.marketplace.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/**
 * The N+1 rules, enforced at build time rather than at code review time.
 * An EAGER association is the most common way an innocent-looking list
 * endpoint starts issuing one query per row.
 * <p>
 * This codebase intentionally prefers raw FK columns over JPA associations
 * across module boundaries. The {@code @ManyToOne}/{@code @OneToOne} rules
 * therefore allow an empty selection set, but still fail if any matching
 * association is declared EAGER.
 */
class PersistenceConventionTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.nirvaankar.marketplace");
    }

    @Test
    @DisplayName("no @ManyToOne is EAGER")
    void manyToOneAssociationsAreLazy() {
        ArchRule rule = noFields()
                .that().areAnnotatedWith(ManyToOne.class)
                .should(new EagerFetchCondition())
                .because("EAGER on a @ManyToOne turns every list query into an N+1")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    @DisplayName("no @OneToOne is EAGER")
    void oneToOneAssociationsAreLazy() {
        ArchRule rule = noFields()
                .that().areAnnotatedWith(OneToOne.class)
                .should(new EagerOneToOneCondition())
                .because("@OneToOne defaults to EAGER, so it must be set explicitly")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    @DisplayName("entity-declared createdAt stays on persistence types")
    void entitiesDeclareCreatedAt() {
        // Scope to @Entity types only — API/service DTOs may expose createdAt
        // without living under domain/common.
        ArchRule rule = fields()
                .that().haveName("createdAt")
                .and().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
                .should().beDeclaredInClassesThat().resideInAPackage("..domain..")
                .orShould().beDeclaredInClassesThat().resideInAPackage("..common..")
                .because("persistence audit timestamps belong on entities, not DTO payloads");
        rule.check(classes);
    }

    private static final class EagerFetchCondition
            extends com.tngtech.archunit.lang.ArchCondition<com.tngtech.archunit.core.domain.JavaField> {

        EagerFetchCondition() {
            super("be annotated with fetch = EAGER");
        }

        @Override
        public void check(com.tngtech.archunit.core.domain.JavaField field,
                          com.tngtech.archunit.lang.ConditionEvents events) {
            field.tryGetAnnotationOfType(ManyToOne.class)
                    .filter(annotation -> annotation.fetch() == FetchType.EAGER)
                    .ifPresent(annotation -> events.add(
                            com.tngtech.archunit.lang.SimpleConditionEvent.satisfied(field,
                                    field.getFullName() + " is EAGER")));
        }
    }

    private static final class EagerOneToOneCondition
            extends com.tngtech.archunit.lang.ArchCondition<com.tngtech.archunit.core.domain.JavaField> {

        EagerOneToOneCondition() {
            super("be annotated with fetch = EAGER");
        }

        @Override
        public void check(com.tngtech.archunit.core.domain.JavaField field,
                          com.tngtech.archunit.lang.ConditionEvents events) {
            field.tryGetAnnotationOfType(OneToOne.class)
                    .filter(annotation -> annotation.fetch() == FetchType.EAGER)
                    .ifPresent(annotation -> events.add(
                            com.tngtech.archunit.lang.SimpleConditionEvent.satisfied(field,
                                    field.getFullName() + " is EAGER")));
        }
    }
}
