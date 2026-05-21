package com.onesley.oneclick.shared.persistence;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;

/**
 * UserType de passthrough {@code String} ⇄ colonne PostgreSQL {@code jsonb}.
 *
 * <p>Le champ Java reste un {@code String} contenant du JSON brut (le frontend
 * désérialise lui-même). Côté écriture on lie via {@code setObject(.., Types.OTHER)}
 * pour que le driver Postgres caste le texte en {@code jsonb} ; côté lecture on
 * retourne le {@code jsonb} tel quel via {@code getString} (texte).
 *
 * <p>Corrige l'erreur « column is of type jsonb but expression is of type character
 * varying » qui faisait échouer (HTTP 500) tout INSERT/UPDATE JPA sur une entité
 * portant une colonne {@code jsonb} mappée en {@code String} sans type dédié
 * (cf {@code Restaurant.openingHours}). Contrairement à {@code @JdbcTypeCode(SqlTypes.JSON)}
 * (qui (dé)sérialise via Jackson et casse la lecture d'un objet jsonb vers un String),
 * ce type est un passthrough pur.
 */
public class JsonStringUserType implements UserType<String> {

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<String> returnedClass() {
        return String.class;
    }

    @Override
    public boolean equals(String x, String y) {
        return Objects.equals(x, y);
    }

    @Override
    public int hashCode(String x) {
        return Objects.hashCode(x);
    }

    @Override
    public String nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
        throws SQLException {
        return rs.getString(position);
    }

    @Override
    public void nullSafeSet(PreparedStatement st, String value, int index, SharedSessionContractImplementor session)
        throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            st.setObject(index, value, Types.OTHER);
        }
    }

    @Override
    public String deepCopy(String value) {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(String value) {
        return value;
    }

    @Override
    public String assemble(Serializable cached, Object owner) {
        return (String) cached;
    }
}
