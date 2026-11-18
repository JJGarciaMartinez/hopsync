package hopsync;

import hopsync.desktop.AppContainer;
import hopsync.desktop.UiStartup;
import hopsync.desktop.config.AppVersion;
import hopsync.desktop.i18n.Messages;
import hopsync.desktop.kit.HopSyncDark;
import hopsync.desktop.logging.Logging;
import hopsync.desktop.logging.UnexpectedErrors;
import hopsync.desktop.ui.DisplayScaling;
import hopsync.platform.settings.SettingsStore;
import hopsync.platform.system.InstanceLock;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;

/**
 * Entry point: sets up logging and display scaling, makes sure only one copy runs, builds the services
 * and opens the launcher window.
 */
public class Main {
    private static final System.Logger LOG = System.getLogger(Main.class.getName());

    // Held for the whole run; the OS releases it when the process exits
    private static InstanceLock instanceLock;

    public static void main(String[] args) {
        SettingsStore settings = SettingsStore.forCurrentUser();
        Path dataDir = settings.getFile().getParent();
        // Before any text is read, so even the "already open" notice uses the player's language
        Messages.use(settings.load().language());
        // Before any AWT class loads, or the scale is ignored
        DisplayScaling.configure();

        // Checked before file logging starts, so a second copy never competes for the log file
        if (!acquireInstanceLock(dataDir)) {
            LOG.log(System.Logger.Level.INFO, "Another HopSync instance is running, exiting");
            SwingUtilities.invokeLater(() -> {
                HopSyncDark.setup();
                JOptionPane.showMessageDialog(null, Messages.get("app.alreadyRunning"), "HopSync",
                    JOptionPane.INFORMATION_MESSAGE);
            });
            return;
        }

        Logging.configure(Logging.folderFor(settings.getFile()));
        UnexpectedErrors errors = new UnexpectedErrors(Clock.systemUTC());
        Thread.setDefaultUncaughtExceptionHandler(errors);
        LOG.log(System.Logger.Level.INFO, "Starting HopSync " + AppVersion.VERSION_APP + " on Java "
            + System.getProperty("java.version") + ", " + System.getProperty("os.name"));
        AppContainer container = AppContainer.forCurrentUser();
        SwingUtilities.invokeLater(() -> UiStartup.start(container, errors));
    }

    /**
     * @return false only when another instance holds the lock; a lock that cannot be created does not
     *         stop the app
     */
    private static boolean acquireInstanceLock(Path dataDir) {
        try {
            Optional<InstanceLock> lock = InstanceLock.tryAcquire(dataDir.resolve("hopsync.lock"));
            lock.ifPresent(held -> instanceLock = held);
            return lock.isPresent();
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not create the instance lock, continuing without it", e);
            return true;
        }
    }
}
