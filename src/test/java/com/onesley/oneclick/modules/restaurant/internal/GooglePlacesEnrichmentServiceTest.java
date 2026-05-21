package com.onesley.oneclick.modules.restaurant.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link GooglePlacesEnrichmentService} (L3 — modules.restaurant).
 * Branches déterministes (already_enriched / no_api_key / no_match / no_details / error)
 * + helpers statiques privés (mapPriceLevel, extractCuisineAndTags) via réflexion.
 * Le chemin HTTP « succès » dépend du réseau → couvert en intégration, pas ici.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class GooglePlacesEnrichmentServiceTest {

    @Mock EntityManager em;
    @Mock Query query;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        lenient().when(em.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
    }

    private GooglePlacesEnrichmentService svc(RestClient rc, String apiKey) {
        RestClient.Builder b = mock(RestClient.Builder.class);
        when(b.build()).thenReturn(rc);
        GooglePlacesEnrichmentService s = new GooglePlacesEnrichmentService(b);
        ReflectionTestUtils.setField(s, "em", em);
        if (apiKey != null) ReflectionTestUtils.setField(s, "apiKey", apiKey);
        return s;
    }

    /** RestClient avec chaîne fluent explicitement stubée (deep-stubs cassent sur header varargs). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private RestClient restClientReturning(String postBody, String getBody) {
        RestClient rc = mock(RestClient.class);
        // POST → searchText
        RestClient.RequestBodyUriSpec bodyUri = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec postResp = mock(RestClient.ResponseSpec.class);
        when(rc.post()).thenReturn(bodyUri);
        when(bodyUri.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
        when(bodySpec.body(anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(postResp);
        when(postResp.body(String.class)).thenReturn(postBody);
        // GET → place details
        RestClient.RequestHeadersUriSpec headUri = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec getResp = mock(RestClient.ResponseSpec.class);
        when(rc.get()).thenReturn(headUri);
        when(headUri.uri(anyString())).thenReturn(headSpec);
        when(headSpec.header(anyString(), anyString())).thenReturn(headSpec);
        when(headSpec.retrieve()).thenReturn(getResp);
        when(getResp.body(String.class)).thenReturn(getBody);
        return rc;
    }

    @Test
    void enrich_alreadyEnriched_skips() {
        when(query.getSingleResult()).thenReturn(new Object[]{UUID.randomUUID(), "R", "Casa", java.time.Instant.now(), null});
        Map<String, Object> res = svc(mock(RestClient.class, RETURNS_DEEP_STUBS), "KEY").enrichRestaurant(UUID.randomUUID(), false);
        assertThat(res.get("reason")).isEqualTo("already_enriched");
    }

    @Test
    void enrich_noApiKey_skips() {
        when(query.getSingleResult()).thenReturn(new Object[]{UUID.randomUUID(), "R", "Casa", null, null});
        Map<String, Object> res = svc(mock(RestClient.class, RETURNS_DEEP_STUBS), null).enrichRestaurant(UUID.randomUUID(), false);
        assertThat(res.get("reason")).isEqualTo("no_api_key");
    }

    @Test
    void enrich_noMatch_whenSearchReturnsEmpty() {
        when(query.getSingleResult()).thenReturn(new Object[]{UUID.randomUUID(), "R", "Casa", null, null});
        // searchText renvoie un JSON sans "places" → no_match
        Map<String, Object> res = svc(restClientReturning("{}", null), "KEY").enrichRestaurant(UUID.randomUUID(), true);
        assertThat(res.get("reason")).isEqualTo("no_match");
    }

    @Test
    void enrich_noDetails_whenPlaceIdReused() {
        // place_id déjà en DB → skip search, va direct au details (get) qui renvoie null → no_details
        when(query.getSingleResult()).thenReturn(new Object[]{UUID.randomUUID(), "R", "Casa", null, "place-123"});
        Map<String, Object> res = svc(restClientReturning(null, null), "KEY").enrichRestaurant(UUID.randomUUID(), true);
        assertThat(res.get("reason")).isEqualTo("no_details");
    }

    @Test
    void enrich_fullSuccess_updatesRow() {
        when(query.getSingleResult()).thenReturn(new Object[]{UUID.randomUUID(), "R", "Casa", null, null});
        when(query.executeUpdate()).thenReturn(1);
        String search = "{\"places\":[{\"id\":\"place-xyz\",\"displayName\":{\"text\":\"R\"}}]}";
        String details = "{\"id\":\"place-xyz\",\"formattedAddress\":\"123 Rue\",\"nationalPhoneNumber\":\"0600\","
            + "\"location\":{\"latitude\":33.5,\"longitude\":-7.6},\"rating\":4.5,\"userRatingCount\":120,"
            + "\"regularOpeningHours\":{\"weekdayDescriptions\":[\"Lundi: 09:00-18:00\"]},"
            + "\"websiteUri\":\"http://r.ma\",\"types\":[\"moroccan_restaurant\",\"cafe\"],\"priceLevel\":\"PRICE_LEVEL_MODERATE\"}";
        Map<String, Object> res = svc(restClientReturning(search, details), "KEY").enrichRestaurant(UUID.randomUUID(), true);
        assertThat(res.get("enriched")).isEqualTo(true);
        assertThat(res.get("placeId")).isEqualTo("place-xyz");
    }

    @Test
    void enrich_exceptionSwallowed_returnsError() {
        when(query.getSingleResult()).thenReturn(new Object[]{UUID.randomUUID(), "R", "Casa", null, null});
        RestClient rc = mock(RestClient.class, RETURNS_DEEP_STUBS);
        when(rc.post()).thenThrow(new RuntimeException("boom"));
        Map<String, Object> res = svc(rc, "KEY").enrichRestaurant(UUID.randomUUID(), true);
        assertThat(res.get("enriched")).isEqualTo(false);
        assertThat(res).containsKey("error");
    }

    // ─── helpers statiques privés via réflexion ──────────────────────────────────

    @Test
    void mapPriceLevel_allCases() throws Exception {
        Method m = GooglePlacesEnrichmentService.class.getDeclaredMethod("mapPriceLevel", String.class);
        m.setAccessible(true);
        assertThat(m.invoke(null, (Object) null)).isNull();
        assertThat(m.invoke(null, "PRICE_LEVEL_INEXPENSIVE")).isEqualTo("€");
        assertThat(m.invoke(null, "PRICE_LEVEL_MODERATE")).isEqualTo("€€");
        assertThat(m.invoke(null, "PRICE_LEVEL_EXPENSIVE")).isEqualTo("€€€");
        assertThat(m.invoke(null, "PRICE_LEVEL_VERY_EXPENSIVE")).isEqualTo("€€€€");
        assertThat(m.invoke(null, "UNKNOWN")).isNull();
    }

    @Test
    void extractCuisineAndTags_variousTypes() throws Exception {
        Method m = GooglePlacesEnrichmentService.class.getDeclaredMethod("extractCuisineAndTags", JsonNode.class);
        m.setAccessible(true);

        // null/empty → [null, []]
        Object[] empty = (Object[]) m.invoke(null, (Object) null);
        assertThat(empty[0]).isNull();
        assertThat((String[]) empty[1]).isEmpty();

        // moroccan en cuisine principale + tags secondaires (cafe/bar), "restaurant" générique ignoré
        JsonNode types = mapper.readTree("[\"moroccan_restaurant\",\"cafe\",\"bar\",\"restaurant\"]");
        Object[] res = (Object[]) m.invoke(null, types);
        assertThat(res[0]).isEqualTo("Marocaine");
        assertThat((String[]) res[1]).contains("Café", "Bar");

        // seulement "restaurant" générique → cuisine null
        JsonNode generic = mapper.readTree("[\"restaurant\"]");
        Object[] g = (Object[]) m.invoke(null, generic);
        assertThat(g[0]).isNull();
    }
}
