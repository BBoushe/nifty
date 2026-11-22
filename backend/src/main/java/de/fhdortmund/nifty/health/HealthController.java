package de.fhdortmund.nifty.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Service availability and metadata endpoints")
public class HealthController {

    private final String applicationName;
    private final String aiProviderMode;

    public HealthController(
            @Value("${spring.application.name:nifty-backend}") String applicationName,
            @Value("${nifty.ai.provider-mode:placeholder}") String aiProviderMode
    ) {
        this.applicationName = applicationName;
        this.aiProviderMode = aiProviderMode;
    }

    @GetMapping
    @Operation(summary = "Check backend API readiness")
    public HealthResponse health() {
        return new HealthResponse(
                "UP",
                applicationName,
                aiProviderMode,
                Instant.now().toString()
        );
    }

    public record HealthResponse(
            String status,
            String service,
            String aiProviderMode,
            String timestamp
    ) {}
}
