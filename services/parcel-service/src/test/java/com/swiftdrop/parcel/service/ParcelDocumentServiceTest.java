package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.domain.Parcel;
import com.swiftdrop.parcel.domain.ParcelStatus;
import com.swiftdrop.parcel.repository.ParcelRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParcelDocumentServiceTest {

    private final ParcelRepository repository = mock(ParcelRepository.class);
    private final ParcelDocumentService service = new ParcelDocumentService(repository, "http://localhost:3000/track");

    @Test
    void customerOwnerGetsPdfWithoutPersonalDetails() {
        UUID userId = UUID.randomUUID();
        Parcel parcel = parcel(userId);
        when(repository.findById(parcel.getId())).thenReturn(Optional.of(parcel));

        byte[] receipt = service.createReceipt(parcel.getId(), userId, Set.of("CUSTOMER"));

        assertThat(new String(receipt, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        try (var document = Loader.loadPDF(receipt)) {
            String pdfText = new PDFTextStripper().getText(document);
            assertThat(pdfText).contains("SD-RECEIPT-1", "Colombo", "Kandy", "600.00");
            assertThat(pdfText).doesNotContain("Secret full pickup address", "Secret full dropoff address");
        } catch (java.io.IOException exception) {
            throw new AssertionError("Generated receipt was not a readable PDF", exception);
        }
    }

    @Test
    void unrelatedUserCannotDownloadReceipt() {
        Parcel parcel = parcel(UUID.randomUUID());
        when(repository.findById(parcel.getId())).thenReturn(Optional.of(parcel));

        assertThatThrownBy(() -> service.createReceipt(parcel.getId(), UUID.randomUUID(), Set.of("CUSTOMER")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value())
                .isEqualTo(403);
    }

    @Test
    void customerOwnerGetsPdfLabelWithTrackingQrUrl() {
        UUID userId = UUID.randomUUID();
        Parcel parcel = parcel(userId);
        when(repository.findById(parcel.getId())).thenReturn(Optional.of(parcel));

        byte[] label = service.createLabel(parcel.getId(), userId, Set.of("CUSTOMER"));

        assertThat(new String(label, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        try (var document = Loader.loadPDF(label)) {
            String pdfText = new PDFTextStripper().getText(document);
            assertThat(pdfText).contains("SwiftDrop parcel label", "SD-RECEIPT-1",
                    "http://localhost:3000/track/SD-RECEIPT-1");
        } catch (java.io.IOException exception) {
            throw new AssertionError("Generated label was not a readable PDF", exception);
        }
    }

    private static Parcel parcel(UUID customerId) {
        Parcel parcel = new Parcel("SD-RECEIPT-1", customerId, new BigDecimal("1.0"), "SMALL",
                new BigDecimal("600.00"), "Secret full pickup address", "Secret full dropoff address");
        parcel.setId(UUID.randomUUID());
        parcel.setPickupCity("Colombo");
        parcel.setDropoffCity("Kandy");
        parcel.setCurrentStatus(ParcelStatus.CREATED);
        return parcel;
    }
}
