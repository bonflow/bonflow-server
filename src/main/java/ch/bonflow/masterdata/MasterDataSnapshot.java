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

    /** {@code color} is {@code #RRGGBB} or null. */
    public record AreaDto(long id, String name, String color) {
    }

    /** {@code color} is already resolved: the table's own, else its area's (may be null). */
    public record TableDto(long id, long areaId, String label, String color) {
    }

    public record StationDto(long id, String name) {
    }

    /** {@code color} is already resolved: the category's own, else the nearest parent's (may be null). */
    public record CategoryDto(long id, String name, int sortOrder, Long parentId, String color) {
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
