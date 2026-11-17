package hopsync.desktop.screens.mods;

import hopsync.desktop.i18n.Messages;
import hopsync.desktop.kit.Icons;
import hopsync.desktop.kit.Palette;
import hopsync.desktop.store.ModVersions;
import hopsync.desktop.store.UiAction;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.util.function.Consumer;

/**
 * "Cambiar versión": a menu under the row's button with the mod's versions for the pack, newest first, saying
 * which one the pack has and which is the newest. It opens at once and fills in as the versions arrive.
 */
final class VersionMenu extends JPopupMenu {
    private final Consumer<UiAction> dispatch;
    private String packId;
    private String slug;
    private ModVersions shown;

    VersionMenu(Consumer<UiAction> dispatch) {
        this.dispatch = dispatch;
        addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                if (packId != null) dispatch.accept(new UiAction.CloseModVersions());
                packId = null;
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
    }

    void open(JComponent invoker, int x, int y, String forPack, String forSlug) {
        packId = forPack;
        slug = forSlug;
        shown = null;
        fill(null);
        show(invoker, x, y);
    }

    /** Fills the open menu with the versions for its mod; anything else is ignored. */
    void render(ModVersions versions) {
        if (!isVisible() || packId == null || versions == null || versions.equals(shown)
                || !versions.packId().equals(packId) || !versions.slug().equals(slug)) {
            return;
        }
        shown = versions;
        fill(versions);
        pack();
    }

    private void fill(ModVersions versions) {
        removeAll();
        if (versions == null || versions.choices() == null) {
            add(note(Messages.get("mods.versions.loading")));
        } else if (versions.error() != null) {
            add(note(Messages.get("mods.versions.failed", Messages.failure(versions.error()))));
        } else if (versions.choices().size() <= 1) {
            add(note(Messages.get("mods.versions.none")));
        } else {
            for (int i = 0; i < versions.choices().size(); i++) {
                add(choice(versions.choices().get(i), i == 0));
            }
        }
        revalidate();
    }

    private JMenuItem choice(ModVersions.Choice choice, boolean newest) {
        String note = choice.current() ? Messages.get("mods.versions.current")
            : newest ? Messages.get("mods.versions.newest")
            : choice.release() ? null : Messages.get("mods.versions.beta");
        JMenuItem item = new JMenuItem(note == null ? choice.number() : choice.number() + "  ·  " + note,
            choice.current() ? Icons.get(Icons.Glyph.CHECK, 13, Palette.mine()) : null);
        if (!choice.current()) {
            String forPack = packId;
            String forSlug = slug;
            item.addActionListener(e -> {
                packId = null;
                dispatch.accept(new UiAction.ChangeModVersion(forPack, forSlug, choice.id()));
            });
        }
        return item;
    }

    private static JMenuItem note(String text) {
        JMenuItem item = new JMenuItem(text);
        item.setEnabled(false);
        return item;
    }
}
