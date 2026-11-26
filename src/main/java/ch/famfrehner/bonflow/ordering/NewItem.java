package ch.famfrehner.bonflow.ordering;

import java.util.Set;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One line of a submission. */
public record NewItem(
        @NotNull Long articleId,
        @Min(1) @Max(99) int quantity,
        @Size(max = 500) String note,
        @Min(1) @Max(9) Integer course,
        Set<Long> modifierIds) {
}
