package ch.bonflow.masterdata;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the waiter app needs to take orders offline-capable: one consistent view of the
 * master data, containing only active tables and articles.
 */
public record MasterDataSnapshot(
        List<AreaDto> areas,
        List<TableDto> tables,
        List<StationDto> stations,
        List<CategoryDto> categories,
        List<ArticleDto> articles,
        List<ModifierGroupDto> modifierGroups) {

    public record AreaDto(long id, String name) {
    }

    public record TableDto(long id, long areaId, String label) {
    }

    public record StationDto(long id, String name) {
    }

    public record CategoryDto(long id, String name, int sortOrder, Long parentId) {
    }

    /** {@code stationId} is already resolved: article override, else the category's default (may be null). */
    public record ArticleDto(long id, String name, String shortName, long categoryId, BigDecimal price,
            Long stationId, List<Long> modifierGroupIds) {
    }

    public record ModifierGroupDto(long id, String name, boolean multiSelect, List<ModifierDto> modifiers) {
    }

    public record ModifierDto(long id, String name) {
    }
}
