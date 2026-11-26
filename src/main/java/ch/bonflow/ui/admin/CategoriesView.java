package ch.bonflow.ui.admin;

import java.util.List;

import ch.bonflow.masterdata.MasterDataTransferService;
import ch.bonflow.masterdata.TransferSection;
import ch.bonflow.masterdata.ArticleCategory;
import ch.bonflow.masterdata.ArticleCategoryRepository;
import ch.bonflow.masterdata.Station;
import ch.bonflow.masterdata.StationRepository;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "admin/categories", layout = AdminLayout.class)
@PageTitle("Kategorien | BonFlow Admin")
@RolesAllowed({"ADMIN", "MANAGER"})
public class CategoriesView extends CrudView<ArticleCategory> {

    private final ArticleCategoryRepository categories;
    private final StationRepository stations;

    public CategoriesView(ArticleCategoryRepository categories, StationRepository stations,
            MasterDataTransferService transfer) {
        super("Kategorien", categories, ArticleCategory::new);
        this.categories = categories;
        this.stations = stations;
        enableTransfer(transfer, TransferSection.CATEGORIES);
        init();
    }

    @Override
    protected List<ArticleCategory> loadItems() {
        return categories.findAllByOrderBySortOrderAscNameAsc();
    }

    @Override
    protected void configureGrid() {
        grid.addColumn(ArticleCategory::getName).setHeader("Name");
        grid.addComponentColumn(c -> ColorField.dot(c.getColor())).setHeader("Farbe").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(ArticleCategory::getSortOrder).setHeader("Reihenfolge");
        grid.addColumn(c -> c.getDefaultStation() == null ? "" : c.getDefaultStation().getName())
                .setHeader("Standard-Station");
        grid.addColumn(c -> c.getParent() == null ? "" : c.getParent().getName())
                .setHeader("Übergeordnet");
    }

    @Override
    protected void buildForm(FormLayout form) {
        TextField name = new TextField("Name");
        IntegerField sortOrder = new IntegerField("Reihenfolge");
        ComboBox<Station> station = new ComboBox<>("Standard-Station");
        station.setItems(stations.findAll());
        station.setItemLabelGenerator(Station::getName);
        station.setClearButtonVisible(true);
        ComboBox<ArticleCategory> parent = new ComboBox<>("Übergeordnete Kategorie");
        parent.setItems(categories.findAll());
        parent.setItemLabelGenerator(ArticleCategory::getName);
        parent.setClearButtonVisible(true);

        binder.forField(name).asRequired("Name erforderlich")
                .bind(ArticleCategory::getName, ArticleCategory::setName);
        binder.forField(sortOrder).asRequired("Reihenfolge erforderlich")
                .bind(ArticleCategory::getSortOrder, (c, v) -> c.setSortOrder(v));
        binder.forField(station)
                .bind(ArticleCategory::getDefaultStation, ArticleCategory::setDefaultStation);
        binder.forField(parent).bind(ArticleCategory::getParent, ArticleCategory::setParent);
        ColorField color = new ColorField("Farbe (leer = Farbe der Oberkategorie)");
        binder.forField(color).bind(ArticleCategory::getColor, ArticleCategory::setColor);
        form.add(name, sortOrder, station, parent, color);
    }

    @Override
    protected void beforeSave(ArticleCategory item) {
        if (item.getParent() != null && item.getParent().equals(item)) {
            throw new IllegalArgumentException("Eine Kategorie kann nicht ihre eigene Oberkategorie sein.");
        }
    }
}
