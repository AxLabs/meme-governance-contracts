package com.axlabs;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.Iterator;
import io.neow3j.devpack.List;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.FindOptions;

@ManifestExtra(key = "author", value = "AxLabs")
public class MemeContract {

    static StorageContext ctx = Storage.getStorageContext();

    static final StorageMap CONTRACT_MAP = ctx.createMap((byte) 1);
    static final byte[] OWNER_KEY = Helper.toByteArray("0d");

    static final byte[] REGISTRY_PREFIX = Helper.toByteArray((byte) 2);
    static final StorageMap REGISTRY_MAP = ctx.createMap(REGISTRY_PREFIX);
    static final StorageMap DESCRIPTION_MAP = ctx.createMap((byte) 3);
    static final StorageMap URL_MAP = ctx.createMap((byte) 4);
    static final StorageMap IMAGE_HASH_MAP = ctx.createMap((byte) 5);

    static final int MAX_GET_MEMES = 100;

    @OnDeployment
    public static void deploy(Object data, boolean update) throws Exception {
        if (!update) {
            String defaultErrorMsg = "MemeContract's deploy method expects the owner hash as an argument ";
            if (data == null) {
                throw new Exception(defaultErrorMsg + "but argument was null.");
            }
            if (!(data instanceof ByteString) || !(new Hash160((ByteString) data).isValid())) {
                throw new Exception(defaultErrorMsg + "but argument was not a valid Hash160.");
            }
            CONTRACT_MAP.put(OWNER_KEY, (ByteString) data);
        }
    }

    /**
     * Initializes the connection to the government contract.
     * <p>
     * This method is intended to be called from the governance contract.
     */
    public static boolean initialize() throws Exception {
        if (!Runtime.checkWitness(getOwner())) {
            return false;
        }
        Hash160 callingScriptHash = Runtime.getCallingScriptHash();
        CONTRACT_MAP.put(OWNER_KEY, callingScriptHash.toByteArray());
        return true;
    }

    /**
     * Gets the owner of this contract, that is permitted to create or remove memes.
     */
    @Safe
    public static Hash160 getOwner() {
        return new Hash160(CONTRACT_MAP.get(OWNER_KEY));
    }

    /**
     * Creates a meme.
     */
    public static boolean createMeme(String memeId, String description, String url, String imageHash) {
        if (memeId == null || description == null || url == null || imageHash == null) {
            return false;
        }
        if (Runtime.getCallingScriptHash() != getOwner()) {
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
    public static boolean removeMeme(String memeId) {
        if (Runtime.getCallingScriptHash() != getOwner()) {
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
    public static Meme getMeme(String memeId) throws Exception {
        if (REGISTRY_MAP.get(memeId) == null) {
            throw new Exception("No meme found for this id.");
        }
        String desc = DESCRIPTION_MAP.get(memeId).toString();
        String url = URL_MAP.get(memeId).toString();
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
            String memeId = pair.key.toString();
            String desc = DESCRIPTION_MAP.get(memeId).toString();
            String url = URL_MAP.get(memeId).toString();
            ByteString imgHash = IMAGE_HASH_MAP.get(memeId);

            memes.add(new Meme(memeId, desc, url, imgHash));
        }
        return memes;
    }

}
