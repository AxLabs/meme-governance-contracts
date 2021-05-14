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
    // static Hash160 memeContract =
    //     StringLiteralHelper.addressToScriptHash("NMBA3Wh1iFbB4mtYtAKvyrzHnYBr38pnTg");

    static final ByteString OWNER_KEY = StringLiteralHelper.hexToBytes("0x01");
    static final ByteString MEME_CONTRACT_KEY = StringLiteralHelper.hexToBytes("0x02");
    static final ByteString VOTING_TIME_KEY = StringLiteralHelper.hexToBytes("0x03");

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
    static final StorageMap FINALIZATION_MAP = ctx.createMap((byte) 16);

    /**
     * Stores the last block on which a proposal can be safely executed.
     * After this block has passed, other proposals may overwrite the proposals id with a new proposal.
     */
    // static final StorageMap SAFE_EXEC_BLOCK_MAP = ctx.createMap((byte) 17);

    @OnDeployment
    public static void deploy(Object data, boolean update) throws Exception {
        if (!update) {
            Hash160 initialOwner = StringLiteralHelper.addressToScriptHash("NXXazKH39yNFWWZF5MJ8tEN98VYHwzn7g3");
            CONTRACT_MAP.put(OWNER_KEY, initialOwner.toByteArray());
            // The amount of blocks after the voting is closed.
            Storage.put(ctx, VOTING_TIME_KEY, 10);
        }
    }

    public static Hash160 getOwner() {
        return new Hash160(CONTRACT_MAP.get(OWNER_KEY));
    }

    public static void unsetOwner() throws Exception {
        Hash160 owner = getOwner();
        Hash160 zeroAddress = Hash160.zero();
        if (owner != zeroAddress) {
            if (!Runtime.checkWitness(owner)) {
                throw new Exception("No autorization.");
            }
            CONTRACT_MAP.put(OWNER_KEY, zeroAddress.toByteArray());
        }
    }

    @DisplayName("SetMemeContract")
    private static Event1Arg<Hash160> onMemeContractSet;

    public static void setMemeContract(Hash160 memeContract) throws Exception {
        if (!Runtime.checkWitness(getOwner())) {
            throw new Exception("No authorization.");
        }
        CONTRACT_MAP.put(MEME_CONTRACT_KEY, memeContract.toByteArray());
        onMemeContractSet.fire(memeContract);
    }

    @Safe
    public static Hash160 getMemeContract() {
        return new Hash160(CONTRACT_MAP.get(MEME_CONTRACT_KEY));
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
        // The current index is the index of the block that was created last.
        int finalization = LedgerContract.currentIndex() + getVotingTime() + 1;
        FINALIZATION_MAP.put(memeId, finalization);
        VOTE_COUNT_MAP.put(memeId, 0);
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
        boolean exists = (boolean) Contract.call(getMemeContract(), "exists", CallFlags.READ_ONLY, new Object[]{memeId});
        if (!exists) {
            throw new Exception("No meme with the provided id exists.");
        }
        int currentIndex = LedgerContract.currentIndex();
        PROPOSAL_MAP.put(memeId, REMOVE);
        int finalization = currentIndex + getVotingTime();
        FINALIZATION_MAP.put(memeId, finalization);
        VOTE_COUNT_MAP.put(memeId, 0);
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
    public static void vote(ByteString memeId, Hash160 voter, boolean vote) throws Exception {
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
        ByteString votesFor = VOTE_FOR_MAP.get(memeId);
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
    public static Integer getTotalVoteCount(ByteString memeId) throws Exception {
        ByteString voteCount = VOTE_COUNT_MAP.get(memeId);
        if (voteCount == null) {
            throw new Exception("No proposal for this id.");
        }
        return voteCount.toInteger();
    }

    @Safe
    public static int getFinalizationBlock(ByteString memeId) throws Exception {
        if (PROPOSAL_MAP.get(memeId) == null) {
            throw new Exception("No proposal found for this id.");
        }
        return FINALIZATION_MAP.get(memeId).toInteger();
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
        int finalizationBlock = FINALIZATION_MAP.get(memeId).toInteger();
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
                return (boolean) Contract.call(getMemeContract(), "createMeme", CallFlags.ALL,
                    new Object[]{memeId, description, url, imageHash});
            } else {
                // Remove Meme
                return (boolean) Contract.call(getMemeContract(), "removeMeme", CallFlags.ALL,
                    new Object[]{memeId});
            }
        }
        // If the proposal was not accepted, there is nothing to execute.
        return true;
    }
}
