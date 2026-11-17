package com.civictrack.controller;

import com.civictrack.dto.Dtos.GeoPlace;
import com.civictrack.service.GeocodingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Authenticated geocoding proxy (place search / address autocomplete / reverse geocoding). */
@RestController
@RequestMapping("/api/geo")
@RequiredArgsConstructor
public class GeoController {
    private final GeocodingService geo;

    @GetMapping("/search")
    public List<GeoPlace> search(@RequestParam String q) { return geo.search(q); }

    @GetMapping("/reverse")
    public GeoPlace reverse(@RequestParam double lat, @RequestParam double lon) { return geo.reverse(lat, lon); }
}
