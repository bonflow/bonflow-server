package ch.bonflow.ordering;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Follow-up submission. {@code submissionNo} is the number the client expects this submission to
 * get (last known submission + 1); a mismatch means the order changed or the request was already
 * applied, and is answered with a conflict instead of creating duplicate tickets.
 */
public record AddItems(
        @NotNull Integer submissionNo,
        @NotEmpty @Size(max = 100) List<@Valid @NotNull NewItem> items) {
}
