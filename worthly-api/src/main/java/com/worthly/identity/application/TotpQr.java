package com.worthly.identity.application;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

final class TotpQr {

    private TotpQr() {}

    static String svg(String otpauthUri) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            BitMatrix matrix = new QRCodeWriter().encode(otpauthUri, BarcodeFormat.QR_CODE, 0, 0, hints);
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            StringBuilder svg = new StringBuilder(width * height * 8);
            svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                    .append(width)
                    .append(' ')
                    .append(height)
                    .append("\" shape-rendering=\"crispEdges\">");
            svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#F2EFE8\"/>");
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (matrix.get(x, y)) {
                        svg.append("<rect x=\"")
                                .append(x)
                                .append("\" y=\"")
                                .append(y)
                                .append("\" width=\"1\" height=\"1\" fill=\"#131A19\"/>");
                    }
                }
            }
            svg.append("</svg>");
            return svg.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to render TOTP QR", ex);
        }
    }
}
