package hopsync.desktop.screens.sheets;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import hopsync.desktop.i18n.Messages;
import hopsync.desktop.kit.ButtonStyle;
import hopsync.desktop.kit.Icons;
import hopsync.desktop.kit.LogoIcon;
import hopsync.desktop.kit.Palette;
import hopsync.desktop.kit.SegmentedControl;
import hopsync.desktop.store.GameFolder;
import hopsync.desktop.store.Sheet;
import hopsync.desktop.store.UiAction;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The add-pack wizard. Step 1 checks the link and shows the pack, or, after "Empieza desde cero", names a new
 * pack and picks its Minecraft version and loader; step 2 picks the game folder among the launcher instances
 * found on disk or any other. Fills the window on first run and sits in a sheet later.
 */
public final class AddPackView extends JPanel {
    private final Consumer<UiAction> dispatch;
    private final String demoUrl;
    // The welcome title is the largest text in the app: 25 px over the 13 px UI font
    private final JLabel title = SheetParts.title("", 12);
    private final StepBar steps = new StepBar(2);
    private final JLabel subtitle = new JLabel();
    private final JLabel step = new JLabel();
    private final LinkField link;
    private final JButton demo = new JButton(Messages.get("sheet.wizard.demo"));
    private final JPanel linkStep = SheetParts.column();
    private final JPanel createStep = SheetParts.column();
    private final JTextField newName = new JTextField();
    private final JComboBox<String> minecraft = new JComboBox<>();
    private final SegmentedControl<String> loader;
    private final JPanel versionsNote = SheetParts.column();
    private boolean rendering;
    private final JPanel folderStep = SheetParts.column();
    private final JPanel body = new JPanel(new BorderLayout());
    private final JPanel bottom = SheetParts.column();
    private Sheet.AddPack shown;

    /** @param demoUrl the preset pack link, offered to try HopSync; blank to hide it */
    public AddPackView(Consumer<UiAction> dispatch, String demoUrl) {
        super(new BorderLayout(0, UIScale.scale(18)));
        this.dispatch = dispatch;
        this.demoUrl = demoUrl;
        setOpaque(false);
        link = new LinkField(dispatch, new UiAction.WizardNext());
        subtitle.setForeground(Palette.textSecondary());
        step.putClientProperty(FlatClientProperties.STYLE, "font: semibold -1; foreground: $HopSync.textFaint");
        body.setOpaque(false);
        // Built once: rebuilding it while the player types would take the focus away from the field
        ButtonStyle.apply(demo, ButtonStyle.QUIET, ButtonStyle.SMALL);
        demo.addActionListener(e -> {
            dispatch.accept(new UiAction.SetLinkUrl(demoUrl));
            dispatch.accept(new UiAction.CheckLink());
        });
        SheetParts.stack(linkStep, 8, SheetParts.label(Messages.get("sheet.link.label")), link, demo);

        loader = new SegmentedControl<>(Sheet.Creation.LOADERS, picked -> edit(newName.getText(), selectedVersion(), picked));
        Sheet.Creation.LOADERS.forEach(id -> loader.setLabel(id, Messages.get("loader." + id)));
        buildCreateStep();

        JPanel header = SheetParts.column();
        SheetParts.stack(header, 0, new JLabel(new LogoIcon(56, LogoIcon.onDark())));
        SheetParts.stack(header, 22, steps);
        SheetParts.stack(header, 6, step);
        SheetParts.stack(header, 4, title, subtitle);
        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    /** Name, then Minecraft version beside the loader, then a hint; the versions come from the mod catalog. */
    private void buildCreateStep() {
        newName.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Messages.get("sheet.create.namePlaceholder"));
        newName.getAccessibleContext().setAccessibleName(Messages.get("sheet.create.name"));
        newName.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                changed();
            }

            private void changed() {
                edit(newName.getText(), selectedVersion(), shownLoader());
            }
        });
        newName.addActionListener(e -> dispatch.accept(new UiAction.WizardNext()));
        minecraft.getAccessibleContext().setAccessibleName(Messages.get("sheet.create.minecraft"));
        minecraft.addActionListener(e -> edit(newName.getText(), selectedVersion(), shownLoader()));
        minecraft.setPrototypeDisplayValue("1.21.10 ");

        JPanel versionColumn = SheetParts.column();
        SheetParts.stack(versionColumn, 0, SheetParts.label(Messages.get("sheet.create.minecraft")));
        SheetParts.stack(versionColumn, 8, minecraft);
        JPanel loaderColumn = SheetParts.column();
        SheetParts.stack(loaderColumn, 0, SheetParts.label(Messages.get("sheet.create.loader")));
        SheetParts.stack(loaderColumn, 8, loader);
        JPanel choices = new JPanel(new BorderLayout(UIScale.scale(14), 0));
        choices.setOpaque(false);
        choices.add(versionColumn, BorderLayout.WEST);
        choices.add(loaderColumn, BorderLayout.CENTER);

        SheetParts.stack(createStep, 8, SheetParts.label(Messages.get("sheet.create.name")), newName);
        SheetParts.stack(createStep, 16, choices);
        SheetParts.stack(createStep, 8, versionsNote);
    }

    private String selectedVersion() {
        return (String) minecraft.getSelectedItem();
    }

    private String shownLoader() {
        return shown == null || shown.creation() == null ? Sheet.Creation.LOADERS.getFirst() : shown.creation().loader();
    }

    private void edit(String name, String version, String pickedLoader) {
        if (!rendering) dispatch.accept(new UiAction.EditNewPack(name, version, pickedLoader));
    }

    public void render(Sheet.AddPack sheet) {
        if (sheet.equals(shown)) return;
        rendering = true;
        try {
            show(sheet);
        } finally {
            rendering = false;
        }
    }

    private void show(Sheet.AddPack sheet) {
        boolean creating = sheet.creation() != null;
        boolean bodyChanged = shown == null || shown.step() != sheet.step()
            || (shown.creation() != null) != creating;
        shown = sheet;
        step.setText(Messages.get("sheet.wizard.step", sheet.step()));
        steps.setStep(sheet.step());
        title.setText(Messages.get(creating ? "sheet.wizard.createTitle"
            : sheet.firstRun() ? "sheet.wizard.firstRunTitle" : "sheet.wizard.title"));
        subtitle.setText(Messages.get(sheet.step() == 1
            ? creating ? "sheet.wizard.createSubtitle" : "sheet.wizard.linkSubtitle"
            : creating ? "sheet.wizard.createFolderSubtitle" : "sheet.wizard.folderSubtitle"));

        if (sheet.step() == 1 && creating) {
            showCreation(sheet.creation());
        } else if (sheet.step() == 1) {
            link.render(sheet.url(), sheet.link(), Messages.get("sheet.link.hint"));
            demo.setVisible(demoUrl != null && !demoUrl.isBlank() && !demoUrl.equals(sheet.url()));
        } else {
            folderStep.removeAll();
            SheetParts.stack(folderStep, 8, SheetParts.label(Messages.get("sheet.folder.label")));
            folders(sheet).forEach(part -> SheetParts.stack(folderStep, 8, part));
        }
        if (bodyChanged) {
            body.removeAll();
            body.add(sheet.step() == 2 ? folderStep : creating ? createStep : linkStep, BorderLayout.NORTH);
        }

        bottom.removeAll();
        if (sheet.error() != null) {
            SheetParts.stack(bottom, 10, SheetParts.notice(
                Messages.get("sheet.wizard.saveFailed", Messages.failure(sheet.error())), Palette.error()));
        }
        SheetParts.stack(bottom, 12, footer(sheet));
        revalidate();
        repaint();
        if (bodyChanged && sheet.step() == 1) {
            (creating ? newName : link.field()).requestFocusInWindow();
        }
    }

    /** The fields keep what the player typed; only a different value from the state is written back. */
    private void showCreation(Sheet.Creation creation) {
        if (!newName.getText().equals(creation.name())) {
            newName.setText(creation.name());
        }
        List<String> versions = creation.versions() == null ? List.of() : creation.versions();
        if (!versions.equals(itemsOf(minecraft))) {
            minecraft.removeAllItems();
            versions.forEach(minecraft::addItem);
        }
        if (creation.minecraft() != null && !creation.minecraft().equals(minecraft.getSelectedItem())) {
            minecraft.setSelectedItem(creation.minecraft());
        }
        minecraft.setEnabled(!versions.isEmpty());
        loader.setSelected(creation.loader());

        versionsNote.removeAll();
        if (creation.versionsError() != null) {
            SheetParts.stack(versionsNote, 0, SheetParts.notice(Messages.get("sheet.create.versionsFailed",
                Messages.failure(creation.versionsError())), Palette.error()));
            JButton retry = SheetParts.button("sheet.create.retry", ButtonStyle.SECONDARY, dispatch,
                new UiAction.StartFromScratch());
            ButtonStyle.apply(retry, ButtonStyle.SECONDARY, ButtonStyle.SMALL);
            SheetParts.stack(versionsNote, 8, retry);
        } else if (creation.versions() == null) {
            SheetParts.stack(versionsNote, 0, SheetParts.hint(Messages.get("sheet.create.loadingVersions")));
        } else {
            SheetParts.stack(versionsNote, 0, SheetParts.hint(Messages.get("sheet.create.hint")));
        }
    }

    private static List<String> itemsOf(JComboBox<String> box) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < box.getItemCount(); i++) items.add(box.getItemAt(i));
        return items;
    }

    private List<JComponent> folders(Sheet.AddPack sheet) {
        List<JComponent> parts = new ArrayList<>();
        if (sheet.folders() == null) {
            parts.add(SheetParts.hint(Messages.get("sheet.folder.searching")));
        } else if (sheet.folders().isEmpty()) {
            parts.add(SheetParts.hint(Messages.get("sheet.folder.none")));
        }
        List<GameFolder> shownFolders = new ArrayList<>(sheet.folders() == null ? List.of() : sheet.folders());
        GameFolder chosen = sheet.chosen();
        // A folder picked by hand gets its own card under the ones found
        if (chosen != null && shownFolders.stream().noneMatch(folder -> folder.path().equals(chosen.path()))) {
            shownFolders.add(chosen);
        }
        for (GameFolder folder : shownFolders) {
            boolean selected = chosen != null && chosen.path().equals(folder.path());
            parts.add(new FolderCard(selected ? chosen : folder, selected,
                () -> dispatch.accept(new UiAction.ChooseFolder(folder.path()))));
        }

        JButton other = new JButton(Messages.get("sheet.folder.other"), Icons.get(Icons.Glyph.FOLDER));
        ButtonStyle.apply(other, ButtonStyle.SECONDARY, ButtonStyle.SMALL);
        other.addActionListener(e -> SheetParts.chooseFolder(this, chosen == null ? null : chosen.path())
            .ifPresent(path -> dispatch.accept(new UiAction.ChooseFolder(path))));
        parts.add(other);

        if (chosen != null && !chosen.isFree()) {
            parts.add(SheetParts.notice(Messages.get("sheet.folder.taken", chosen.usedBy()), Palette.error()));
            boolean allTaken = !shownFolders.isEmpty() && shownFolders.stream().noneMatch(GameFolder::isFree);
            if (allTaken) {
                parts.add(SheetParts.hint(Messages.get("sheet.folder.allTaken")));
            }
        } else if (chosen != null && chosen.jarCount() > 0) {
            parts.add(SheetParts.notice(Messages.get("sheet.folder.existingMods", chosen.jarCount()),
                Palette.update()));
        } else {
            parts.add(SheetParts.hint(Messages.get("sheet.folder.hint")));
        }
        return parts;
    }

    /** The other way to add a pack on the left of step 1; the buttons on the right. */
    private JPanel footer(Sheet.AddPack sheet) {
        boolean creating = sheet.creation() != null;
        List<JButton> buttons = new ArrayList<>();
        if (sheet.step() == 1) {
            if (!sheet.firstRun()) {
                buttons.add(SheetParts.button("sheet.cancel", ButtonStyle.SECONDARY, dispatch, new UiAction.CloseSheet()));
            }
            JButton next = SheetParts.button("sheet.wizard.next", ButtonStyle.PRIMARY, dispatch,
                new UiAction.WizardNext());
            next.setEnabled(sheet.canContinue());
            buttons.add(next);
        } else {
            buttons.add(SheetParts.button("sheet.wizard.back", ButtonStyle.SECONDARY, dispatch,
                new UiAction.WizardBack()));
            String finishKey = creating ? "sheet.wizard.create"
                : sheet.firstRun() ? "sheet.wizard.start" : "sheet.wizard.finish";
            JButton finish = SheetParts.button(finishKey, ButtonStyle.PRIMARY, dispatch, new UiAction.FinishAddPack());
            finish.setEnabled(sheet.chosen() != null && sheet.chosen().isFree());
            buttons.add(finish);
        }
        JPanel footer = new JPanel(new BorderLayout(UIScale.scale(12), 0));
        footer.setOpaque(false);
        if (sheet.step() == 1) {
            // Centered on the buttons' line
            JPanel west = new JPanel(new GridBagLayout());
            west.setOpaque(false);
            footer.add(west, BorderLayout.WEST);
            west.add(creating
                ? SheetParts.textLink(Messages.get("sheet.wizard.haveLink"), Messages.get("sheet.wizard.useLink"),
                    () -> dispatch.accept(new UiAction.UseLink()))
                : SheetParts.textLink(Messages.get("sheet.wizard.noLink"), Messages.get("sheet.wizard.fromScratch"),
                    () -> dispatch.accept(new UiAction.StartFromScratch())));
        }
        footer.add(SheetParts.footer(buttons.toArray(JButton[]::new)), BorderLayout.EAST);
        return footer;
    }
}
