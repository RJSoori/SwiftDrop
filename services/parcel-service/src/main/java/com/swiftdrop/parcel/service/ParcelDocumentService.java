package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.domain.Parcel;
import com.swiftdrop.parcel.repository.ParcelRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.beans.factory.annotation.Value;

@Service
public class ParcelDocumentService {

    private final ParcelRepository parcelRepository;
    private final String trackingBaseUrl;

    public ParcelDocumentService(ParcelRepository parcelRepository,
                                 @Value("${swiftdrop.tracking.public-base-url:http://localhost:3000/track}") String trackingBaseUrl) {
        this.parcelRepository = parcelRepository;
        this.trackingBaseUrl = trackingBaseUrl.replaceAll("/+$", "");
    }

    @Transactional(readOnly = true)
    public byte[] createReceipt(UUID parcelId, UUID userId, Set<String> roles) {
        Parcel parcel = findOwnedParcel(parcelId, userId, roles);

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                writeLine(content, 50, 780, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                        20, "SwiftDrop delivery receipt");
                writeLine(content, 50, 735, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                        12, "Tracking number: " + safe(parcel.getTrackingNo()));
                writeLine(content, 50, 710, new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                        12, "Status: " + parcel.getCurrentStatus().name());
                writeLine(content, 50, 685, new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                        12, "Pickup city: " + safe(parcel.getPickupCity()));
                writeLine(content, 50, 660, new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                        12, "Drop-off city: " + safe(parcel.getDropoffCity()));
                writeLine(content, 50, 635, new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                        12, "Booking date: " + parcel.getCreatedAt());
                writeLine(content, 50, 610, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                        14, "Delivery fee: LKR " + parcel.getFeeLkr().toPlainString());
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate parcel receipt", exception);
        }
    }

    @Transactional(readOnly = true)
    public byte[] createLabel(UUID parcelId, UUID userId, Set<String> roles) {
        Parcel parcel = findOwnedParcel(parcelId, userId, roles);
        String trackingUrl = trackingBaseUrl + "/" + URLEncoder.encode(parcel.getTrackingNo(), StandardCharsets.UTF_8);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A6);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page);
                 ByteArrayOutputStream qrOutput = new ByteArrayOutputStream()) {
                MatrixToImageWriter.writeToStream(
                        new QRCodeWriter().encode(trackingUrl, BarcodeFormat.QR_CODE, 240, 240),
                        "PNG", qrOutput);
                PDImageXObject qrCode = PDImageXObject.createFromByteArray(document, qrOutput.toByteArray(), "tracking-qr");
                content.drawImage(qrCode, 65, 160, 180, 180);
                writeLine(content, 35, 135, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                        12, "SwiftDrop parcel label");
                writeLine(content, 35, 115, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                        11, safe(parcel.getTrackingNo()));
                writeLine(content, 35, 90, new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                        9, "Pickup: " + safe(parcel.getPickupCity()));
                writeLine(content, 35, 72, new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                        9, "Drop-off: " + safe(parcel.getDropoffCity()));
                writeLine(content, 35, 48, new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                        7, trackingUrl);
            } catch (WriterException exception) {
                throw new IllegalStateException("Could not generate parcel tracking QR code", exception);
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate parcel label", exception);
        }
    }

    private Parcel findOwnedParcel(UUID parcelId, UUID userId, Set<String> roles) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found"));
        boolean customerOwner = roles.contains("CUSTOMER") && userId.equals(parcel.getCustomerId());
        boolean merchantOwner = roles.contains("MERCHANT") && userId.equals(parcel.getMerchantId());
        if (!customerOwner && !merchantOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Parcel document access denied");
        }
        return parcel;
    }

    private void writeLine(PDPageContentStream content, float x, float y, PDType1Font font,
                           float fontSize, String text) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "Not provided";
        }
        return value.replaceAll("[^\\x20-\\x7E]", "?");
    }
}
