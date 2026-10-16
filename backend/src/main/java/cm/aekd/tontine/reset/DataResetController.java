package cm.aekd.tontine.reset;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Réinitialisation des données : super administrateur uniquement (docs/decisions.md n° 63). */
@RestController
@RequestMapping("/api/settings/data-reset")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class DataResetController {

    private final DataResetService service;

    public DataResetController(DataResetService service) {
        this.service = service;
    }

    @GetMapping
    public List<DataResetDtos.CategoryPreview> preview() {
        return service.preview();
    }

    @PostMapping
    public DataResetDtos.ResetResult reset(@Valid @RequestBody DataResetDtos.ResetRequest request) {
        return service.reset(request);
    }
}
