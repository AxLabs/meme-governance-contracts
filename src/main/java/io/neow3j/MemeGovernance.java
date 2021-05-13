package io.neow3j;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.CallFlags;
import io.neow3j.devpack.Contract;
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
import io.neow3j.devpack.contracts.LedgerContract;
import io.neow3j.devpack.events.Event1Arg;
import io.neow3j.devpack.events.Event2Args;
import io.neow3j.devpack.events.Event5Args;

@ManifestExtra(key = "name", value = "MemeGovernance")
@ManifestExtra(key = "author", value = "AxLabs")
public class MemeGovernance {

    static Hash160 memeContract =
        StringLiteralHelper.addressToScriptHash("NRTztyyexkyrEs88DjWPy6veYmFuuN2q6i");

    static final ByteString OWNER_KEY = StringLiteralHelper.hexToBytes("0x01");
    static final ByteString VOTING_TIME_KEY = StringLiteralHelper.hexToBytes("0x02");
    static final ByteString SAFE_TIME_KEY = StringLiteralHelper.hexToBytes("0x03");

    static final byte CREATE = (byte) 20;
    static final byte REMOVE = (byte) 21;

    static StorageContext ctx = Storage.getStorageContext();
    static final StorageMap CONTRACT_MAP = ctx.createMap((byte) 10);

    static final StorageMap PROPOSAL_MAP = ctx.createMap((byte) 11);
    static final StorageMap VOTE_MAP = ctx.createMap((byte) 12);
    static final StorageMap VOTE_COUNT_MAP = ctx.createMap((byte) 13);

    static final StorageMap DESCRIPTION_MAP = ctx.createMap((byte) 14);
    static final StorageMap URL_MAP = ctx.createMap((byte) 15);
    static final StorageMap IMAGE_HASH_MAP = ctx.createMap((byte) 16);

    /**
     * Stores the final blocks for voting on proposals.
     * Used for any kind of proposal, hence only one type of proposal is allowed per meme id once at a time.
     */
    static final StorageMap FINAL_VOTE_BLOCK_MAP = ctx.createMap((byte) 16);

    /**
     * Stores the last block on which a proposal can be safely executed.
     * After this block has passed, other proposals may overwrite the proposals id with a new proposal.
     */
    static final StorageMap SAFE_EXEC_BLOCK_MAP = ctx.createMap((byte) 17);

    @OnDeployment
    public static void deploy(Object data, boolean update) throws Exception {
        if (!update) {
            boolean successfulInitialized = (boolean) Contract.call(memeContract, "setOwner", CallFlags.ALL, new Object[]{});
            if (!successfulInitialized) {
                throw new Exception("Contract could not successfully be connected to Memes contract.");
            }
            // The amount of blocks that accept votes after the initial proposal was made.
            Storage.put(ctx, SAFE_TIME_KEY, 10);
            // The amount of blocks after the voting is closed in which an unexecuted proposal cannot be overwritten.
            Storage.put(ctx, VOTING_TIME_KEY, 10);
        }
    }

    @Safe
    public static int getVotingTime() {
        return Storage.get(ctx, VOTING_TIME_KEY).toInteger();
    }

    @Safe
    public static int getSafeExecutionTime() {
        return Storage.get(ctx, SAFE_TIME_KEY).toInteger();
    }

    @DisplayName("CreationProposal")
    private static Event5Args<ByteString, String, String, String, Integer> onCreationProposal;

    /**
     * Proposes to create a meme with the provided data.
     * 
     * @param description the description of the meme.
     * @param url         the url of the meme.
     * @param imageHash   the hash of the image.
     * @throws Exception
     */
    public static void proposeCreation(ByteString memeId, String description, String url, String imageHash) throws Exception {
        ByteString meme = (ByteString) Contract.call(memeContract, "exists", CallFlags.READ_ONLY, new Object[]{memeId});
        if (meme != null) {
            throw new Exception("A meme with the provided id already exists.");
        }
        int currentIndex = LedgerContract.currentIndex();
        throwIfMemeIdLocked(memeId, currentIndex);
        PROPOSAL_MAP.put(memeId, CREATE);
        DESCRIPTION_MAP.put(memeId, description);
        URL_MAP.put(memeId, url);
        IMAGE_HASH_MAP.put(memeId, imageHash);
        int finalization = LedgerContract.currentIndex() + getVotingTime();
        FINAL_VOTE_BLOCK_MAP.put(memeId, finalization);
        SAFE_EXEC_BLOCK_MAP.put(memeId, finalization + getSafeExecutionTime());
        onCreationProposal.fire(memeId, description, url, imageHash, finalization);
    }

    @DisplayName("RemovalProposal")
    private static Event2Args<ByteString, Integer> onRemovalProposal;

    /**
     * This method proposes to remove an existing meme.
     * 
     * @param memeId the id of the existing meme that should be removed.
     * @throws Exception
     */
    public static void proposeRemoval(ByteString memeId) throws Exception {
        boolean exists = (boolean) Contract.call(memeContract, "exists", CallFlags.READ_ONLY, new Object[]{memeId});
        if (!exists) {
            throw new Exception("No meme with the provided id exists.");
        }
        int currentIndex = LedgerContract.currentIndex();
        throwIfMemeIdLocked(memeId, currentIndex);
        PROPOSAL_MAP.put(memeId, REMOVE);
        int finalization = currentIndex + getVotingTime();
        int proposalIdLockedUntilBlock = finalization + getSafeExecutionTime();
        FINAL_VOTE_BLOCK_MAP.put(memeId, finalization);
        SAFE_EXEC_BLOCK_MAP.put(memeId, proposalIdLockedUntilBlock);
        onRemovalProposal.fire(memeId, finalization);
    }

    @DisplayName("Vote")
    private static Event1Arg<ByteString> onVote;

    public static void vote(ByteString memeId, Hash160 voter) throws Exception {
        if (!Runtime.checkWitness(voter)) {
            throw new Exception("No valid signature for the provided voter.");
        }
        if (PROPOSAL_MAP.get(memeId) == null) {
            throw new Exception("No proposal found.");
        }
        throwIfVoteClosed(memeId);
        ByteString votePrefix = new ByteString(Helper.concat(memeId.toByteArray(), voter.asByteString()));
        if (VOTE_MAP.get(votePrefix) != null) {
            throw new Exception("Already voted.");
        }
        VOTE_MAP.put(votePrefix, 1);
        int currentVotes = VOTE_COUNT_MAP.get(memeId).toInteger();
        VOTE_COUNT_MAP.put(memeId, currentVotes + 1);
    }

    @Safe
    public static int getVotes(ByteString proposalId) throws Exception {
        ByteString voteCount = VOTE_COUNT_MAP.get(proposalId);
        if (voteCount == null) {
            throw new Exception("No proposal for this id.");
        }
        return voteCount.toInteger();
    }

    private static void throwIfVoteClosed(ByteString memeId) throws Exception {
        int currentIndex = LedgerContract.currentIndex();
        if (PROPOSAL_MAP.get(memeId) != null) {
            int finalizationBlock = VOTE_MAP.get(memeId).toInteger();
            if (currentIndex > finalizationBlock) {
                throw new Exception("The vote for this meme is no longer open.");
            }
        }
    }

    private static void throwIfMemeIdLocked(ByteString memeId, int currentIndex) throws Exception {
        if (PROPOSAL_MAP.get(memeId) != null) {
            int safeExecBlock = SAFE_EXEC_BLOCK_MAP.get(memeId).toInteger();
            if (currentIndex > safeExecBlock) {
                throw new Exception("There exists already a proposal with the provided id.");
            }
        }
    }

    public static boolean executeProposal(ByteString memeId) {
        // TODO: check vote closed, check enough votes, execute proposal intention
        return true;
    }

}
