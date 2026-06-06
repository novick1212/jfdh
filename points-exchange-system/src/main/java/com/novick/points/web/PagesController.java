package com.novick.points.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PagesController {

    @GetMapping("/app")
    public String app() {
        return "redirect:/app/index.html";
    }

    @GetMapping("/admin")
    public String admin() {
        return "redirect:/admin/index.html";
    }
}
