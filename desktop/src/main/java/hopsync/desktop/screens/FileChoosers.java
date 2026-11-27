package hopsync.desktop.screens;

import hopsync.desktop.i18n.Messages;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** File pickers the screens open from a button's handler, never while rendering. */
public final class FileChoosers {

    private FileChoosers() {
    }

    /** The mod jars the player has, to add to a pack they created; empty when they cancel. */
    public static List<Path> chooseJars(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Messages.get("addMods.jarChooserTitle"));
        chooser.setFileFilter(new FileNameExtensionFilter(Messages.get("addMods.jarFilter"), "jar"));
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setMultiSelectionEnabled(true);
        return chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION
            ? Arrays.stream(chooser.getSelectedFiles()).map(File::toPath).toList() : List.of();
    }
}
