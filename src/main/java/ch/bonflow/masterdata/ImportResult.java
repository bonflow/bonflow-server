package ch.bonflow.masterdata;

import java.util.Map;

/**
 * What an import did.
 *
 * @param counts per imported section how many entries were created and updated
 */
public record ImportResult(Map<TransferSection, Counts> counts) {

    public record Counts(int created, int updated) {
    }
}
