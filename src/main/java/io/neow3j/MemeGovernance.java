package io.neow3j;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.CallFlags;
import io.neow3j.devpack.Contract;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.StringLiteralHelper;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.contracts.LedgerContract;

// A simple smart contract with one method that returns a string and takes no arguments.
@ManifestExtra(key = "name", value = "MemeGovernance")
@ManifestExtra(key = "author", value = "AxLabs")
public class MemeGovernance {

    static Hash160 owner =
        StringLiteralHelper.addressToScriptHash("NM7Aky765FG8NhhwtxjXRx7jEL1cnw7PBP");
    
    static Hash160 memeContract =
        StringLiteralHelper.addressToScriptHash("NM7Aky765FG8NhhwtxjXRx7jEL1cnw7PBP");

    /**
     * The amount of blocks that accept votes after the initial proposal was made.
     */
    static int votingTime = 10;

    /**
     * The amount of blocks after the voting is closed in which an unexecuted proposal cannot be overwritten.
     */
    static int safeTimeForExecutingSuccessfulProposals = 10;

    static final ByteString CREATE_PROPOSAL_COUNT_KEY = StringLiteralHelper.hexToBytes("0x01");
    static final ByteString REMOVE_PROPOSAL_COUNT_KEY = StringLiteralHelper.hexToBytes("0x02");

    static StorageContext ctx = Storage.getStorageContext();
    static final StorageMap CONTRACT_MAP = ctx.createMap((byte) 1);

    static final StorageMap DESCRIPTION_MAP = ctx.createMap((byte) 2);
    static final StorageMap URL_MAP = ctx.createMap((byte) 3);
    static final StorageMap IMAGE_HASH_MAP = ctx.createMap((byte) 4);

    static final StorageMap REMOVAL_MAP = ctx.createMap((byte) 5);

    /**
     * Stores the final blocks for voting on proposals.
     * Used for any kind of proposal, hence only one type of proposal is allowed per meme id once at a time.
     */
    static final StorageMap FINAL_BLOCK_MAP = ctx.createMap((byte) 7);

    @OnDeployment
    public static void deploy(Object data, boolean update) {
        Storage.put(ctx, CREATE_PROPOSAL_COUNT_KEY, 0);
        Storage.put(ctx, REMOVE_PROPOSAL_COUNT_KEY, 0);
    }

    /**
     * Sets the owner to the zero address.
     */
    public static void unsetOwner() throws Exception {
        if (!Runtime.checkWitness(owner)) {
            throw new Exception("No authorization.");
        }
        owner = Hash160.zero();
    }

    /**
     * Gets the owner of this contract.
     * 
     * @return the contract owner.
     */
    public static Hash160 getOwner() {
        return owner;
    }

    /**
     * Proposes to create a meme with the provided data.
     * 
     * @param description the description of the meme.
     * @param url         the url of the meme.
     * @param imageHash   the hash of the image.
     * @throws Exception
     */
    public static void proposeCreation(ByteString memeId, String description, String url, String imageHash) throws Exception {
        boolean exists = (boolean) Contract.call(memeContract, "exists", CallFlags.READ_ONLY, new Object[]{memeId});
        if (exists) {
            throw new Exception("A meme with the provided uuid already exists.");
        }
        DESCRIPTION_MAP.put(memeId, description);
        URL_MAP.put(memeId, url);
        IMAGE_HASH_MAP.put(memeId, imageHash);
        FINAL_BLOCK_MAP.put(memeId, LedgerContract.currentIndex() + votingTime);
    }

    /**
     * This method proposes to remove an existing meme.
     * 
     * @param memeId the id of the existing meme that should be removed.
     * @throws Exception
     */
    public static void proposeRemoval(ByteString memeId) throws Exception {
        boolean exists = (boolean) Contract.call(memeContract, "exists", CallFlags.READ_ONLY, new Object[]{memeId});
        if (!exists) {
            throw new Exception("No meme with the provided uuid exists.");
        }
        if (FINAL_BLOCK_MAP.get(memeId) != null) {
            throw new Exception("This meme already has an active proposal ongoing.");
        }
        REMOVAL_MAP.put(memeId, memeId);
        FINAL_BLOCK_MAP.put(memeId, LedgerContract.currentIndex() + votingTime);
    }

    public static void retryProposal(int proposalId) {}

    // public static class MemeProperties {
    //     private String description;
    //     private String url;
    //     private String imageHash;

    //     public MemeProperties(String description, String url, String imageHash) {
    //         this.description = description;
    //         this.url = url;
    //         this.imageHash = imageHash;
    //     }

    //     public String getDescription() {
    //         return description;
    //     }

    //     public String getURL() {
    //         return url;
    //     }

    //     public String getImageHash() {
    //         return imageHash;
    //     }
    // }

//    public static boolean setMemeContractOwner()
    
}
