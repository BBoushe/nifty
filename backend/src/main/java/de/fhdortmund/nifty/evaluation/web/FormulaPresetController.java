package de.fhdortmund.nifty.evaluation.web;

import de.fhdortmund.nifty.evaluation.service.EvaluationDtos;
import de.fhdortmund.nifty.evaluation.service.FormulaPresetService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REQ-FE-02, REQ-FE-05 / Decision 2.19: PROFESSOR-only formula preset listing, immutable
 * formula version apply, and what-if cohort preview.
 */
@RestController
@RequestMapping("/api/v1/ranking")
@PreAuthorize("hasRole('PROFESSOR')")
class FormulaPresetController {

    private final FormulaPresetService formulaPresetService;

    FormulaPresetController(FormulaPresetService formulaPresetService) {
        this.formulaPresetService = formulaPresetService;
    }

    @GetMapping("/formula-presets")
    List<EvaluationDtos.FormulaPresetInfo> listPresets() {
        return formulaPresetService.listPresets();
    }

    @PostMapping("/configs/{configId}/formula")
    EvaluationDtos.FormulaVersionResponse applyPreset(
            @PathVariable UUID configId,
            @RequestBody FormulaPresetService.FormulaPresetRequest request) {
        return formulaPresetService.applyPreset(configId, request);
    }

    @PostMapping("/configs/{configId}/formula/preview")
    EvaluationDtos.FormulaPreviewResponse previewPreset(
            @PathVariable UUID configId,
            @RequestBody FormulaPresetService.FormulaPresetRequest request) {
        return formulaPresetService.previewPreset(configId, request);
    }
}
