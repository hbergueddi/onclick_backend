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
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat {@code equals/hashCode} de TOUTES les entités JPA (Layer 1 — couverture
 * exhaustive). Découverte dynamique via scan {@code @Entity} : aucune entité ne
 * peut être ratée (présentes ET futures). Teste la seule logique écrite à la main
 * des entités (equals id-based anti-proxy + hashCode stable par classe) ; les
 * getters/setters Lombok ne sont pas re-testés (code généré).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AllEntitiesEqualsHashCodeContractTest {

    static List<Class<?>> entities() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        return scanner.findCandidateComponents("com.onesley.oneclick").stream()
            .map(bd -> {
                try { return Class.forName(bd.getBeanClassName()); }
                catch (ClassNotFoundException e) { throw new IllegalStateException(e); }
            })
            .sorted(Comparator.comparing(Class::getSimpleName))
            .collect(Collectors.toList());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entities")
    void equalsHashCode_idBasedContract(Class<?> type) throws Exception {
        Field idField = idField(type);
        assertThat(idField).as("@Id/@EmbeddedId sur %s", type.getSimpleName()).isNotNull();
        idField.setAccessible(true);

        Object a = instantiate(type);
        Object b = instantiate(type);
        Object c = instantiate(type);

        Object id = sampleId(idField.getType());
        idField.set(a, id);
        idField.set(b, id);              // même id que a
        idField.set(c, sampleId(idField.getType())); // id différent

        // Invariants universels (vrais que equals soit surchargé ou pas)
        assertThat(a.equals(a)).as("réflexif").isTrue();
        assertThat(a.equals(null)).as("jamais égal à null").isFalse();
        assertThat(a.equals("autre-type")).as("jamais égal à un autre type").isFalse();

        if (declaresEquals(type)) {
            // Pattern OneClick id-based anti-proxy : même id => égaux, hashCode stable par classe.
            assertThat(a).as("%s : même id => égaux", type.getSimpleName()).isEqualTo(b);
            assertThat(a.hashCode()).as("%s : hashCode stable", type.getSimpleName()).isEqualTo(b.hashCode());
            assertThat(a.equals(c)).as("%s : id différent => non égaux", type.getSimpleName()).isFalse();
        } else {
            // Pas d'equals surchargé => sémantique d'identité (Object) : instances distinctes non égales.
            assertThat(a.equals(b)).as("%s : identité, instances distinctes non égales", type.getSimpleName()).isFalse();
        }
    }

    /** L'entité (ou un ancêtre non-Object) déclare-t-elle son propre {@code equals} ? */
    private static boolean declaresEquals(Class<?> type) {
        for (Class<?> k = type; k != null && k != Object.class; k = k.getSuperclass()) {
            try { k.getDeclaredMethod("equals", Object.class); return true; }
            catch (NoSuchMethodException ignored) { /* continue up */ }
        }
        return false;
    }

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

    private static final Random RND = new Random();

    private static Object sampleId(Class<?> idType) throws Exception {
        if (idType == UUID.class) return UUID.randomUUID();
        if (idType == Long.class || idType == long.class) return RND.nextLong();
        if (idType == Integer.class || idType == int.class) return RND.nextInt(1, Integer.MAX_VALUE);
        if (idType == String.class) return UUID.randomUUID().toString();
        // clé composite (@EmbeddedId) : on fabrique deux instances distinctes en
        // settant un champ UUID/String, sinon on renvoie une nouvelle instance.
        Object key = instantiate(idType);
        for (Field f : idType.getDeclaredFields()) {
            if (f.getType() == UUID.class) { f.setAccessible(true); f.set(key, UUID.randomUUID()); break; }
        }
        return key;
    }
}
