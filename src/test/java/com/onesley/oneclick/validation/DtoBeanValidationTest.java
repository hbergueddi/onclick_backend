package com.onesley.oneclick.validation;

import com.onesley.oneclick.core.configuration.api.ConfigurationDtos;
import com.onesley.oneclick.core.media.api.MediaDtos;
import com.onesley.oneclick.modules.event.api.EventDtos;
import com.onesley.oneclick.modules.financial.api.FinancialDtos;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyEarnDto;
import com.onesley.oneclick.modules.oneclickhi.api.OneClickHIDtos;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos;
import com.onesley.oneclick.modules.restaurant.api.ExploreFeaturedDtos;
import com.onesley.oneclick.modules.restaurant.api.RestaurantCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantPatchDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires de Bean Validation sur les 15 DTO d'entrée durcis (retour senior).
 *
 * <p>Pur (zéro Spring/DB) : un {@link Validator} jakarta valide chaque record.
 * Pour chaque DTO : une instance « mauvaise » doit produire une violation sur le
 * champ visé, une instance « valide » zéro. Vérifie les bornes ajoutées
 * (@Size colonnes, @DecimalMin montants, @PositiveOrZero/@Min compteurs,
 * lat/long ±90/±180).
 */
class DtoBeanValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        if (factory != null) factory.close();
    }

    private static <T> Set<String> violated(T dto) {
        return validator.validate(dto).stream()
            .map(ConstraintViolation::getPropertyPath)
            .map(Object::toString)
            .collect(Collectors.toSet());
    }

    private static final UUID ID = UUID.randomUUID();

    // ─── RestaurantCreateDto : lat ±90, long ±180, maxStaff @Min(0) ────────────
    @Test
    void restaurantCreate_latitudeOutOfRange_flagged_andValidOk() {
        assertThat(violated(new RestaurantCreateDto(
            ID, "Bistrot", null, null, null, "Casablanca",
            new BigDecimal("999"), null, null, null, null))).contains("latitude");

        assertThat(violated(new RestaurantCreateDto(
            ID, "Bistrot", null, null, null, "Casablanca",
            new BigDecimal("33.5"), new BigDecimal("-7.6"), "marocaine", 10, null))).isEmpty();
    }

    @Test
    void restaurantCreate_negativeMaxStaff_andOutOfRangeLongitude_flagged() {
        assertThat(violated(new RestaurantCreateDto(
            ID, "Bistrot", null, null, null, "Casablanca",
            null, new BigDecimal("999"), null, -1, null)))
            .contains("longitude", "maxStaff");
    }

    // ─── RestaurantPatchDto : @Size colonnes (tous nullable) ───────────────────
    @Test
    void restaurantPatch_oversizedName_flagged_andValidOk() {
        assertThat(violated(new RestaurantPatchDto(
            "X".repeat(200), null, null, null, null, null, null,
            null, null, null, null, null, null, null, null))).contains("name");

        assertThat(violated(new RestaurantPatchDto(
            "Bistrot", "Bonne table", "+212600000000", "12 rue X", "Rabat",
            new BigDecimal("34.0"), new BigDecimal("-6.8"),
            null, null, null, 0, null, "marocaine", 5, null))).isEmpty();
    }

    // ─── LoyaltyEarnDto : amount @DecimalMin(0.00) ─────────────────────────────
    @Test
    void loyaltyEarn_negativeAmount_flagged_andValidOk() {
        assertThat(violated(new LoyaltyEarnDto(ID, ID, 5, new BigDecimal("-5"), "scan")))
            .contains("amount");
        assertThat(violated(new LoyaltyEarnDto(ID, ID, 5, new BigDecimal("120.00"), "scan")))
            .isEmpty();
    }

    // ─── EventCreateDto / EventPatchDto : capacity @PositiveOrZero ─────────────
    @Test
    void eventCreate_negativeCapacity_flagged() {
        assertThat(violated(new EventDtos.EventCreateDto(
            ID, null, "Soirée", null, null, java.time.Instant.now(), null,
            -1, null, null, null, null))).contains("capacity");
    }

    @Test
    void eventPatch_negativeCapacity_flagged_andValidOk() {
        assertThat(violated(new EventDtos.EventPatchDto(
            null, null, null, null, null, -1, null, null, null, null))).contains("capacity");
        assertThat(violated(new EventDtos.EventPatchDto(
            null, null, null, null, null, 50, null, null, null, null))).isEmpty();
    }

    // ─── ResourceCreateDto.capacity / PricingCreateDto.durationMinutes ─────────
    @Test
    void resourceCreate_negativeCapacity_flagged() {
        assertThat(violated(new ResourceBookingDtos.ResourceCreateDto(
            ID, "padel", "Court 1", null, -1))).contains("capacity");
    }

    @Test
    void pricingCreate_negativeDuration_flagged() {
        assertThat(violated(new ResourceBookingDtos.PricingCreateDto(
            ID, "90 min", new BigDecimal("200.00"), -1))).contains("durationMinutes");
    }

    // ─── ExploreFeaturedCreateDto.rank @PositiveOrZero ─────────────────────────
    @Test
    void exploreFeaturedCreate_negativeRank_flagged_andValidOk() {
        assertThat(violated(new ExploreFeaturedDtos.ExploreFeaturedCreateDto(
            ID, -1, true, null, null))).contains("rank");
        assertThat(violated(new ExploreFeaturedDtos.ExploreFeaturedCreateDto(
            ID, 3, true, null, null))).isEmpty();
    }

    // ─── InvoiceUpdateDto montants / InvoiceLineCreateDto.sortOrder ────────────
    @Test
    void invoiceUpdate_negativeAmounts_flagged() {
        assertThat(violated(new FinancialDtos.InvoiceUpdateDto(
            new BigDecimal("-1"), new BigDecimal("-2"), new BigDecimal("-3"),
            null, null, null))).contains("subtotal", "tvaAmount", "totalTtc");
    }

    @Test
    void invoiceLineCreate_negativeSortOrder_flagged() {
        assertThat(violated(new FinancialDtos.InvoiceLineCreateDto(
            ID, "Ligne", new BigDecimal("1.00"), new BigDecimal("10.00"), -1)))
            .contains("sortOrder");
    }

    // ─── OneClickHIInvoice Create/Patch : montants @DecimalMin(0.00) ───────────
    @Test
    void oneClickHiInvoiceCreate_negativeAmounts_flagged() {
        assertThat(violated(new OneClickHIDtos.OneClickHIInvoiceCreateDto(
            ID, ID, "INV-1", "2026-05", new BigDecimal("-1"), new BigDecimal("-2"),
            null, new BigDecimal("-3")))).contains("totalAmount", "vatAmount", "credit3pct");
    }

    @Test
    void oneClickHiInvoicePatch_negativeAmounts_flagged() {
        assertThat(violated(new OneClickHIDtos.OneClickHIInvoicePatchDto(
            null, new BigDecimal("-1"), new BigDecimal("-2"), null, new BigDecimal("-3"),
            null, null, null, null))).contains("totalAmount", "vatAmount", "credit3pct");
    }

    // ─── MediaCreateDto / FileCreateDto : sizeBytes/sortOrder @PositiveOrZero ──
    @Test
    void mediaCreate_negativeSizeAndSortOrder_flagged_andValidOk() {
        assertThat(violated(new MediaDtos.MediaCreateDto(
            "restaurant", ID, "https://x/y.png", "image", "image/png", -1L, -1)))
            .contains("sizeBytes", "sortOrder");
        assertThat(violated(new MediaDtos.MediaCreateDto(
            "restaurant", ID, "https://x/y.png", "image", "image/png", 2048L, 0))).isEmpty();
    }

    @Test
    void fileCreate_negativeSize_flagged() {
        assertThat(violated(new MediaDtos.FileCreateDto(
            "ticket", ID, "/path/x", "application/pdf", -1L, "x.pdf"))).contains("sizeBytes");
    }

    // ─── CacheConfigCreateDto.maxEntries @Min(1) ───────────────────────────────
    @Test
    void cacheConfigCreate_zeroMaxEntries_flagged_andValidOk() {
        assertThat(violated(new ConfigurationDtos.CacheConfigCreateDto("userDetails", 3600, 0)))
            .contains("maxEntries");
        assertThat(violated(new ConfigurationDtos.CacheConfigCreateDto("userDetails", 3600, 5000)))
            .isEmpty();
    }
}
