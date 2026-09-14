package com.nirvaankar.marketplace.shipping;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.Barcode128;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.nirvaankar.marketplace.shipping.ShippingProvider.LabelRequest;
import com.nirvaankar.marketplace.shipping.ShippingProvider.LabelResult;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Printable 4&quot;×4&quot; shipping label with QR code (customer + order details).
 */
public final class ShippingLabelPdf {

    private static final float PT_PER_INCH = 72f;
    private static final Rectangle LABEL = new Rectangle(4f * PT_PER_INCH, 4f * PT_PER_INCH);
    private static final Color BRAND = new Color(31, 61, 43);
    private static final Color BORDER = new Color(210, 210, 210);

    private ShippingLabelPdf() {
    }

    public static LabelResult render(LabelRequest request) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(LABEL, 8, 8, 8, 8);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();
            PdfContentByte cb = writer.getDirectContent();

            Font brand = new Font(Font.HELVETICA, 11, Font.BOLD, BRAND);
            Font label = new Font(Font.HELVETICA, 6.5f, Font.BOLD, Color.DARK_GRAY);
            Font body = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, Color.BLACK);
            Font strong = new Font(Font.HELVETICA, 8.5f, Font.BOLD, Color.BLACK);
            Font pinFont = new Font(Font.HELVETICA, 14, Font.BOLD, Color.BLACK);
            Font badge = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);

            PdfPTable header = new PdfPTable(2);
            header.setWidthPercentage(100);
            header.setWidths(new float[] { 58f, 42f });
            header.addCell(borderedCell("NIRVAANKAR", brand, Element.ALIGN_LEFT, false));
            PdfPCell paymentCell = borderedCell(paymentBadge(request), badge, Element.ALIGN_CENTER, true);
            paymentCell.setBackgroundColor("COD".equalsIgnoreCase(dash(request.paymentMode()))
                    ? new Color(180, 45, 45) : BRAND);
            header.addCell(paymentCell);
            document.add(header);

            document.add(spacer(2));
            document.add(metaRow("Order", dash(request.orderNumber()), label, strong));
            document.add(metaRow("AWB", dash(request.awbNumber()), label, strong));
            document.add(metaRow("Store", dash(request.storeName()), label, body));
            document.add(metaRow("Product", dash(request.productRef()), label, body));
            document.add(metaRow("Weight", request.weightGrams() + " g", label, body));

            document.add(spacer(3));
            PdfPTable shipBlock = new PdfPTable(2);
            shipBlock.setWidthPercentage(100);
            shipBlock.setWidths(new float[] { 62f, 38f });

            PdfPCell addressCell = new PdfPCell();
            addressCell.setBorder(Rectangle.BOX);
            addressCell.setBorderColor(BORDER);
            addressCell.setPadding(6);
            addressCell.addElement(new Paragraph("SHIP TO", label));
            addressCell.addElement(new Paragraph(dash(request.consigneeName()), strong));
            addressCell.addElement(new Paragraph(dash(request.contactPhone()), body));
            addressCell.addElement(new Paragraph(formatAddress(request), body));
            addressCell.addElement(new Paragraph("PIN  " + dash(request.pincode()), pinFont));
            if (request.codAmountMinor() > 0) {
                addressCell.addElement(new Paragraph(
                        "Collect: Rs " + formatAmount(request.codAmountMinor()), strong));
            }
            shipBlock.addCell(addressCell);

            PdfPCell qrCell = new PdfPCell();
            qrCell.setBorder(Rectangle.BOX);
            qrCell.setBorderColor(BORDER);
            qrCell.setPadding(4);
            qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            qrCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            try {
                Image qr = Image.getInstance(qrPng(buildQrPayload(request), 180, 180));
                qr.scaleToFit(95, 95);
                qr.setAlignment(Element.ALIGN_CENTER);
                qrCell.addElement(qr);
            } catch (Exception ignored) {
                qrCell.addElement(new Paragraph("QR", label));
            }
            qrCell.addElement(new Paragraph("Scan for details", label));
            shipBlock.addCell(qrCell);
            document.add(shipBlock);

            document.add(spacer(4));
            String awb = dash(request.awbNumber());
            try {
                Barcode128 barcode = new Barcode128();
                barcode.setCode(awb.length() > 24 ? awb.substring(0, 24) : awb);
                barcode.setCodeType(Barcode128.CODE128);
                barcode.setBarHeight(30f);
                barcode.setX(0.85f);
                barcode.setFont(null);
                Image barImg = barcode.createImageWithBarcode(cb, Color.BLACK, Color.BLACK);
                barImg.scaleToFit(LABEL.getWidth() - 24, 40);
                barImg.setAlignment(Element.ALIGN_CENTER);
                document.add(barImg);
                Paragraph awbText = new Paragraph(awb, strong);
                awbText.setAlignment(Element.ALIGN_CENTER);
                document.add(awbText);
            } catch (Exception ignored) {
                document.add(new Paragraph(awb, strong));
            }

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not generate shipping label PDF", e);
        }
        return new LabelResult(
                "label-" + (request.awbNumber() == null ? "na" : request.awbNumber()),
                out.toByteArray(),
                "application/pdf");
    }

    private static String buildQrPayload(LabelRequest request) {
        StringBuilder payload = new StringBuilder();
        payload.append("NIRVAANKAR SHIPPING\n");
        payload.append("Order: ").append(dash(request.orderNumber())).append('\n');
        payload.append("AWB: ").append(dash(request.awbNumber())).append('\n');
        payload.append("Customer: ").append(dash(request.consigneeName())).append('\n');
        payload.append("Phone: ").append(dash(request.contactPhone())).append('\n');
        payload.append("Address: ").append(formatAddress(request)).append('\n');
        payload.append("Pincode: ").append(dash(request.pincode())).append('\n');
        payload.append("Payment: ").append(paymentBadge(request));
        if (request.codAmountMinor() > 0) {
            payload.append(" Rs ").append(formatAmount(request.codAmountMinor()));
        }
        payload.append('\n');
        payload.append("Product: ").append(dash(request.productRef())).append('\n');
        payload.append("Weight: ").append(request.weightGrams()).append(" g");
        return payload.toString();
    }

    private static String formatAddress(LabelRequest request) {
        String line1 = dash(request.line1());
        String city = dash(request.city());
        String state = dash(request.state());
        String pin = dash(request.pincode());
        if (!line1.equals("-") || !city.equals("-")) {
            return line1 + ", " + city + ", " + state + " - " + pin;
        }
        return dash(request.addressBlock());
    }

    private static String paymentBadge(LabelRequest request) {
        return "COD".equalsIgnoreCase(dash(request.paymentMode())) ? "COD" : "PREPAID";
    }

    private static String formatAmount(long minor) {
        return String.format(Locale.US, "%.2f", minor / 100.0);
    }

    private static byte[] qrPng(String content, int width, int height) throws Exception {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 1);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height, hints);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", png);
        return png.toByteArray();
    }

    private static PdfPCell borderedCell(String text, Font font, int align, boolean filled) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER);
        cell.setPadding(5);
        cell.setHorizontalAlignment(align);
        if (filled) {
            cell.setBackgroundColor(BRAND);
        }
        return cell;
    }

    private static Paragraph metaRow(String key, String value, Font keyFont, Font valueFont) {
        Paragraph p = new Paragraph();
        p.add(new com.lowagie.text.Chunk(key + ":  ", keyFont));
        p.add(new com.lowagie.text.Chunk(value, valueFont));
        return p;
    }

    private static Paragraph spacer(float pts) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingBefore(pts);
        p.setSpacingAfter(pts);
        return p;
    }

    private static String dash(String v) {
        return v == null || v.isBlank() ? "-" : v.trim();
    }
}
