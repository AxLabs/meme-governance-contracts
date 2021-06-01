package com.axlabs;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.FindOptions;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.Iterator;
import io.neow3j.devpack.List;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.StringLiteralHelper;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.Safe;

@ManifestExtra(key = "author", value = "AxLabs")
@DisplayName("Memes")
public class MemeContract {

    static Hash160 initialOwner =
            StringLiteralHelper.addressToScriptHash("NXXazKH39yNFWWZF5MJ8tEN98VYHwzn7g3");
    static StorageContext ctx = Storage.getStorageContext();

    static final StorageMap OWNER_MAP = ctx.createMap((byte) 1);
    static final byte[] OWNER_KEY = Helper.toByteArray("0d");

    static final byte[] REGISTRY_PREFIX = Helper.toByteArray((byte) 2);
    static final StorageMap REGISTRY_MAP = ctx.createMap(REGISTRY_PREFIX);
    static final StorageMap DESCRIPTION_MAP = ctx.createMap((byte) 3);
    static final StorageMap URL_MAP = ctx.createMap((byte) 4);
    static final StorageMap IMAGE_HASH_MAP = ctx.createMap((byte) 5);

    static final int MAX_GET_MEMES = 8;

    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (!update) {
            OWNER_MAP.put(OWNER_KEY, Hash160.zero().toByteArray());
        }
    }

    /**
     * Initializes the connection to the government contract.
     * <p>
     * This method is intended to be called from the governance contract.
     */
    public static boolean initialize() throws Exception {
        // Restricts to set the owner (governance contract) only once to ensure its decentralized
        // aspect.
        if (getOwner() != Hash160.zero()) {
            throw new Exception("Already initialized.");
        }
        if (!Runtime.checkWitness(initialOwner)) {
            throw new Exception("No authorization.");
        }
        Hash160 callingScriptHash = Runtime.getCallingScriptHash();
        OWNER_MAP.put(OWNER_KEY, callingScriptHash.toByteArray());
        return true;
    }

    /**
     * Gets the owner of this contract, that is permitted to create or remove memes.
     */
    @Safe
    public static Hash160 getOwner() {
        return new Hash160(OWNER_MAP.get(OWNER_KEY));
    }

    /**
     * Creates a meme.
     */
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

    /**
     * Removes a meme.
     */
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

    /**
     * Gets the properties of a meme.
     */
    @Safe
    public static Meme getMeme(ByteString memeId) throws Exception {
        if (REGISTRY_MAP.get(memeId) == null) {
            throw new Exception("No meme found for this id.");
        }
        ByteString desc = DESCRIPTION_MAP.get(memeId);
        ByteString url = URL_MAP.get(memeId);
        ByteString imgHash = IMAGE_HASH_MAP.get(memeId);
        return new Meme(memeId, desc, url, imgHash);
    }

    @Safe
    public static List<Meme> getMemes(int startingIndex) {
        int finalIndex = startingIndex + MAX_GET_MEMES;
        int index = -1;
        List<Meme> memes = new List<>();
        Iterator<Iterator.Struct<ByteString, ByteString>> iterator =
                Storage.find(ctx, REGISTRY_PREFIX, FindOptions.RemovePrefix);
        while (iterator.next()) {
            index += 1;
            if (index < startingIndex) {
                continue;
            }
            if (index == finalIndex) {
                break;
            }
            Iterator.Struct<ByteString, ByteString> pair = iterator.get();
            ByteString memeId = pair.key;
            ByteString desc = DESCRIPTION_MAP.get(memeId);
            ByteString url = URL_MAP.get(memeId);
            ByteString imgHash = IMAGE_HASH_MAP.get(memeId);

            memes.add(new Meme(memeId, desc, url, imgHash));
        }
        return memes;
    }

}
