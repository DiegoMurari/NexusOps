package com.nexusops.shared.crypto;

/**
 * Minimal RFC 4648 Base32 codec (unpadded), needed because TOTP/MFA secrets
 * and otpauth:// URIs (Google Authenticator, Authy, etc.) require Base32,
 * not Base64 - java.util.Base64 does not implement this.
 */
public final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32() {
    }

    public static String encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                sb.append(ALPHABET.charAt(index));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            sb.append(ALPHABET.charAt(index));
        }
        return sb.toString();
    }

    public static byte[] decode(String base32) {
        String cleaned = base32.trim().toUpperCase().replace("=", "");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char c : cleaned.toCharArray()) {
            int index = ALPHABET.indexOf(c);
            if (index < 0) {
                continue;
            }
            buffer = (buffer << 5) | index;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }
}
