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

    // Set this address after the deployment of the MemeContract accordingly.
    static Hash160 memeContract =
        StringLiteralHelper.addressToScriptHash("NRTztyyexkyrEs88DjWPy6veYmFuuN2q6i");

    static final ByteString OWNER_KEY = StringLiteralHelper.hexToBytes("0x01");
    static final ByteString VOTING_TIME_KEY = StringLiteralHelper.hexToBytes("0x02");
    static final ByteString SAFE_TIME_KEY = StringLiteralHelper.hexToBytes("0x03");

    static final int CREATE = 1;
    static final int REMOVE = 2;

    static StorageContext ctx = Storage.getStorageContext();
    static final StorageMap CONTRACT_MAP = ctx.createMap((byte) 10);

    static final StorageMap PROPOSAL_MAP = ctx.createMap((byte) 11);
    static final StorageMap VOTE_MAP = ctx.createMap((byte) 12);
    static final StorageMap VOTE_COUNT_MAP = ctx.createMap((byte) 13);

    static final StorageMap VOTE_FOR_MAP = ctx.createMap((byte) 14);
    static final StorageMap VOTE_AGAINST_MAP = ctx.createMap((byte) 15);

    static final StorageMap DESCRIPTION_MAP = ctx.createMap((byte) 16);
    static final StorageMap URL_MAP = ctx.createMap((byte) 17);
    static final StorageMap IMAGE_HASH_MAP = ctx.createMap((byte) 18);

    /**
     * Stores the final blocks for voting on proposals.
     * Used for any kind of proposal, hence only one type of proposal is allowed per meme id once at a time.
     */
    static final StorageMap FINAL_VOTE_BLOCK_MAP = ctx.createMap((byte) 16);

    /**
     * Stores the last block on which a proposal can be safely executed.
     * After this block has passed, other proposals may overwrite the proposals id with a new proposal.
     */
    // static final StorageMap SAFE_EXEC_BLOCK_MAP = ctx.createMap((byte) 17);

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

    /**
     * Gets the amount of blocks that a proposal is open for voting after it was created.
     */
    @Safe
    public static int getVotingTime() {
        return Storage.get(ctx, VOTING_TIME_KEY).toInteger();
    }

    @DisplayName("CreationProposal")
    private static Event5Args<ByteString, String, String, String, Integer> onCreationProposal;

    /**
     * Proposes to create a meme with the provided data.
     * 
     * @param description the description of the meme.
     * @param url         the url of the meme.
     * @param imageHash   the hash of the image.
     * @throws Exception  if this meme id already exists.
     */
    public static void proposeCreation(ByteString memeId, String description, String url, String imageHash) throws Exception {
        if (PROPOSAL_MAP.get(memeId) != null) {
            throw new Exception("");
        }
        PROPOSAL_MAP.put(memeId, CREATE);
        DESCRIPTION_MAP.put(memeId, description);
        URL_MAP.put(memeId, url);
        IMAGE_HASH_MAP.put(memeId, imageHash);
        int finalization = LedgerContract.currentIndex() + getVotingTime();
        FINAL_VOTE_BLOCK_MAP.put(memeId, finalization);
        VOTE_FOR_MAP.put(memeId, 0);
        VOTE_AGAINST_MAP.put(memeId, 0);
        onCreationProposal.fire(memeId, description, url, imageHash, finalization);
    }

    @DisplayName("RemovalProposal")
    private static Event2Args<ByteString, Integer> onRemovalProposal;

    /**
     * This method proposes to remove an existing meme.
     * 
     * @param memeId the id of the existing meme that should be removed.
     */
    public static void proposeRemoval(ByteString memeId) throws Exception {
        boolean exists = (boolean) Contract.call(memeContract, "exists", CallFlags.READ_ONLY, new Object[]{memeId});
        if (!exists) {
            throw new Exception("No meme with the provided id exists.");
        }
        int currentIndex = LedgerContract.currentIndex();
        PROPOSAL_MAP.put(memeId, REMOVE);
        int finalization = currentIndex + getVotingTime();
        FINAL_VOTE_BLOCK_MAP.put(memeId, finalization);
        VOTE_FOR_MAP.put(memeId, 0);
        VOTE_AGAINST_MAP.put(memeId, 0);
        onRemovalProposal.fire(memeId, finalization);
    }

    @DisplayName("Vote")
    private static Event1Arg<ByteString> onVote;

    /**
     * Votes for or against the proposal of a meme.
     * 
     * @param memeId the id of the meme.
     * @param voter  the voter.
     * @param vote   the vote.
     */
    public static void vote(ByteString memeId, Hash160 voter, Boolean vote) throws Exception {
        if (!Runtime.checkWitness(voter)) {
            throw new Exception("No valid signature for the provided voter.");
        }
        if (PROPOSAL_MAP.get(memeId) == null) {
            throw new Exception("No proposal found.");
        }
        if (!voteOpen(memeId)) {
            throw new Exception("The vote for this meme is no longer open.");
        }
        ByteString voteKey = new ByteString(Helper.concat(memeId.toByteArray(), voter.asByteString()));
        if (VOTE_MAP.get(voteKey) != null) {
            throw new Exception("Already voted.");
        }
        VOTE_MAP.put(voteKey, 1);
        int currentVotes = VOTE_COUNT_MAP.get(memeId).toInteger();
        VOTE_COUNT_MAP.put(memeId, currentVotes + 1);
        if (vote) {
            int votesFor = VOTE_FOR_MAP.get(memeId).toInteger();
            VOTE_FOR_MAP.put(memeId, votesFor + 1);
        } else {
            int votesAgainst = VOTE_AGAINST_MAP.get(memeId).toInteger();
            VOTE_AGAINST_MAP.put(memeId, votesAgainst + 1);
        }
    }

    /**
     * Gets the amount of votes that are in favor of the proposal.
     */
    @Safe
    public static int getVotesFor(ByteString memeId) throws Exception {
        ByteString votesFor = VOTE_COUNT_MAP.get(memeId);
        if (votesFor == null) {
            throw new Exception("No proposal for this id.");
        }
        return votesFor.toInteger();
    }

    /**
     * Gets the amount of votes that are against the proposal.
     */
    @Safe
    public static int getVotesAgainst(ByteString memeId) throws Exception {
        ByteString votesAgainst = VOTE_AGAINST_MAP.get(memeId);
        if (votesAgainst == null) {
            throw new Exception("No proposal for this id.");
        }
        return votesAgainst.toInteger();
    }

    /**
     * Gets the total amount of votes for the proposal.
     */
    @Safe
    public static int getTotalVoteCount(ByteString memeId) throws Exception {
        ByteString voteCount = VOTE_COUNT_MAP.get(memeId);
        if (voteCount == null) {
            throw new Exception("No proposal for this id.");
        }
        return voteCount.toInteger();
    }

    /**
     * Whether the voting timeframe is open for a proposal.
     */
    @Safe
    public static boolean voteOpen(ByteString memeId) {
        if (PROPOSAL_MAP.get(memeId) == null) {
            return false;
        }
        int currentIndex = LedgerContract.currentIndex();
        int finalizationBlock = VOTE_MAP.get(memeId).toInteger();
        return finalizationBlock >= currentIndex;
    }

    /**
     * Executes a successful proposal.
     */
    public static boolean execute(ByteString memeId) throws Exception {
        ByteString proposal = PROPOSAL_MAP.get(memeId);
        if (proposal == null) {
            throw new Exception("No proposal found for this id.");
        }
        if (voteOpen(memeId)) {
            throw new Exception("The voting timeframe for this id is still open.");
        }
        int votesFor = VOTE_FOR_MAP.get(memeId).toInteger();
        int votesAgainst = VOTE_AGAINST_MAP.get(memeId).toInteger();
        boolean inFavor = votesFor > votesAgainst;
        if (inFavor && votesFor > 3) {
            int proposalKind = proposal.toInteger();
            if (proposalKind == CREATE) {
                // Create Meme
                String description = DESCRIPTION_MAP.get(memeId).toString();
                String url = URL_MAP.get(memeId).toString();
                String imageHash = IMAGE_HASH_MAP.get(memeId).toString();
                return (boolean) Contract.call(memeContract, "createMeme", CallFlags.ALL,
                    new Object[]{memeId, description, url, imageHash});
            } else {
                // Remove Meme
                return (boolean) Contract.call(memeContract, "removeMeme", CallFlags.ALL,
                    new Object[]{memeId});
            }
        }
        // If the proposal was not accepted, there is nothing to execute.
        return true;
    }
}
