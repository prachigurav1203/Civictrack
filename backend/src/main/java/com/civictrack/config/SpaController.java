package com.civictrack.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Lets the bundled React app handle its own routes (refresh / deep links) when served from the JAR. */
@Controller
public class SpaController {
    @GetMapping({"/", "/login", "/register", "/track", "/dashboard", "/notifications", "/complaints", "/complaints/**", "/admin/**"})
    public String index() { return "forward:/index.html"; }
}
