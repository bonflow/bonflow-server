package ch.bonflow.masterdata;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasterDataService {

    private final AreaRepository areas;
    private final DiningTableRepository tables;
    private final StationRepository stations;
    private final ArticleCategoryRepository categories;
    private final ArticleRepository articles;
    private final ModifierGroupRepository modifierGroups;

    MasterDataService(AreaRepository areas, DiningTableRepository tables, StationRepository stations,
            ArticleCategoryRepository categories, ArticleRepository articles,
            ModifierGroupRepository modifierGroups) {
        this.areas = areas;
        this.tables = tables;
        this.stations = stations;
        this.categories = categories;
        this.articles = articles;
        this.modifierGroups = modifierGroups;
    }

    @Transactional(readOnly = true)
    public MasterDataSnapshot snapshot() {
        return new MasterDataSnapshot(
                areas.findAll().stream()
                        .sorted(Comparator.comparing(Area::getName))
                        .map(a -> new MasterDataSnapshot.AreaDto(a.getId(), a.getName(), a.getColor()))
                        .toList(),
                tables.findAll().stream()
                        .filter(DiningTable::isActive)
                        .sorted(Comparator.comparing(DiningTable::getLabel))
                        .map(t -> new MasterDataSnapshot.TableDto(t.getId(), t.getArea().getId(), t.getLabel(),
                                t.getColor() != null ? t.getColor() : t.getArea().getColor()))
                        .toList(),
                stations.findAll().stream()
                        .sorted(Comparator.comparing(Station::getName))
                        .map(s -> new MasterDataSnapshot.StationDto(s.getId(), s.getName()))
                        .toList(),
                categories.findAllByOrderBySortOrderAscNameAsc().stream()
                        .map(c -> new MasterDataSnapshot.CategoryDto(c.getId(), c.getName(), c.getSortOrder(),
                                c.getParent() == null ? null : c.getParent().getId(), categoryColor(c)))
                        .toList(),
                articles.findAll().stream()
                        .filter(Article::isActive)
                        .sorted(Comparator.comparing(Article::getName))
                        .map(a -> {
                            Station station = a.resolveStation();
                            return new MasterDataSnapshot.ArticleDto(a.getId(), a.getName(), a.getShortName(),
                                    a.getCategory().getId(), a.getPrice(),
                                    station == null ? null : station.getId(),
                                    a.getModifierGroups().stream().map(ModifierGroup::getId).sorted().toList());
                        })
                        .toList(),
                modifierGroups.findAll().stream()
                        .sorted(Comparator.comparing(ModifierGroup::getName))
                        .map(g -> new MasterDataSnapshot.ModifierGroupDto(g.getId(), g.getName(), g.isMultiSelect(),
                                g.getModifiers().stream()
                                        .map(m -> new MasterDataSnapshot.ModifierDto(m.getId(), m.getName()))
                                        .toList()))
                        .toList());
    }

    /**
     * Content based version of a snapshot, used as HTTP ETag. Records have a deterministic
     * {@code toString()} over all components, so equal data yields the same version and any change
     * (including changes made while the app was offline) yields a different one.
     */
    public static String versionOf(MasterDataSnapshot snapshot) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(Objects.toString(snapshot).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String categoryColor(ArticleCategory category) {
        for (ArticleCategory c = category; c != null; c = c.getParent()) {
            if (c.getColor() != null) {
                return c.getColor();
            }
        }
        return null;
    }
}
