package com.onesley.oneclick.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validation de TOUS les DTO d'entrée contraints (Layer 2 — couverture exhaustive).
 *
 * <p>Découverte dynamique : tout record (y compris imbriqué dans un holder type
 * {@code FinancialDtos}) portant ≥1 contrainte jakarta. Pour chacun :
 * <ul>
 *   <li><b>requis</b> : instance "vide" (null/défauts) → chaque {@code @NotNull /
 *       @NotBlank / @NotEmpty} doit produire une violation sur son champ ;</li>
 *   <li><b>bornes</b> : pour chaque champ Size(max) / Min / Max / DecimalMin /
 *       DecimalMax / Positive(OrZero) / Negative(OrZero), une instance avec UNE
 *       valeur fautive ciblée doit flaguer ce champ.</li>
 * </ul>
 * (Les Pattern purs sont couverts par les tests dédiés ; ici on cible les
 * contraintes à valeur fautive déterministe.)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AllInputDtosValidationTest {

    private static final Object SKIP = new Object();
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() { factory = Validation.buildDefaultValidatorFactory(); validator = factory.getValidator(); }
    @AfterAll
    static void close() { if (factory != null) factory.close(); }

    static List<Class<?>> constrainedDtos() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((mr, f) -> true);
        Set<Class<?>> records = new LinkedHashSet<>();
        for (var bd : scanner.findCandidateComponents("com.onesley.oneclick")) {
            try { collectRecords(Class.forName(bd.getBeanClassName()), records); }
            catch (ClassNotFoundException ignored) { }
        }
        return records.stream().filter(AllInputDtosValidationTest::isConstrained)
            .sorted(Comparator.comparing(Class::getName)).collect(Collectors.toList());
    }

    private static void collectRecords(Class<?> c, Set<Class<?>> acc) {
        if (c.isRecord()) acc.add(c);
        for (Class<?> nested : c.getDeclaredClasses()) collectRecords(nested, acc);
    }

    private static boolean isConstrained(Class<?> c) {
        if (!c.isRecord()) return false;
        // Les contraintes jakarta (@Target FIELD/PARAMETER, pas RECORD_COMPONENT) se
        // posent sur les CHAMPS générés du record, pas sur le RecordComponent.
        for (java.lang.reflect.Field f : c.getDeclaredFields())
            for (Annotation a : f.getAnnotations())
                if (a.annotationType().getPackageName().startsWith("jakarta.validation")) return true;
        return false;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("constrainedDtos")
    void constraints_fireOnInvalidInput(Class<?> dto) throws Exception {
        RecordComponent[] comps = dto.getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[comps.length];
        for (int i = 0; i < comps.length; i++) paramTypes[i] = comps[i].getType();
        Constructor<?> ctor = dto.getDeclaredConstructor(paramTypes);
        ctor.setAccessible(true);

        // (a) Champs requis : instance vide -> violations sur chaque @NotNull/@NotBlank/@NotEmpty
        Object empty = ctor.newInstance(baseline(comps));
        Set<String> emptyViolations = validator.validate(empty).stream()
            .map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
        for (RecordComponent rc : comps) {
            java.lang.reflect.Field f = dto.getDeclaredField(rc.getName());
            if (f.isAnnotationPresent(NotNull.class) || f.isAnnotationPresent(NotBlank.class)
                || f.isAnnotationPresent(NotEmpty.class)) {
                assertThat(emptyViolations)
                    .as("%s.%s : champ requis non validé", dto.getSimpleName(), rc.getName())
                    .contains(rc.getName());
            }
        }

        // (b) Bornes : une valeur fautive ciblée par champ -> ce champ flagué
        for (int i = 0; i < comps.length; i++) {
            Object bad = boundViolation(dto.getDeclaredField(comps[i].getName()), comps[i].getType());
            if (bad == SKIP) continue;
            Object[] args = baseline(comps);
            args[i] = bad;
            Object instance = ctor.newInstance(args);
            Set<String> v = validator.validate(instance).stream()
                .map(cv -> cv.getPropertyPath().toString()).collect(Collectors.toSet());
            assertThat(v).as("%s.%s : borne non validée", dto.getSimpleName(), comps[i].getName())
                .contains(comps[i].getName());
        }
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private static Object[] baseline(RecordComponent[] comps) {
        Object[] a = new Object[comps.length];
        for (int i = 0; i < comps.length; i++) a[i] = primitiveDefault(comps[i].getType());
        return a;
    }

    private static Object primitiveDefault(Class<?> t) {
        if (!t.isPrimitive()) return null;
        if (t == boolean.class) return false;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t == short.class) return (short) 0;
        if (t == byte.class) return (byte) 0;
        if (t == double.class) return 0d;
        if (t == float.class) return 0f;
        if (t == char.class) return '\0';
        return null;
    }

    /** Valeur fautive déterministe pour une contrainte de borne (lue sur le champ), ou SKIP. */
    private static Object boundViolation(java.lang.reflect.Field rc, Class<?> t) {
        Size size = rc.getAnnotation(Size.class);
        if (size != null && t == String.class) {
            if (size.max() < Integer.MAX_VALUE) return "a".repeat(size.max() + 1); // trop long
            if (size.min() > 0) return "";                                          // trop court
        }
        if (rc.isAnnotationPresent(Positive.class)) return num(t, "-1");
        if (rc.isAnnotationPresent(PositiveOrZero.class)) return num(t, "-1");
        if (rc.isAnnotationPresent(Negative.class)) return num(t, "1");
        if (rc.isAnnotationPresent(NegativeOrZero.class)) return num(t, "1");
        Min min = rc.getAnnotation(Min.class);
        if (min != null) return num(t, BigDecimal.valueOf(min.value()).subtract(BigDecimal.ONE).toPlainString());
        Max max = rc.getAnnotation(Max.class);
        if (max != null) return num(t, BigDecimal.valueOf(max.value()).add(BigDecimal.ONE).toPlainString());
        if (rc.isAnnotationPresent(DecimalMin.class)) return num(t, "-999999999");
        if (rc.isAnnotationPresent(DecimalMax.class)) return num(t, "999999999");
        return SKIP;
    }

    /** Construit une valeur numérique du type du composant à partir d'une représentation décimale. */
    private static Object num(Class<?> t, String dec) {
        BigDecimal b = new BigDecimal(dec);
        if (t == BigDecimal.class) return b;
        if (t == Integer.class || t == int.class) return b.intValue();
        if (t == Long.class || t == long.class) return b.longValue();
        if (t == Short.class || t == short.class) return b.shortValue();
        if (t == Byte.class || t == byte.class) return b.byteValue();
        if (t == Double.class || t == double.class) return b.doubleValue();
        if (t == Float.class || t == float.class) return b.floatValue();
        return SKIP; // type inattendu pour une borne numérique
    }
}
