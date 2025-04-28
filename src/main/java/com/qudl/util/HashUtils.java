// HashUtils.java
package com.qudl.util;

import org.apache.commons.codec.digest.DigestUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class HashUtils {
    public static String calculateSHA256(Path file) throws IOException {
        try (InputStream is = Files.newInputStream(file)) {
            return DigestUtils.sha256Hex(is);
        }
    }

    public static boolean validateSHA256(Path file, String expectedHash) {
        try {
            return !calculateSHA256(file).equals(expectedHash);
        } catch (IOException e) {
            return true;
        }
    }
}