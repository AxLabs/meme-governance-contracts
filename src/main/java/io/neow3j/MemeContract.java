package io.neow3j;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.StringLiteralHelper;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.events.Event1Arg;

@ManifestExtra(key = "author", value = "AxLabs")
@DisplayName("Memes")
public class MemeContract {

    static Hash160 initialOwner =
            StringLiteralHelper.addressToScriptHash("NXXazKH39yNFWWZF5MJ8tEN98VYHwzn7g3");
    static StorageContext ctx = Storage.getStorageContext();

    static final StorageMap OWNER_MAP = ctx.createMap((byte) 1);
    static final byte[] OWNER_KEY = Helper.toByteArray("0d");

    static final StorageMap REGISTRY_MAP = ctx.createMap((byte) 2);
    static final StorageMap DESCRIPTION_MAP = ctx.createMap((byte) 3);
    static final StorageMap URL_MAP = ctx.createMap((byte) 4);
    static final StorageMap IMAGE_HASH_MAP = ctx.createMap((byte) 5);

    @DisplayName("SetOwner")
    private static Event1Arg<Hash160> onSetOwner;

    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (!update) {
            setOwner(Hash160.zero());
        }
    }

    /**
     * Sets the owner.
     */
    public static boolean setOwner() throws Exception {
        if (getOwner() != Hash160.zero()) {
            throw new Exception("The owner is already set.");
        }
        if (!Runtime.checkWitness(initialOwner)) {
            throw new Exception("No authorization.");
        }
        Hash160 callingScriptHash = Runtime.getCallingScriptHash();
        setOwner(callingScriptHash);
        onSetOwner.fire(callingScriptHash);
        return true;
    }

    private static void setOwner(Hash160 newOwner) {
        OWNER_MAP.put(OWNER_KEY, newOwner.toByteArray());
    }

    @Safe
    public static Hash160 getOwner() {
        return new Hash160(OWNER_MAP.get(OWNER_KEY));
    }

    @Safe
    public static Hash160 getInitialOwner() {
        return initialOwner;
    }

    public static boolean createMeme(ByteString memeId, String description, String url,
            String imageHash) {
        if (memeId == null || description == null || url == null || imageHash == null) {
            return false;
        }
        if (!Runtime.checkWitness(getOwner())) {
            return false;
        }
        if (REGISTRY_MAP.get(memeId) != null) {
            return false;
        }
        REGISTRY_MAP.put(memeId, memeId);
        DESCRIPTION_MAP.put(memeId, description);
        URL_MAP.put(memeId, url);
        IMAGE_HASH_MAP.put(memeId, imageHash);
        return true;
    }

    public static boolean removeMeme(ByteString memeId) {
        if (!Runtime.checkWitness(getOwner())) {
            return false;
        }
        REGISTRY_MAP.delete(memeId);
        DESCRIPTION_MAP.delete(memeId);
        URL_MAP.delete(memeId);
        IMAGE_HASH_MAP.delete(memeId);
        return true;
    }

    @Safe
    public static Meme getMeme(ByteString memeId) throws Exception {
        if (REGISTRY_MAP.get(memeId) == null) {
            throw new Exception("No meme found for this id.");
        }
        ByteString desc = DESCRIPTION_MAP.get(memeId);
        ByteString url = URL_MAP.get(memeId);
        ByteString imageHash = IMAGE_HASH_MAP.get(memeId);
        return new Meme(desc, url, imageHash);
    }
}
