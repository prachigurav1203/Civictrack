package com.civictrack.service;

import com.civictrack.dto.Dtos.GeoPlace;
import com.civictrack.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-side proxy for the Nominatim (OpenStreetMap) geocoder. Going through the backend lets us follow the
 * public usage policy: identifying User-Agent, max 1 request/second, result caching and graceful errors.
 * For heavy traffic point app.geocoder.base-url at your own Nominatim / a commercial geocoder.
 */
@Slf4j
@Service
public class GeocodingService {
    private static final long MIN_GAP_MS = 1100;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong nextSlot = new AtomicLong(0);
    private final Map<String, Object> cache = Collections.synchronizedMap(new LinkedHashMap<String, Object>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Object> e) { return size() > 500; }
    });
    private final String baseUrl;
    private final String userAgent;
    private final String countryCodes;

    public GeocodingService(@Value("${app.geocoder.base-url}") String baseUrl,
                            @Value("${app.geocoder.user-agent}") String userAgent,
                            @Value("${app.geocoder.country-codes:}") String countryCodes) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.userAgent = userAgent;
        this.countryCodes = countryCodes == null ? "" : countryCodes.trim();
    }

    @SuppressWarnings("unchecked")
    public List<GeoPlace> search(String q) {
        String query = q == null ? "" : q.trim();
        if (query.length() < 3) throw ApiException.badRequest("Type at least 3 characters to search");
        if (query.length() > 200) throw ApiException.badRequest("Search text is too long");
        String key = "s:" + query.toLowerCase();
        Object hit = cache.get(key);
        if (hit != null) return (List<GeoPlace>) hit;

        String url = baseUrl + "/search?format=jsonv2&limit=6&addressdetails=0&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + (countryCodes.isEmpty() ? "" : "&countrycodes=" + URLEncoder.encode(countryCodes, StandardCharsets.UTF_8));
        JsonNode root = fetch(url);
        List<GeoPlace> out = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode n : root) {
                try {
                    out.add(new GeoPlace(n.path("display_name").asText(""), Double.parseDouble(n.path("lat").asText()),
                            Double.parseDouble(n.path("lon").asText()), n.path("type").asText("")));
                } catch (NumberFormatException ignored) { }
            }
        }
        cache.put(key, out);
        return out;
    }

    public GeoPlace reverse(double lat, double lon) {
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) throw ApiException.badRequest("Invalid coordinates");
        String key = String.format(Locale.ROOT, "r:%.5f,%.5f", lat, lon);
        Object hit = cache.get(key);
        if (hit != null) return (GeoPlace) hit;

        JsonNode n = fetch(String.format(Locale.ROOT, "%s/reverse?format=jsonv2&zoom=18&lat=%.6f&lon=%.6f", baseUrl, lat, lon));
        if (n.has("error") || !n.has("display_name")) throw ApiException.notFound("No address found for this point");
        GeoPlace p = new GeoPlace(n.path("display_name").asText(), lat, lon, n.path("type").asText(""));
        cache.put(key, p);
        return p;
    }

    private JsonNode fetch(String url) {
        long now = System.currentTimeMillis();
        if (nextSlot.get() - now > 5000) throw ApiException.tooMany("The location service is busy, please try again in a few seconds");
        long start = Math.max(nextSlot.getAndUpdate(prev -> Math.max(prev, System.currentTimeMillis()) + MIN_GAP_MS), now);
        long wait = start - now;
        if (wait > 0) {
            try { Thread.sleep(wait); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                    .header("User-Agent", userAgent).header("Accept", "application/json").GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 429) throw ApiException.tooMany("The location service is rate-limiting requests, try again shortly");
            if (res.statusCode() / 100 != 2) throw new ApiException(HttpStatus.BAD_GATEWAY, "Location service error (" + res.statusCode() + "). You can still pick the location on the map.");
            return mapper.readTree(res.body());
        } catch (IOException e) {
            log.warn("Geocoder request failed: {}", e.toString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Location service is unavailable. You can still pick the location on the map.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Location lookup was interrupted");
        }
    }
}
