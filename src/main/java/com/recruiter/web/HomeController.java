package com.recruiter.web;

import com.recruiter.config.RecruitmentProperties;
import com.recruiter.document.CvTextExtractionService;
import com.recruiter.document.DocumentExtractionException;
import com.recruiter.document.ExtractedDocument;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final RecruitmentProperties properties;
    private final CvTextExtractionService cvTextExtractionService;
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

        List<ExtractedDocument> extractedDocuments;
        try {
            extractedDocuments = cvTextExtractionService.extractAll(screeningForm.getCvFiles());
        } catch (DocumentExtractionException ex) {
            bindingResult.rejectValue("cvFiles", "Extraction", ex.getMessage());
            addFormConstants(model);
            return "index";
        }

        int fileCount = extractedDocuments.size();
        redirectAttributes.addFlashAttribute("successMessage",
                "Received " + fileCount + " CV(s) for analysis. Text extraction complete.");

        // TODO: hand off extractedDocuments and screeningForm to screening service
        return "redirect:/";
    }

    private void addFormConstants(Model model) {
        model.addAttribute("maxCandidates", properties.getMaxCandidates());
        model.addAttribute("maxWords", properties.getMaxJobDescriptionWords());
    }
}
