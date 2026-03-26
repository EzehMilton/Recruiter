package com.recruiter.web;

import com.recruiter.config.RecruitmentProperties;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final RecruitmentProperties properties;
    private final ScreeningFormValidator screeningFormValidator;

    @InitBinder("screeningForm")
    public void initBinder(WebDataBinder binder) {
        binder.addValidators(screeningFormValidator);
    }

    @GetMapping("/")
    public String home(Model model) {
        ScreeningForm form = new ScreeningForm();
        form.setShortlistCount(properties.getShortlistCount());
        model.addAttribute("screeningForm", form);
        addFormConstants(model);
        return "index";
    }

    @PostMapping("/analyse")
    public String analyse(@Valid @ModelAttribute ScreeningForm screeningForm,
                          BindingResult bindingResult,
                          RedirectAttributes redirectAttributes,
                          Model model) {

        if (bindingResult.hasErrors()) {
            addFormConstants(model);
            return "index";
        }

        List<MultipartFile> files = screeningForm.getCvFiles();
        int fileCount = (int) files.stream().filter(f -> !f.isEmpty()).count();
        redirectAttributes.addFlashAttribute("successMessage",
                "Received " + fileCount + " CV(s) for analysis. Processing coming soon.");

        // TODO: hand off to screening service
        return "redirect:/";
    }

    private void addFormConstants(Model model) {
        model.addAttribute("maxCandidates", properties.getMaxCandidates());
        model.addAttribute("maxWords", ScreeningFormValidator.MAX_JOB_DESCRIPTION_WORDS);
    }
}