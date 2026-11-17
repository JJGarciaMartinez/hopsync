package hopsync.platform.secrets;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import hopsync.core.port.SecretStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** macOS Keychain, as generic passwords of the service "HopSync" in the player's login keychain. */
final class MacKeychain implements SecretStore {
    private static final int OK = 0;
    private static final int ITEM_NOT_FOUND = -25300;

    private interface Security extends Library {
        int SecKeychainAddGenericPassword(Pointer keychain, int serviceLength, byte[] service, int accountLength,
                                          byte[] account, int passwordLength, byte[] password, Pointer item);

        int SecKeychainFindGenericPassword(Pointer keychains, int serviceLength, byte[] service, int accountLength,
                                           byte[] account, IntByReference passwordLength,
                                           PointerByReference password, PointerByReference item);

        int SecKeychainItemModifyAttributesAndData(Pointer item, Pointer attributes, int length, byte[] data);

        int SecKeychainItemDelete(Pointer item);

        int SecKeychainItemFreeContent(Pointer attributes, Pointer data);
    }

    private interface CoreFoundation extends Library {
        void CFRelease(Pointer object);
    }

    private final Security security = Native.load("Security", Security.class);
    private final CoreFoundation coreFoundation = Native.load("CoreFoundation", CoreFoundation.class);

    @Override
    public boolean persistent() {
        return true;
    }

    @Override
    public synchronized Optional<String> read(String key) throws IOException {
        byte[] service = bytes(SystemKeychain.SERVICE);
        byte[] account = bytes(key);
        IntByReference length = new IntByReference();
        PointerByReference data = new PointerByReference();
        int status = security.SecKeychainFindGenericPassword(null, service.length, service, account.length, account,
            length, data, null);
        if (status == ITEM_NOT_FOUND) return Optional.empty();
        check(status, "read");
        try {
            return Optional.of(new String(data.getValue().getByteArray(0, length.getValue()), StandardCharsets.UTF_8));
        } finally {
            security.SecKeychainItemFreeContent(null, data.getValue());
        }
    }

    @Override
    public synchronized void write(String key, String secret) throws IOException {
        byte[] service = bytes(SystemKeychain.SERVICE);
        byte[] account = bytes(key);
        byte[] password = bytes(secret);
        PointerByReference item = new PointerByReference();
        int status = security.SecKeychainFindGenericPassword(null, service.length, service, account.length, account,
            null, null, item);
        if (status == ITEM_NOT_FOUND) {
            check(security.SecKeychainAddGenericPassword(null, service.length, service, account.length, account,
                password.length, password, null), "add");
            return;
        }
        check(status, "find");
        try {
            check(security.SecKeychainItemModifyAttributesAndData(item.getValue(), null, password.length, password),
                "update");
        } finally {
            coreFoundation.CFRelease(item.getValue());
        }
    }

    @Override
    public synchronized void delete(String key) throws IOException {
        byte[] service = bytes(SystemKeychain.SERVICE);
        byte[] account = bytes(key);
        PointerByReference item = new PointerByReference();
        int status = security.SecKeychainFindGenericPassword(null, service.length, service, account.length, account,
            null, null, item);
        if (status == ITEM_NOT_FOUND) return;
        check(status, "find");
        try {
            check(security.SecKeychainItemDelete(item.getValue()), "delete");
        } finally {
            coreFoundation.CFRelease(item.getValue());
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static void check(int status, String what) throws IOException {
        if (status != OK) throw new IOException("Keychain " + what + " failed with OSStatus " + status);
    }
}
