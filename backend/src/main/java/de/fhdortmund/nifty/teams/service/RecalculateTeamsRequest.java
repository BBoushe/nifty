package de.fhdortmund.nifty.teams.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** REQ-TB-03: the complete desired placement after a drag and drop; teamNo is 1-based. */
public record RecalculateTeamsRequest(@NotEmpty List<@NotNull @Valid Assignment> assignments) {

    public record Assignment(@NotNull UUID inviteId, @NotNull @Min(1) Integer teamNo) {
    }
}
