package com.onesley.oneclick.shared.persistence;

import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests unitaires de {@link JsonStringUserType} — passthrough String ⇄ jsonb. */
class JsonStringUserTypeTest {

    private final JsonStringUserType type = new JsonStringUserType();

    @Test
    void metadata() {
        assertThat(type.getSqlType()).isEqualTo(Types.OTHER);
        assertThat(type.returnedClass()).isEqualTo(String.class);
        assertThat(type.isMutable()).isFalse();
    }

    @Test
    void equalsHashCode_copy_assembleDisassemble() {
        assertThat(type.equals("a", "a")).isTrue();
        assertThat(type.equals("a", "b")).isFalse();
        assertThat(type.equals(null, null)).isTrue();
        assertThat(type.hashCode("x")).isEqualTo("x".hashCode());
        assertThat(type.deepCopy("x")).isEqualTo("x");
        assertThat(type.disassemble("x")).isEqualTo("x");
        assertThat(type.assemble("x", null)).isEqualTo("x");
    }

    @Test
    void nullSafeGet_returnsString() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("{\"a\":1}");
        assertThat(type.nullSafeGet(rs, 1, null, null)).isEqualTo("{\"a\":1}");
    }

    @Test
    void nullSafeSet_nullAndValue() throws Exception {
        PreparedStatement st = mock(PreparedStatement.class);
        type.nullSafeSet(st, null, 1, null);
        verify(st).setNull(1, Types.OTHER);
        type.nullSafeSet(st, "{\"a\":1}", 2, null);
        verify(st).setObject(eq(2), eq("{\"a\":1}"), eq(Types.OTHER));
    }
}
