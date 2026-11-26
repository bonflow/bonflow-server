package ch.bonflow.ui.admin;

import java.math.BigDecimal;
import java.util.stream.Collectors;

import ch.bonflow.masterdata.Article;
import ch.bonflow.masterdata.ArticleCategory;
import ch.bonflow.masterdata.ArticleCategoryRepository;
import ch.bonflow.masterdata.ArticleRepository;
import ch.bonflow.masterdata.ModifierGroup;
import ch.bonflow.masterdata.ModifierGroupRepository;
import ch.bonflow.masterdata.Station;
import ch.bonflow.masterdata.StationRepository;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "admin/articles", layout = AdminLayout.class)
@PageTitle("Artikel | BonFlow Admin")
@RolesAllowed({"ADMIN", "MANAGER"})
public class ArticlesView extends CrudView<Article> {

    private final ArticleCategoryRepository categories;
    private final StationRepository stations;
    private final ModifierGroupRepository modifierGroups;

    public ArticlesView(ArticleRepository articles, ArticleCategoryRepository categories,
            StationRepository stations, ModifierGroupRepository modifierGroups) {
        super("Artikel", articles, Article::new);
        this.categories = categories;
        this.stations = stations;
        this.modifierGroups = modifierGroups;
        init();
    }

    @Override
    protected void configureGrid() {
        grid.addColumn(Article::getName).setHeader("Name").setSortable(true);
        grid.addColumn(Article::getShortName).setHeader("Kurzname");
        grid.addColumn(a -> a.getCategory().getName()).setHeader("Kategorie").setSortable(true);
        grid.addColumn(a -> a.resolveStation() == null ? "–" : a.resolveStation().getName())
                .setHeader("Station");
        grid.addColumn(Article::getPrice).setHeader("Preis");
        grid.addColumn(a -> a.isActive() ? "ja" : "nein").setHeader("Aktiv");
    }

    @Override
    protected void buildForm(FormLayout form) {
        TextField name = new TextField("Name");
        TextField shortName = new TextField("Kurzname (für Bons)");
        ComboBox<ArticleCategory> category = new ComboBox<>("Kategorie");
        category.setItems(categories.findAllByOrderBySortOrderAscNameAsc());
        category.setItemLabelGenerator(ArticleCategory::getName);
        BigDecimalField price = new BigDecimalField("Preis (nur Information)");
        ComboBox<Station> override = new ComboBox<>("Station überschreiben");
        override.setItems(stations.findAll());
        override.setItemLabelGenerator(Station::getName);
        override.setClearButtonVisible(true);
        MultiSelectComboBox<ModifierGroup> groups = new MultiSelectComboBox<>("Modifier-Gruppen");
        groups.setItems(modifierGroups.findAll());
        groups.setItemLabelGenerator(ModifierGroup::getName);
        Checkbox active = new Checkbox("Aktiv (ausverkauft = deaktivieren)");

        binder.forField(name).asRequired("Name erforderlich").bind(Article::getName, Article::setName);
        binder.forField(shortName).bind(Article::getShortName, Article::setShortName);
        binder.forField(category).asRequired("Kategorie erforderlich")
                .bind(Article::getCategory, Article::setCategory);
        binder.forField(price).asRequired("Preis erforderlich")
                .withValidator(p -> p.signum() >= 0, "Preis darf nicht negativ sein")
                .bind(Article::getPrice, (a, v) -> a.setPrice(v == null ? BigDecimal.ZERO : v));
        binder.forField(override).bind(Article::getStationOverride, Article::setStationOverride);
        binder.forField(groups).bind(Article::getModifierGroups, Article::setModifierGroups);
        binder.forField(active).bind(Article::isActive, Article::setActive);
        form.add(name, shortName, category, price, override, groups, active);
    }

    @Override
    protected void beforeSave(Article item) {
        if (item.resolveStation() == null) {
            throw new IllegalArgumentException(
                    "Weder der Artikel noch seine Kategorie hat eine Station: Der Artikel könnte nicht gedruckt werden.");
        }
    }
}
