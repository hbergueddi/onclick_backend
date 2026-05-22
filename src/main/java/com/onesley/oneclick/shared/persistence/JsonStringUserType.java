package com.onesley.oneclick.shared.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 *
 * <p><b>Invariant</b> : la {@code String} fournie en écriture doit être du JSON valide
 * (objet, tableau, ou scalaire JSON). Une valeur non-JSON serait rejetée par Postgres
 * au cast {@code ::jsonb} avec une erreur SQL opaque (500) ; on la transforme ici en
 * {@link IllegalArgumentException} explicite, levée au point de binding (debuggable).
 */
public class JsonStringUserType implements UserType<String> {

    /** Validation de bonne-formation JSON (alignée sur ce que Postgres accepte en jsonb). */
    private static final ObjectMapper JSON = new ObjectMapper();

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
            assertWellFormedJson(value);
            st.setObject(index, value, Types.OTHER);
        }
    }

    /** Échec rapide et explicite si la valeur n'est pas du JSON valide (sinon 500 opaque côté Postgres). */
    private static void assertWellFormedJson(String value) {
        try {
            JSON.readTree(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException(
                "JsonStringUserType : valeur non-JSON pour une colonne jsonb (longueur=" + value.length() + ") : " + e.getOriginalMessage(), e);
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
