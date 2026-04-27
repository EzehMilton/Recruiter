package com.recruiter.web;

import com.recruiter.persistence.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/analytics")
    public String analytics(Model model) {
        model.addAttribute("analytics", analyticsService.load());
        return "analytics";
    }
}
