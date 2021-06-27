package com.axlabs;

import static io.neow3j.devpack.Helper.toByteArray;
import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
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
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.constants.FindOptions;
import io.neow3j.devpack.contracts.LedgerContract;
import io.neow3j.devpack.events.Event1Arg;
import io.neow3j.devpack.events.Event2Args;
import io.neow3j.devpack.events.Event3Args;
import io.neow3j.devpack.events.Event4Args;
import io.neow3j.devpack.events.Event5Args;

@ManifestExtra(key = "author", value = "AxLabs")
@Permission(contract = "*")
public class GovernanceContract {

    static final StorageContext ctx = Storage.getStorageContext();
    static final StorageMap contractMap = ctx.createMap((byte) 1);

    static final ByteString MEME_CONTRACT_KEY = StringLiteralHelper.hexToBytes("0x01");

    static final byte PROPOSAL_PREFIX = 2;
    static final StorageMap proposalMap = ctx.createMap(PROPOSAL_PREFIX);

    static final byte VOTE_PREFIX = 3;
    static final StorageMap voteCountMap = ctx.createMap((byte) 4);
    static final StorageMap voteForMap = ctx.createMap((byte) 5);
    static final StorageMap voteAgainstMap = ctx.createMap((byte) 6);

    // Memes 
    static final StorageMap descriptionMap = ctx.createMap((byte) 7);
    static final StorageMap urlMap = ctx.createMap((byte) 8);
    static final StorageMap imgHashMap = ctx.createMap((byte) 9);

    // Stores the vote deadline block number for proposals. 
    static final StorageMap finalizationMap = ctx.createMap((byte) 10);

    static final int REMOVE = 0;
    static final int CREATE = 1;

    static final int VOTING_TIME = 10;
    static final int MIN_VOTES_IN_FAVOR = 3;
    static final int MAX_GET_PROPOSALS = 100;

    @DisplayName("deployEvent")
    private static Event1Arg<Hash160> onDeploy;

    /**
     * Stores the last block on which a proposal can be safely executed. After this block has
     * passed, other proposals may overwrite the proposals id with a new proposal.
     */
    @OnDeployment
    public static void deploy(Object data, boolean update) throws Exception {
        if (!update) {
            initialize((Hash160) data);
        }
    }

    /**
     * Initializes the link to the underlying MemeContract and removes this contract's user.
     */
    private static void initialize(Hash160 memeContract) throws Exception {
        boolean initialize = (boolean) Contract.call(memeContract, "initialize", CallFlags.States,
                new Object[] {});
        if (initialize) {
            contractMap.put(MEME_CONTRACT_KEY, memeContract.toByteString());
        } else {
            throw new Exception("Could not initialize.");
        }
    }

    /**
     * Gets the address of the underlying MemeContract.
     */
    @Safe
    public static Hash160 getMemeContract() {
        return new Hash160(contractMap.get(MEME_CONTRACT_KEY));
    }

    /**
     * Gets the amount of blocks that a proposal is open for voting after it was created.
     */
    @Safe
    public static int getVotingTime() {
        return VOTING_TIME;
    }

    /**
     * Gets the minimum number of votes in favor for a proposal to be accepted.
     */
    @Safe
    public static int getMinVotesInFavor() {
        return MIN_VOTES_IN_FAVOR;
    }

    @DisplayName("CreationProposal")
    private static Event5Args<String, String, String, ByteString, Integer> onCreationProposal;

    /**
     * Proposes to create a meme with the provided data.
     *
     * @param description the description of the meme.
     * @param url the url of the meme.
     * @param imageHash the sha256 hash of the image.
     * @throws Exception if this meme id already exists.
     */
    public static void proposeNewMeme(String memeId, String description, String url,
            ByteString imageHash) throws Exception {
        if (memeExists(memeId)) {
            throw new Exception("There already exists a meme with this id. Propose and execute "
                    + "its removal before you can create a proposal for a new meme with this id.");
        }
        handleExistingProposal(memeId);

        proposalMap.put(memeId, CREATE);
        descriptionMap.put(memeId, description);
        urlMap.put(memeId, url);
        imgHashMap.put(memeId, imageHash);
        // The current index is the index of the block that was created last.
        int finalization = LedgerContract.currentIndex() + getVotingTime();
        finalizationMap.put(memeId, finalization);
        voteCountMap.put(memeId, 0);
        voteForMap.put(memeId, 0);
        voteAgainstMap.put(memeId, 0);
        onCreationProposal.fire(memeId, description, url, imageHash, finalization);
    }

    @DisplayName("RemovalProposal")
    private static Event2Args<String, Integer> onRemovalProposal;

    /**
     * This method proposes to remove an existing meme.
     *
     * @param memeId the id of the existing meme that should be removed.
     */
    public static void proposeRemoval(String memeId) throws Exception {
        if (!memeExists(memeId)) {
            throw new Exception("No meme with the provided id exists.");
        }
        handleExistingProposal(memeId);

        int currentIndex = LedgerContract.currentIndex();
        proposalMap.put(memeId, REMOVE);
        int finalization = currentIndex + getVotingTime();
        finalizationMap.put(memeId, finalization);
        voteCountMap.put(memeId, 0);
        voteForMap.put(memeId, 0);
        voteAgainstMap.put(memeId, 0);
        onRemovalProposal.fire(memeId, finalization);
    }

    private static boolean memeExists(String memeId) {
        try {
            Contract.call(getMemeContract(), "getMeme", CallFlags.ReadOnly, new Object[] {memeId});
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private static void handleExistingProposal(String memeId) throws Exception {
        if (proposalMap.get(memeId) != null) {
            if (voteInProgress(memeId)) {
                throw new Exception("A proposal is still ongoing for this meme id.");
            }
            if (isAccepted(memeId)) {
                throw new Exception("This proposal was accepted and needs to be executed "
                        + "before creating a new proposal for this meme id.");
            }
        }
    }

    private static boolean isAccepted(String memeId) {
        int votesFor = voteForMap.get(memeId).toInteger();
        int votesAgainst = voteAgainstMap.get(memeId).toInteger();
        return votesFor > votesAgainst && votesFor >= MIN_VOTES_IN_FAVOR;
    }

    @DisplayName("Vote")
    private static Event3Args<String, ByteString, Boolean> onVote;

    /**
     * Votes for or against the proposal of a meme.
     *
     * @param memeId the id of the meme.
     * @param voter the voter.
     * @param inFavor whether voting in favor of the proposal or against.
     */
    public static void vote(String memeId, Hash160 voter, boolean inFavor) throws Exception {
        if (!Runtime.checkWitness(voter)) {
            throw new Exception("No valid signature for the provided voter.");
        }
        if (proposalMap.get(memeId) == null) {
            throw new Exception("No proposal found.");
        }
        if (!voteInProgress(memeId)) {
            throw new Exception("The vote for this meme is no longer open.");
        }

        StorageMap voteMap = ctx.createMap(createVotePrefix(memeId));
        ByteString voterByteString = voter.toByteString();
        ByteString alreadyVoted = voteMap.get(voterByteString);
        onVote.fire(memeId, voterByteString, inFavor);
        if (alreadyVoted != null) {
            throw new Exception("Already voted.");
        }
        voteMap.put(voterByteString, 1);

        int currentVotes = voteCountMap.get(memeId).toInteger();
        voteCountMap.put(memeId, currentVotes + 1);
        if (inFavor) {
            int votesFor = voteForMap.get(memeId).toInteger();
            voteForMap.put(memeId, votesFor + 1);
        } else {
            int votesAgainst = voteAgainstMap.get(memeId).toInteger();
            voteAgainstMap.put(memeId, votesAgainst + 1);
        }
    }

    private static byte[] createVotePrefix(String memeId) {
        return Helper.concat(Helper.toByteArray(VOTE_PREFIX), memeId);
    }

    @DisplayName("MemeCreation")
    private static Event4Args<String, String, String, String> onCreation;

    @DisplayName("MemeRemoval")
    private static Event1Arg<String> onRemoval;

    @DisplayName("UnacceptedProposalRemoval")
    private static Event1Arg<String> onRemovingUnacceptedProposal;

    /**
     * Executes a proposal.
     */
    public static boolean execute(String memeId) throws Exception {
        ByteString proposal = proposalMap.get(memeId);
        if (proposal == null) {
            throw new Exception("No proposal found for this id.");
        }
        if (voteInProgress(memeId)) {
            throw new Exception("The voting timeframe for this id is still open.");
        }
        int votesFor = voteForMap.get(memeId).toInteger();
        int votesAgainst = voteAgainstMap.get(memeId).toInteger();
        boolean inFavor = votesFor > votesAgainst;
        if (inFavor && votesFor >= MIN_VOTES_IN_FAVOR) {
            int proposalKind = proposal.toInteger();
            if (proposalKind == CREATE) {
                String description = descriptionMap.get(memeId).toString();
                String url = urlMap.get(memeId).toString();
                String imageHash = imgHashMap.get(memeId).toString();
                boolean createMeme = (boolean) Contract.call(getMemeContract(), "createMeme",
                        CallFlags.All, new Object[] {memeId, description, url, imageHash});
                if (createMeme) {
                    onCreation.fire(memeId, description, url, imageHash);
                    clearProposal(memeId);
                    return true;
                }
            } else {
                boolean removeMeme = (boolean) Contract.call(getMemeContract(), "removeMeme",
                        CallFlags.All, new Object[] {memeId});
                if (removeMeme) {
                    onRemoval.fire(memeId);
                    clearProposal(memeId);
                    return true;
                }
            }
            return false;
        }
        onRemovingUnacceptedProposal.fire(memeId);
        clearProposal(memeId);
        return true;
    }

    private static boolean voteInProgress(String memeId) {
        int currentIndex = LedgerContract.currentIndex();
        int finalizationBlock = finalizationMap.get(memeId).toInteger();
        return finalizationBlock >= currentIndex;
    }

    // If the proposal was not accepted, there is nothing to execute.
    private static void clearProposal(String memeId) {
        proposalMap.delete(memeId);
        finalizationMap.delete(memeId);

        byte[] voteMapPrefix = createVotePrefix(memeId);
        StorageMap voteMap = ctx.createMap(voteMapPrefix);
        Iterator<Iterator.Struct<ByteString, ByteString>> iterator =
                Storage.find(ctx, voteMapPrefix, FindOptions.RemovePrefix);
        while (iterator.next()) {
            Iterator.Struct<ByteString, ByteString> pair = iterator.get();
            voteMap.delete(pair.key);
        }

        voteCountMap.delete(memeId);
        voteForMap.delete(memeId);
        voteAgainstMap.delete(memeId);

        descriptionMap.delete(memeId);
        urlMap.delete(memeId);
        imgHashMap.delete(memeId);
    }

    /**
     * Gets the proposal for the specified meme id.
     */
    @Safe
    public static Proposal getProposal(String memeId) {
        boolean create = proposalMap.get(memeId).toInteger() == CREATE;
        boolean voteInProgress = voteInProgress(memeId);
        int finalizationBlock = finalizationMap.get(memeId).toInteger();
        int votesInFavor = voteForMap.get(memeId).toInteger();
        int votesAgainst = voteAgainstMap.get(memeId).toInteger();

        if (create) {
            String description = descriptionMap.get(memeId).toString();
            String url = urlMap.get(memeId).toString();
            ByteString imageHash = imgHashMap.get(memeId);
            Meme meme = new Meme(memeId, description, url, imageHash);
            return new Proposal(meme, true, voteInProgress, finalizationBlock, votesInFavor,
                    votesAgainst);
        } else {
            Meme meme = (Meme) Contract.call(getMemeContract(), "getMeme", CallFlags.ReadOnly,
                    new Object[] {memeId});
            return new Proposal(meme, false, voteInProgress, finalizationBlock, votesInFavor,
                    votesAgainst);
        }
    }

    /**
     * Gets a list of proposals.
     */
    @Safe
    public static List<Proposal> getProposals(int startingIndex) {
        int finalIndex = startingIndex + MAX_GET_PROPOSALS;
        List<Proposal> proposals = new List<>();
        Iterator<Iterator.Struct<ByteString, ByteString>> iterator =
                Storage.find(ctx, toByteArray(PROPOSAL_PREFIX), FindOptions.RemovePrefix);
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
            Proposal proposal = getProposal(memeId);
            proposals.add(proposal);
            i++;
        }
        return proposals;
    }

}
