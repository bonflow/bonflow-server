package ch.bonflow.ui.admin;

import java.util.Arrays;
import java.util.List;

import ch.bonflow.masterdata.ModifierGroup;
import ch.bonflow.masterdata.ModifierGroupRepository;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "admin/modifiers", layout = AdminLayout.class)
@PageTitle("Modifier | BonFlow Admin")
@RolesAllowed({"ADMIN", "MANAGER"})
public class ModifierGroupsView extends CrudView<ModifierGroup> {

    public ModifierGroupsView(ModifierGroupRepository groups) {
        super("Modifier-Gruppen", groups, ModifierGroup::new);
        init();
    }

    @Override
    protected void configureGrid() {
        grid.addColumn(ModifierGroup::getName).setHeader("Name").setSortable(true);
        grid.addColumn(g -> g.isMultiSelect() ? "mehrere" : "eine").setHeader("Auswahl");
        grid.addColumn(g -> String.join(", ", g.getModifierNames())).setHeader("Optionen");
    }

    @Override
    protected void buildForm(FormLayout form) {
        TextField name = new TextField("Name (z.B. Garstufe, Extras)");
        Checkbox multiSelect = new Checkbox("Mehrfachauswahl");
        TextArea options = new TextArea("Optionen (eine pro Zeile)");
        binder.forField(name).asRequired("Name erforderlich")
                .bind(ModifierGroup::getName, ModifierGroup::setName);
        binder.forField(multiSelect).bind(ModifierGroup::isMultiSelect, ModifierGroup::setMultiSelect);
        binder.forField(options)
                .bind(g -> String.join("\n", g.getModifierNames()),
                        (g, text) -> g.setModifierNames(parse(text)));
        form.add(name, multiSelect, options);
    }

    private static List<String> parse(String text) {
        if (text == null) {
            return List.of();
        }
        return Arrays.stream(text.split("\\R")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
