package cm.aekd.tontine.sms;

import cm.aekd.tontine.sms.SmsSettingsDtos.SettingsRequest;
import cm.aekd.tontine.sms.SmsSettingsDtos.SettingsResponse;
import cm.aekd.tontine.sms.SmsSettingsDtos.TestRequest;
import cm.aekd.tontine.sms.SmsSettingsDtos.TestResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Paramètres SMS : super administrateur uniquement (docs/decisions.md n° 48 et 56). */
@RestController
@RequestMapping("/api/settings/sms")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SmsSettingsController {

    private final SmsSettingsService service;

    public SmsSettingsController(SmsSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public SettingsResponse get() {
        return service.get();
    }

    @PutMapping
    public SettingsResponse update(@Valid @RequestBody SettingsRequest request) {
        return service.update(request);
    }

    @PostMapping("/test")
    public TestResponse sendTest(@Valid @RequestBody TestRequest request) {
        return service.sendTest(request);
    }
}
