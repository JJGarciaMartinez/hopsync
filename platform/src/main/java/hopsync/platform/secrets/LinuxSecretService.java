package hopsync.platform.secrets;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.PointerByReference;
import hopsync.core.port.SecretStore;

import java.io.IOException;
import java.util.Optional;

/**
 * The freedesktop Secret Service through libsecret (GNOME Keyring, KWallet), as passwords with the attributes
 * service = "HopSync" and account = the key, in the player's default collection.
 */
final class LinuxSecretService implements SecretStore {
    private static final int ATTRIBUTE_STRING = 0;

    /** SecretSchemaAttribute. */
    @Structure.FieldOrder({"name", "type"})
    public static class Attribute extends Structure {
        public String name;
        public int type;
    }

    /** SecretSchema: a name, flags, up to 32 attributes and reserved fields libsecret keeps for itself. */
    @Structure.FieldOrder({"name", "flags", "attributes", "reserved", "reserved1", "reserved2", "reserved3",
        "reserved4", "reserved5", "reserved6", "reserved7"})
    public static class Schema extends Structure {
        public String name;
        public int flags;
        public Attribute[] attributes = (Attribute[]) new Attribute().toArray(32);
        public int reserved;
        public Pointer reserved1;
        public Pointer reserved2;
        public Pointer reserved3;
        public Pointer reserved4;
        public Pointer reserved5;
        public Pointer reserved6;
        public Pointer reserved7;
    }

    private interface LibSecret extends Library {
        boolean secret_password_store_sync(Schema schema, String collection, String label, String password,
                                           Pointer cancellable, PointerByReference error, Object... attributes);

        Pointer secret_password_lookup_sync(Schema schema, Pointer cancellable, PointerByReference error,
                                            Object... attributes);

        boolean secret_password_clear_sync(Schema schema, Pointer cancellable, PointerByReference error,
                                           Object... attributes);

        void secret_password_free(Pointer password);
    }

    private interface Glib extends Library {
        void g_error_free(Pointer error);
    }

    private final LibSecret secret = Native.load("secret-1", LibSecret.class);
    private final Glib glib = Native.load("glib-2.0", Glib.class);
    private final Schema schema = schema();

    private static Schema schema() {
        Schema schema = new Schema();
        schema.name = "net.hopsync.Secret";
        schema.attributes[0].name = "service";
        schema.attributes[0].type = ATTRIBUTE_STRING;
        schema.attributes[1].name = "account";
        schema.attributes[1].type = ATTRIBUTE_STRING;
        schema.write();
        return schema;
    }

    @Override
    public boolean persistent() {
        return true;
    }

    @Override
    public synchronized Optional<String> read(String key) throws IOException {
        PointerByReference error = new PointerByReference();
        Pointer password = secret.secret_password_lookup_sync(schema, null, error, "service", SystemKeychain.SERVICE,
            "account", key, null);
        check(error, "read");
        if (password == null) return Optional.empty();
        try {
            return Optional.of(password.getString(0, "UTF-8"));
        } finally {
            secret.secret_password_free(password);
        }
    }

    @Override
    public synchronized void write(String key, String value) throws IOException {
        PointerByReference error = new PointerByReference();
        secret.secret_password_store_sync(schema, "default", SystemKeychain.SERVICE + " · " + key, value, null, error,
            "service", SystemKeychain.SERVICE, "account", key, null);
        check(error, "write");
    }

    @Override
    public synchronized void delete(String key) throws IOException {
        PointerByReference error = new PointerByReference();
        secret.secret_password_clear_sync(schema, null, error, "service", SystemKeychain.SERVICE, "account", key, null);
        check(error, "delete");
    }

    /** A GError from libsecret (no Secret Service running, the collection is locked); freed, then thrown. */
    private void check(PointerByReference error, String what) throws IOException {
        Pointer failure = error.getValue();
        if (failure == null) return;
        // GError: guint32 domain, gint code, gchar *message
        String message = failure.getPointer(8).getString(0, "UTF-8");
        glib.g_error_free(failure);
        throw new IOException("Secret Service " + what + " failed: " + message);
    }
}
