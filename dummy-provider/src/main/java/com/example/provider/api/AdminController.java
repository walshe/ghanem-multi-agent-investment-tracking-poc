package com.example.provider.api;

import com.example.provider.api.ApiModels.SendCallbackRequest;
import com.example.provider.api.ApiModels.SendCallbackResult;
import com.example.provider.callback.ManualCallbackService;
import com.example.provider.chaos.Chaos;
import com.example.provider.chaos.ChaosSettings;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints for driving the backend into awkward situations. */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final ChaosSettings chaosSettings;
    private final ManualCallbackService manualCallbackService;

    public AdminController(ChaosSettings chaosSettings, ManualCallbackService manualCallbackService) {
        this.chaosSettings = chaosSettings;
        this.manualCallbackService = manualCallbackService;
    }

    @GetMapping("/chaos")
    public Chaos getChaos() {
        return chaosSettings.get();
    }

    @PutMapping("/chaos")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setChaos(@Valid @RequestBody Chaos chaos) {
        chaosSettings.set(chaos);
    }

    @PostMapping("/send-callback")
    public SendCallbackResult sendCallback(@Valid @RequestBody SendCallbackRequest request) {
        return new SendCallbackResult(manualCallbackService.send(
                request.paymentId(),
                request.status(),
                request.eventId(),
                request.amountMinor(),
                request.validSignature() == null || request.validSignature(),
                request.copies() == null ? 1 : request.copies()));
    }
}
