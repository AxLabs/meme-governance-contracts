package com.axlabs;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Hash160;
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
import static io.neow3j.devpack.Helper.toByteArray;

@ManifestExtra(key = "author", value = "AxLabs")
public class MemeContract {

    static final int MAX_GET_MEMES = 100;
    static final byte[] OWNER_KEY = new byte[]{0x0d};
    static final byte DESC_MAP_PREFIX = 2;

    static StorageContext ctx = Storage.getStorageContext();
    static final StorageMap contractMap = ctx.createMap((byte) 1);
    static final StorageMap descriptionMap = ctx.createMap(DESC_MAP_PREFIX);
    static final StorageMap urlMap = ctx.createMap((byte) 3);
    static final StorageMap imgHashMap = ctx.createMap((byte) 4);


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
            contractMap.put(OWNER_KEY, (ByteString) data);
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
        contractMap.put(OWNER_KEY, callingScriptHash.toByteArray());
        return true;
    }

    /**
     * Gets the owner of this contract, that is permitted to create or remove memes.
     */
    @Safe
    public static Hash160 getOwner() {
        return new Hash160(contractMap.get(OWNER_KEY));
    }

    /**
     * Creates a meme.
     */
    public static boolean createMeme(String memeId, String description, String url, ByteString imageHash) {
        if (memeId == null || description == null || url == null || imageHash == null) {
            return false;
        }
        if (Runtime.getCallingScriptHash() != getOwner()) {
            return false;
        }
        if (descriptionMap.get(memeId) != null) {
            return false;
        }
        descriptionMap.put(memeId, description);
        urlMap.put(memeId, url);
        imgHashMap.put(memeId, imageHash);
        return true;
    }

    /**
     * Removes a meme.
     */
    public static boolean removeMeme(String memeId) {
        if (Runtime.getCallingScriptHash() != getOwner()) {
            return false;
        }
        descriptionMap.delete(memeId);
        urlMap.delete(memeId);
        imgHashMap.delete(memeId);
        return true;
    }

    /**
     * Gets the properties of a meme.
     */
    @Safe
    public static Meme getMeme(String memeId) throws Exception {
        if (descriptionMap.get(memeId) == null) {
            throw new Exception("No meme found for this id.");
        }
        String desc = descriptionMap.get(memeId).toString();
        String url = urlMap.get(memeId).toString();
        ByteString imgHash = imgHashMap.get(memeId);
        return new Meme(memeId, desc, url, imgHash);
    }

    @Safe
    public static List<Meme> getMemes(int startingIndex) {
        int finalIndex = startingIndex + MAX_GET_MEMES;
        List<Meme> memes = new List<>();
        Iterator<Iterator.Struct<ByteString, ByteString>> iterator = 
            Storage.find(ctx, toByteArray(DESC_MAP_PREFIX), FindOptions.RemovePrefix);
        int i = 0;
        while (iterator.next()) {
            if (i < startingIndex) {
                continue;
            }
            if (i == finalIndex) {
                break;
            }
            Iterator.Struct<ByteString, ByteString> pair = iterator.get();
            String memeId = pair.key.toString();
            String desc = pair.value.toString();
            String url = urlMap.get(memeId).toString();
            ByteString imgHash = imgHashMap.get(memeId);
            memes.add(new Meme(memeId, desc, url, imgHash));
            i++;
        }
        return memes;
    }

}
