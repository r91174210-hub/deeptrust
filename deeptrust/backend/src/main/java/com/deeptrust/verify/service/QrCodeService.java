package com.deeptrust.verify.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class QrCodeService {

    @Value("${deeptrust.public-verify-base-url:http://localhost:5173/verify}")
    private String publicVerifyBaseUrl;

    /** Generates a PNG QR code encoding the public verification URL for a certificate. */
    public byte[] generateQrPng(String certificateCode) {
        String url = publicVerifyBaseUrl + "/" + certificateCode;
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(url, BarcodeFormat.QR_CODE, 300, 300);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Failed to generate QR code for certificate " + certificateCode, e);
        }
    }
}
