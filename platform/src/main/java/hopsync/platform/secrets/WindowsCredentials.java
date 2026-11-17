package hopsync.platform.secrets;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import hopsync.core.port.SecretStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Windows Credential Manager, as generic credentials named "HopSync/&lt;key&gt;" kept on this computer. */
final class WindowsCredentials implements SecretStore {
    private static final int CRED_TYPE_GENERIC = 1;
    private static final int CRED_PERSIST_LOCAL_MACHINE = 2;
    private static final int ERROR_NOT_FOUND = 1168;

    /** The Win32 CREDENTIALW structure. */
    @Structure.FieldOrder({"flags", "type", "targetName", "comment", "lastWrittenLow", "lastWrittenHigh",
        "credentialBlobSize", "credentialBlob", "persist", "attributeCount", "attributes", "targetAlias", "userName"})
    public static final class Credential extends Structure {
        public int flags;
        public int type;
        public WString targetName;
        public WString comment;
        public int lastWrittenLow;
        public int lastWrittenHigh;
        public int credentialBlobSize;
        public Pointer credentialBlob;
        public int persist;
        public int attributeCount;
        public Pointer attributes;
        public WString targetAlias;
        public WString userName;

        public Credential() {
        }

        public Credential(Pointer memory) {
            super(memory);
            read();
        }
    }

    private interface Advapi32 extends StdCallLibrary {
        boolean CredReadW(WString target, int type, int flags, PointerByReference credential);

        boolean CredWriteW(Credential credential, int flags);

        boolean CredDeleteW(WString target, int type, int flags);

        void CredFree(Pointer buffer);
    }

    private final Advapi32 advapi = Native.load("Advapi32", Advapi32.class, W32APIOptions.UNICODE_OPTIONS);

    @Override
    public boolean persistent() {
        return true;
    }

    @Override
    public synchronized Optional<String> read(String key) throws IOException {
        PointerByReference found = new PointerByReference();
        if (!advapi.CredReadW(target(key), CRED_TYPE_GENERIC, 0, found)) {
            int error = Native.getLastError();
            if (error == ERROR_NOT_FOUND) return Optional.empty();
            throw new IOException("Credential Manager read failed with error " + error);
        }
        try {
            Credential credential = new Credential(found.getValue());
            byte[] blob = credential.credentialBlob.getByteArray(0, credential.credentialBlobSize);
            return Optional.of(new String(blob, StandardCharsets.UTF_8));
        } finally {
            advapi.CredFree(found.getValue());
        }
    }

    @Override
    public synchronized void write(String key, String secret) throws IOException {
        byte[] blob = secret.getBytes(StandardCharsets.UTF_8);
        Memory memory = new Memory(Math.max(1, blob.length));
        memory.write(0, blob, 0, blob.length);
        Credential credential = new Credential();
        credential.type = CRED_TYPE_GENERIC;
        credential.targetName = target(key);
        credential.credentialBlobSize = blob.length;
        credential.credentialBlob = memory;
        credential.persist = CRED_PERSIST_LOCAL_MACHINE;
        credential.userName = new WString(key);
        try {
            if (!advapi.CredWriteW(credential, 0)) {
                throw new IOException("Credential Manager write failed with error " + Native.getLastError());
            }
        } finally {
            memory.clear();
        }
    }

    @Override
    public synchronized void delete(String key) throws IOException {
        if (!advapi.CredDeleteW(target(key), CRED_TYPE_GENERIC, 0)) {
            int error = Native.getLastError();
            if (error != ERROR_NOT_FOUND) throw new IOException("Credential Manager delete failed with error " + error);
        }
    }

    private static WString target(String key) {
        return new WString(SystemKeychain.SERVICE + "/" + key);
    }
}
