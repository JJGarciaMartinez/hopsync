package hopsync.desktop.screens;

import hopsync.desktop.i18n.Messages;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.nio.file.Path;
import java.util.Optional;

/** File pickers the screens open from a button's handler, never while rendering. */
public final class FileChoosers {

    private FileChoosers() {
    }

    /** A mod jar the player has, to add to a pack they created. */
    public static Optional<Path> chooseJar(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Messages.get("addMods.jarChooserTitle"));
        chooser.setFileFilter(new FileNameExtensionFilter(Messages.get("addMods.jarFilter"), "jar"));
        chooser.setAcceptAllFileFilterUsed(false);
        return chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null
            ? Optional.of(chooser.getSelectedFile().toPath()) : Optional.empty();
    }
}
