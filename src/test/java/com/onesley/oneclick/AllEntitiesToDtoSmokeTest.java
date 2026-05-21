package com.onesley.oneclick;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke {@code toDto()} de TOUTES les entités qui en exposent un (Layer 1, 56
 * entités). Découverte dynamique + remplissage réflexif des champs (scalaires +
 * associations shallow avec id), puis appel de {@code toDto()} :
 * <ul>
 *   <li>ne doit jamais lever (NPE de mapping, mauvais ordre de champs record…) ;</li>
 *   <li>doit retourner non-null ;</li>
 *   <li>si le DTO record a un composant {@code id:UUID}, il doit matcher l'id entité.</li>
 * </ul>
 * Le mapping champ-à-champ fin est couvert par les tests dédiés (User, Tenant…).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AllEntitiesToDtoSmokeTest {

    static List<Class<?>> entitiesWithToDto() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        return scanner.findCandidateComponents("com.onesley.oneclick").stream()
            .map(bd -> {
                try { return Class.forName(bd.getBeanClassName()); }
                catch (ClassNotFoundException e) { throw new IllegalStateException(e); }
            })
            .filter(AllEntitiesToDtoSmokeTest::hasNoArgToDto)
            .sorted(Comparator.comparing(Class::getSimpleName))
            .collect(Collectors.toList());
    }

    static boolean hasNoArgToDto(Class<?> t) {
        try {
            Method m = t.getMethod("toDto");
            return m.getParameterCount() == 0 && m.getReturnType() != void.class;
        } catch (NoSuchMethodException e) { return false; }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entitiesWithToDto")
    void toDto_runsAndMapsId(Class<?> type) throws Exception {
        Object entity = instantiate(type);
        populate(entity, type);

        Object dto = type.getMethod("toDto").invoke(entity);
        assertThat(dto).as("%s.toDto() non-null", type.getSimpleName()).isNotNull();

        Field idF = idField(type);
        if (idF != null && idF.getType() == UUID.class && dto.getClass().isRecord()) {
            idF.setAccessible(true);
            Object entityId = idF.get(entity);
            for (RecordComponent rc : dto.getClass().getRecordComponents()) {
                if (rc.getName().equals("id") && rc.getType() == UUID.class) {
                    assertThat(rc.getAccessor().invoke(dto))
                        .as("%s : id mappé dans le DTO", type.getSimpleName()).isEqualTo(entityId);
                }
            }
        }
    }

    // ─── helpers réflexifs ──────────────────────────────────────────────────────

    private static Field idField(Class<?> type) {
        for (Class<?> k = type; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (f.isAnnotationPresent(Id.class) || f.isAnnotationPresent(EmbeddedId.class)) return f;
            }
        }
        return null;
    }

    private static Object instantiate(Class<?> type) throws Exception {
        Constructor<?> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    /** Remplit tous les champs non-statiques/non-finals avec des valeurs d'exemple. */
    private static void populate(Object obj, Class<?> type) {
        for (Class<?> k = type; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isFinal(mod)) continue;
                try {
                    f.setAccessible(true);
                    if (f.get(obj) != null) continue; // garde les défauts déjà posés
                    Object v = sample(f.getType());
                    if (v != null) f.set(obj, v);
                } catch (Exception ignored) { /* champ non settable -> on laisse */ }
            }
        }
    }

    private static Object sample(Class<?> t) {
        if (t == UUID.class) return UUID.randomUUID();
        if (t == String.class) return "x";
        if (t == boolean.class || t == Boolean.class) return Boolean.TRUE;
        if (t == int.class || t == Integer.class) return 1;
        if (t == long.class || t == Long.class) return 1L;
        if (t == short.class || t == Short.class) return (short) 1;
        if (t == byte.class || t == Byte.class) return (byte) 1;
        if (t == double.class || t == Double.class) return 1.0d;
        if (t == float.class || t == Float.class) return 1.0f;
        if (t == BigDecimal.class) return BigDecimal.ONE;
        if (t == Instant.class) return Instant.now();
        if (t == LocalDate.class) return LocalDate.now();
        if (t == LocalDateTime.class) return LocalDateTime.now();
        if (t == OffsetDateTime.class) return OffsetDateTime.now();
        if (t.isEnum()) { Object[] cs = t.getEnumConstants(); return cs.length > 0 ? cs[0] : null; }
        if (List.class.isAssignableFrom(t)) return new ArrayList<>();
        if (java.util.Set.class.isAssignableFrom(t)) return new HashSet<>();
        if (java.util.Map.class.isAssignableFrom(t)) return new HashMap<>();
        if (t.isArray()) return java.lang.reflect.Array.newInstance(t.getComponentType(), 0);
        if (t.isAnnotationPresent(Entity.class)) {
            try {
                Object assoc = instantiate(t);
                Field aid = idField(t);
                if (aid != null && aid.getType() == UUID.class) {
                    aid.setAccessible(true);
                    aid.set(assoc, UUID.randomUUID());
                }
                return assoc;
            } catch (Exception e) { return null; }
        }
        // autre POJO (value object) : tentative no-arg, sinon null
        try { return instantiate(t); } catch (Exception e) { return null; }
    }
}
