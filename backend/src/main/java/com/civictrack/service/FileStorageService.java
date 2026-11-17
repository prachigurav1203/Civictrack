package com.civictrack.service;

import com.civictrack.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stores uploaded photos. Every file is validated by size AND by its real content (magic bytes), not just the
 * client-supplied name / content type. The stored file name is a random UUID plus the extension of the DETECTED type.
 */
@Service
public class FileStorageService {
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private final Path root;

    public FileStorageService(@Value("${app.upload-dir}") String dir) throws IOException {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public Path getRoot() { return root; }

    /** Validates all files first, then stores them. Returns the public URL paths (e.g. /uploads/abc.jpg). */
    public List<String> storeAll(List<MultipartFile> files, int max) {
        List<MultipartFile> real = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (real.size() > max) throw ApiException.badRequest("You can upload at most " + max + " photos");
        List<String> exts = new ArrayList<>();
        for (MultipartFile f : real) exts.add(validate(f));

        List<String> urls = new ArrayList<>();
        try {
            for (int i = 0; i < real.size(); i++) {
                String name = UUID.randomUUID() + "." + exts.get(i);
                try (InputStream in = real.get(i).getInputStream()) {
                    Files.copy(in, root.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                }
                urls.add("/uploads/" + name);
            }
        } catch (IOException e) {
            urls.forEach(this::deleteQuietly);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the uploaded photo");
        }
        return urls;
    }

    private String validate(MultipartFile file) {
        if (file.getSize() > MAX_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Each photo must be 5 MB or smaller");
        String type = file.getContentType();
        if (type == null || !type.toLowerCase().startsWith("image/")) throw ApiException.badRequest("Only image files are allowed");
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(12);
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the uploaded file");
        }
        String ext = detect(head);
        if (ext == null) throw ApiException.badRequest("Allowed photo types: JPG, PNG, GIF, WEBP (file content does not match an image)");
        return ext;
    }

    private static String detect(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return "jpg";
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) return "png";
        if (h.length >= 6) {
            String s = new String(h, 0, 6, StandardCharsets.ISO_8859_1);
            if (s.equals("GIF87a") || s.equals("GIF89a")) return "gif";
        }
        if (h.length >= 12 && new String(h, 0, 4, StandardCharsets.ISO_8859_1).equals("RIFF")
                && new String(h, 8, 4, StandardCharsets.ISO_8859_1).equals("WEBP")) return "webp";
        return null;
    }

    private void deleteQuietly(String url) {
        try { Files.deleteIfExists(root.resolve(url.substring("/uploads/".length()))); } catch (IOException ignored) { }
    }
}
