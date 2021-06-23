package com.axlabs;

import io.neow3j.compiler.CompilationUnit;
import io.neow3j.compiler.Compiler;
import io.neow3j.contract.ContractManagement;
import io.neow3j.contract.GasToken;
import io.neow3j.contract.NefFile;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.ContractManifest;
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.protocol.http.HttpService;
import io.neow3j.transaction.Signer;
import io.neow3j.transaction.exceptions.TransactionConfigurationException;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.wallet.Account;
import io.neow3j.wallet.Wallet;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static com.axlabs.NeoTestContainer.getNodeUrl;
import static io.neow3j.contract.ContractUtils.writeContractManifestFile;
import static io.neow3j.contract.ContractUtils.writeNefFile;
import static io.neow3j.contract.SmartContract.getContractHash;
import static io.neow3j.protocol.ObjectMapperFactory.getObjectMapper;
import static io.neow3j.transaction.Signer.calledByEntry;
import static io.neow3j.transaction.Signer.feeOnly;
import static io.neow3j.types.ContractParameter.bool;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.string;
import static io.neow3j.utils.Await.waitUntilBlockCountIsGreaterThan;
import static io.neow3j.utils.Await.waitUntilTransactionIsExecuted;
import static io.neow3j.wallet.Account.createMultiSigAccount;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class IntegrationTest {

    private static Neow3j neow3j;
    private static SmartContract governanceContract;
    private static SmartContract memeContract;

    private static final Path MEMES_NEF_FILE = Paths.get("./build/neow3j/Memes.nef");
    private static final Path MEMES_MANIFEST_FILE = Paths.get("./build/neow3j/Memes.manifest.json");
    private static final Path GOVERNANCE_NEF_FILE = Paths.get("./build/neow3j/MemeGovernance.nef");
    private static final Path GOVERNANCE_MANIFEST_FILE =
            Paths.get("./build/neow3j/MemeGovernance.manifest.json");

    private static final Account defaultAccount =
            Account.fromWIF("L1eV34wPoj9weqhGijdDLtVQzUpWGHszXXpdU9dPuh2nRFFzFa7E");
    private static final Account committee =
            createMultiSigAccount(singletonList(defaultAccount.getECKeyPair().getPublicKey()), 1);
    private static final Wallet committeeWallet = Wallet.withAccounts(committee, defaultAccount);
    private static final Account a1 =
            Account.fromWIF("L3cNMQUSrvUrHx1MzacwHiUeCWzqK2MLt5fPvJj9mz6L2rzYZpok");
    private static final Account a2 =
            Account.fromWIF("L1RgqMJEBjdXcuYCMYB6m7viQ9zjkNPjZPAKhhBoXxEsygNXENBb");
    private static final Account a3 =
            Account.fromWIF("Kzkwmjq4aygAHPYwCAhFYwrviar3E5JyiPuNYVcg2Ks88iLm4TmV");
    private static final Account a4 =
            Account.fromWIF("KzTJz7cKJM4dZDeFJroPPK2buag3nA1gWpJtLvoxuEcQUyC4hbzp");
    private static final Account a5 =
            Account.fromWIF("KxT5Fv5kXb82hybcm9vgigncTj69dDcb44RW1mGYrHsYBFo1FkN9");
    private static final Account a6 =
            Account.fromWIF("L4zaeMKdFewWoPTU7X86tNg6VkvbZqZsGgS6xL7Sm4pXYay4rsRP");
    private static final Account a7 =
            Account.fromWIF("Kyb13EoA3RikT7RCXGRzN8QdYmhoR89RcPVE61hLUWXAMk15p1wo");
    private static final Account a8 =
            Account.fromWIF("L2bZXerkt47c5oTE99dQrhx12NMSLHm2oy1nck4E8rSeTVUbFLiV");

    private static final Wallet wallet = Wallet.withAccounts(a1, a2, a3, a4, a5, a6, a7, a8);

    // Governance methods
    private static final String vote = "vote";
    private static final String proposeNewMeme = "proposeNewMeme";
    private static final String proposeRemoval = "proposeRemoval";
    private static final String execute = "execute";
    private static final String getVotingTime = "getVotingTime";
    private static final String getMinVotesInFavor = "getMinVotesInFavor";
    private static final String getMemeContract = "getMemeContract";
    private static final String initialize = "initialize";
    private static final String getProposal = "getProposal";

    private static final BigInteger votingTime = BigInteger.TEN;
    private static final BigInteger minVotesInFavor = new BigInteger("3");

    // Meme contract methods
    private static final String getMeme = "getMeme";
    private static final String getOwner = "getOwner";
    private static final String getMemes = "getMemes";

    @ClassRule
    public static NeoTestContainer neoTestContainer = new NeoTestContainer();

    @BeforeClass
    public static void setUp() throws Throwable {
        neow3j = Neow3j.build(new HttpService("http://localhost:40332"));
        neow3j = Neow3j.build(new HttpService(getNodeUrl(neoTestContainer)));
        compileContracts();
        fundAccounts(defaultAccount, a1, a2, a3, a4, a5, a6, a7, a8);
        memeContract = deployMemeContract();
        System.out.println("MemeContract: " + memeContract.getScriptHash());
        governanceContract = deployMemeGovernance();
        System.out.println("MemeGovernance: " + governanceContract.getScriptHash());
        initialize();
    }

    @Test
    public void testGetOwner() throws IOException {
        Hash160 memeOwner = memeContract.callFunctionReturningScriptHash(getOwner);
        assertThat(memeOwner, is(governanceContract.getScriptHash()));
    }

    @Test
    public void testGetOwner_gov() throws IOException {
        Hash160 govOwner = governanceContract.callFunctionReturningScriptHash(getOwner);
        assertThat(govOwner, is(Hash160.ZERO));
    }

    @Test
    public void testGetMemeContract() throws IOException {
        Hash160 linkedMemeContract =
                governanceContract.callFunctionReturningScriptHash(getMemeContract);
        assertThat(linkedMemeContract, is(memeContract.getScriptHash()));
    }

    @Test
    public void testGetVotingTime() throws IOException {
        BigInteger votingTime = governanceContract.callFuncReturningInt(getVotingTime);
        assertThat(votingTime, is(votingTime));
    }

    @Test
    public void getMinVotesInFavor() throws IOException {
        BigInteger minVotes = governanceContract.callFuncReturningInt(getMinVotesInFavor);
        assertThat(minVotes, is(minVotesInFavor));
    }

    @Test
    public void testProposeNewMeme() throws Throwable {
        ContractParameter memeId = string("proposeNewMeme");
        Hash256 hash = setupBasicProposal(memeId, true);

        IntProposal proposal = getProposal(memeId);

        // Transaction height is 1 higher than the current index that was computed when executing
        // the script.
        // Finalization block is the last block any vote is allowed.
        BigInteger txHeight = neow3j.getTransactionHeight(hash).send().getHeight();
        assertThat(proposal.finalizationBlock,
                is(txHeight.add(votingTime).subtract(BigInteger.ONE)));

        assertTrue(proposal.create);
        assertTrue(proposal.voteInProgress);
        assertThat(proposal.votesInFavor, is(BigInteger.ZERO));
        assertThat(proposal.votesAgainst, is(BigInteger.ZERO));
    }

    @Test
    public void testVote() throws Throwable {
        ContractParameter memeId = string("testVote");
        setupBasicProposal(memeId, true);

        Hash256 voteFor1 = vote(memeId, a1, true);
        Hash256 voteFor2 = vote(memeId, a2, true);
        Hash256 voteFor3 = vote(memeId, a3, true);
        waitUntilTransactionIsExecuted(voteFor1, neow3j);
        waitUntilTransactionIsExecuted(voteFor2, neow3j);
        waitUntilTransactionIsExecuted(voteFor3, neow3j);

        IntProposal proposal = getProposal(memeId);

        assertThat(proposal.votesInFavor, is(new BigInteger("3")));
        assertThat(proposal.votesAgainst, is(BigInteger.ZERO));

        Hash256 voteAgainst = vote(memeId, a4, false);
        waitUntilTransactionIsExecuted(voteAgainst, neow3j);
        try {
            vote(memeId, a4, false);
            fail();
        } catch (TransactionConfigurationException e) {
            assertThat(e.getMessage(), containsString("Already voted"));
        }

        proposal = getProposal(memeId);
        assertThat(proposal.votesInFavor, is(new BigInteger("3")));
        assertThat(proposal.votesAgainst, is(BigInteger.ONE));
    }

    @Test
    public void testExecuteCreation() throws Throwable {
        String memeIdString = "executeCreation";
        ContractParameter memeId = string(memeIdString);
        String description = "coolDescriptionString";
        String url = "AxLabsUrlString";
        String imgHash = "awesomeImageHashString";
        createProposal(memeId, description, url, imgHash);

        Hash256 voteFor1 = vote(memeId, a1, true);
        Hash256 voteFor2 = vote(memeId, a2, true);
        Hash256 voteFor3 = vote(memeId, a3, true);
        waitUntilTransactionIsExecuted(voteFor1, neow3j);
        waitUntilTransactionIsExecuted(voteFor2, neow3j);
        waitUntilTransactionIsExecuted(voteFor3, neow3j);

        waitUntilVotingIsClosed(memeId);

        Hash256 exec = execProp(memeId, a4);
        waitUntilTransactionIsExecuted(exec, neow3j);

        List<StackItem> meme = memeContract.callInvokeFunction(getMeme, asList(memeId))
                .getInvocationResult().getStack().get(0).getList();
        assertThat(meme, hasSize(4));
        assertThat(meme.get(0).getString(), is(memeIdString));
        assertThat(meme.get(1).getString(), is(description));
        assertThat(meme.get(2).getString(), is(url));
        assertThat(meme.get(3).getString(), is(imgHash));
    }

    @Test
    public void testExecuteRemoval() throws Throwable {
        ContractParameter memeId = string("executeRemoval");
        createMemeThroughVote(memeId);
        removeProposal(memeId);

        Hash256 voteFor1 = vote(memeId, a1, true);
        Hash256 voteFor2 = vote(memeId, a2, true);
        Hash256 voteFor3 = vote(memeId, a3, true);
        Hash256 voteFor4 = vote(memeId, a4, true);
        Hash256 voteFor5 = vote(memeId, a5, true);
        Hash256 voteAgainst6 = vote(memeId, a6, false);
        Hash256 voteAgainst7 = vote(memeId, a7, false);
        Hash256 voteAgainst8 = vote(memeId, a8, false);
        waitUntilTransactionIsExecuted(voteFor1, neow3j);
        waitUntilTransactionIsExecuted(voteFor2, neow3j);
        waitUntilTransactionIsExecuted(voteFor3, neow3j);
        waitUntilTransactionIsExecuted(voteFor4, neow3j);
        waitUntilTransactionIsExecuted(voteFor5, neow3j);
        waitUntilTransactionIsExecuted(voteAgainst6, neow3j);
        waitUntilTransactionIsExecuted(voteAgainst7, neow3j);
        waitUntilTransactionIsExecuted(voteAgainst8, neow3j);

        waitUntilVotingIsClosed(memeId);

        Hash256 exec = execProp(memeId, a6);
        waitUntilTransactionIsExecuted(exec, neow3j);

        String exception = memeContract.callInvokeFunction(getMeme, asList(memeId))
                .getInvocationResult().getException();
        // Check whether the meme was successfully removed.
        assertThat(exception, containsString("No meme found for this id."));
    }

    // Creates a proposal that is not accepted and creates a new proposal with the same meme id.
    // This should overwrite the existing proposal.
    @Test
    public void testOverwriteUnacceptedCreateProposal() throws Throwable {
        String memeIdString = "overwriteUnacceptedCreateProposal";
        ContractParameter memeId = string(memeIdString);
        createProposal(memeId, "description1", "url1", "imgHash1");

        waitUntilVotingIsClosed(memeId);

        createProposal(memeId, "description2", "url2", "imgHash2");
        Hash256 voteFor1 = vote(memeId, a1, true);
        Hash256 voteFor2 = vote(memeId, a2, true);
        Hash256 voteFor3 = vote(memeId, a3, true);
        waitUntilTransactionIsExecuted(voteFor1, neow3j);
        waitUntilTransactionIsExecuted(voteFor2, neow3j);
        waitUntilTransactionIsExecuted(voteFor3, neow3j);

        waitUntilVotingIsClosed(memeId);

        Hash256 exec = execProp(memeId, a6);
        waitUntilTransactionIsExecuted(exec, neow3j);

        List<StackItem> meme = memeContract.callInvokeFunction(getMeme, asList(memeId))
                .getInvocationResult().getStack().get(0).getList();
        assertThat(meme, hasSize(4));
        assertThat(meme.get(0).getString(), is(memeIdString));
        assertThat(meme.get(1).getString(), is("description2"));
        assertThat(meme.get(2).getString(), is("url2"));
        assertThat(meme.get(3).getString(), is("imgHash2"));
    }

    // Creates a proposal that is not accepted and creates a new proposal with the same meme id.
    // This should overwrite the existing proposal.
    @Test
    public void testOverwriteUnacceptedRemoveProposal() throws Throwable {
        ContractParameter memeId = string("testOverwriteUnacceptedRemoveProposal");
        createMemeThroughVote(memeId);

        removeProposal(memeId);
        waitUntilVotingIsClosed(memeId);

        removeProposal(memeId);

        Hash256 voteFor1 = vote(memeId, a1, true);
        Hash256 voteFor2 = vote(memeId, a2, true);
        Hash256 voteFor3 = vote(memeId, a3, true);
        waitUntilTransactionIsExecuted(voteFor1, neow3j);
        waitUntilTransactionIsExecuted(voteFor2, neow3j);
        waitUntilTransactionIsExecuted(voteFor3, neow3j);

        waitUntilVotingIsClosed(memeId);

        Hash256 exec = execProp(memeId, a6);
        waitUntilTransactionIsExecuted(exec, neow3j);

        String exception = memeContract.callInvokeFunction(getMeme, asList(memeId))
                .getInvocationResult().getException();
        // Check whether the meme was successfully removed.
        assertThat(exception, containsString("No meme found for this id."));
    }

    @Test
    public void testGetMemes() throws Throwable {
        ContractParameter memeId1 = string("getMemes1");
        ContractParameter memeId2 = string("getMemes2");
        ContractParameter memeId3 = string("getMemes3");
        ContractParameter memeId4 = string("getMemes4");
        createMemeThroughVote(memeId1, "d1", "u1", "i1");
        createMemeThroughVote(memeId2, "d2", "u2", "i2");
        createMemeThroughVote(memeId3, "d3", "u3", "i3");
        createMemeThroughVote(memeId4, "d4", "u4", "i4");

        List<StackItem> memes = memeContract.callInvokeFunction(getMemes, asList(integer(0)))
                .getInvocationResult().getStack().get(0).getList();

        assertThat(memes, hasSize(4));

        List<StackItem> meme = memes.get(0).getList();
        assertThat(meme.get(0).getString(), is("getMemes1"));
        assertThat(meme.get(1).getString(), is("d1"));
        assertThat(meme.get(2).getString(), is("u1"));
        assertThat(meme.get(3).getString(), is("i1"));

        meme = memes.get(3).getList();
        assertThat(meme.get(0).getString(), is("getMemes4"));
        assertThat(meme.get(1).getString(), is("d4"));
        assertThat(meme.get(2).getString(), is("u4"));
        assertThat(meme.get(3).getString(), is("i4"));
    }

    // Helper methods

    private static void initialize() throws Throwable {
        Hash160 memeContractOnGovernance =
                governanceContract.callFunctionReturningScriptHash(getMemeContract);
        if (!memeContractOnGovernance.equals(memeContract.getScriptHash())) {
            System.out.println("Initializing");
            Hash256 txHash = governanceContract.invokeFunction(initialize,
                    hash160(IntegrationTest.memeContract.getScriptHash()))
                    .wallet(committeeWallet)
                    .signers(new Signer.Builder().account(defaultAccount)
                            .allowedContracts(governanceContract.getScriptHash(),
                                    memeContract.getScriptHash())
                            .build()
                    )
                    .sign()
                    .send()
                    .getSendRawTransaction()
                    .getHash();
            waitUntilTransactionIsExecuted(txHash, neow3j);
        }
        memeContractOnGovernance =
                governanceContract.callFunctionReturningScriptHash(getMemeContract);
        System.out.println(
                "Meme contract hash on governance contract: " + memeContractOnGovernance);
        Hash160 ownerOnMeme = memeContract.callFunctionReturningScriptHash(getOwner);
        System.out.println("Owner of Meme contract: " + ownerOnMeme);
    }

    private static void compileContracts() throws IOException {
        compileContract(MemeContract.class.getCanonicalName());
        compileContract(MemeGovernance.class.getCanonicalName());
    }

    private static void compileContract(String canonicalName) throws IOException {
        // Compile the NonFungibleToken contract and construct a SmartContract object from it.
        CompilationUnit res = new Compiler().compile(canonicalName);

        // Write contract (compiled, NEF) to the disk
        Path buildNeow3jPath = Paths.get("build", "neow3j");
        buildNeow3jPath.toFile().mkdirs();
        writeNefFile(res.getNefFile(), res.getManifest().getName(), buildNeow3jPath);

        // Write manifest to the disk
        writeContractManifestFile(res.getManifest(), buildNeow3jPath);
    }

    private static void fundAccounts(Account defaultAccount, Account... accounts) throws Throwable {
        GasToken gasToken = new GasToken(neow3j);
        BigInteger amount = gasToken.toFractions(new BigDecimal("2000"));
        BigInteger minAmount = gasToken.toFractions(new BigDecimal("500"));
        List<Hash256> txHashes = new ArrayList<>();
        for (Account a : accounts) {
            if (gasToken.getBalanceOf(a).compareTo(minAmount) < 0) {
                Hash256 txHash = gasToken
                        .transferFromSpecificAccounts(committeeWallet, a.getScriptHash(),
                                amount, committee.getScriptHash())
                        .sign()
                        .send()
                        .getSendRawTransaction()
                        .getHash();
                txHashes.add(txHash);
                System.out.println("Funded account " + a.getAddress());
            }
        }
        for (Hash256 h : txHashes) {
            waitUntilTransactionIsExecuted(h, neow3j);
        }
    }

    private static SmartContract deployMemeContract() throws Throwable {
        File nefFile = new File(MEMES_NEF_FILE.toUri());
        NefFile nef = NefFile.readFromFile(nefFile);

        File manifestFile = new File(MEMES_MANIFEST_FILE.toUri());
        ContractManifest manifest = getObjectMapper()
                .readValue(manifestFile, ContractManifest.class);
        try {
            Hash256 txHash = new ContractManagement(neow3j).deploy(nef, manifest)
                    .wallet(committeeWallet)
                    .signers(feeOnly(committee))
                    .sign()
                    .send()
                    .getSendRawTransaction()
                    .getHash();
            waitUntilTransactionIsExecuted(txHash, neow3j);
            System.out.println("Deployed MemeContract");
        } catch (TransactionConfigurationException e) {
            System.out.println(e.getMessage());
        }
        return new SmartContract(
                getContractHash(committee.getScriptHash(), nef.getCheckSumAsInteger(),
                        manifest.getName()),
                neow3j);
    }

    private static SmartContract deployMemeGovernance() throws Throwable {
        File nefFile = new File(GOVERNANCE_NEF_FILE.toUri());
        NefFile nef = NefFile.readFromFile(nefFile);

        File manifestFile = new File(GOVERNANCE_MANIFEST_FILE.toUri());
        ContractManifest manifest = getObjectMapper()
                .readValue(manifestFile, ContractManifest.class);

        try {
            Hash256 txHash = new ContractManagement(neow3j).deploy(nef, manifest)
                    .wallet(committeeWallet)
                    .signers(feeOnly(committee))
                    .sign()
                    .send()
                    .getSendRawTransaction()
                    .getHash();
            waitUntilTransactionIsExecuted(txHash, neow3j);
            System.out.println("Deployed MemeGovernance");
        } catch (TransactionConfigurationException e) {
            System.out.println(e.getMessage());
        }
        return new SmartContract(
                getContractHash(committee.getScriptHash(), nef.getCheckSumAsInteger(),
                        manifest.getName()),
                neow3j);
    }

    private Hash256 createProposal(ContractParameter memeId, String description,
            String url, String imgHash) throws Throwable {
        Hash256 hash = governanceContract.invokeFunction(proposeNewMeme, memeId,
                string(description), string(url), string(imgHash))
                .wallet(committeeWallet)
                .signers(feeOnly(committee))
                .sign()
                .send()
                .getSendRawTransaction()
                .getHash();
        waitUntilTransactionIsExecuted(hash, neow3j);
        return hash;
    }

    private Hash256 removeProposal(ContractParameter memeId) throws Throwable {
        Hash256 hash = governanceContract.invokeFunction(proposeRemoval, memeId)
                .wallet(committeeWallet)
                .signers(feeOnly(committee))
                .sign()
                .send()
                .getSendRawTransaction()
                .getHash();
        waitUntilTransactionIsExecuted(hash, neow3j);
        return hash;
    }

    private Hash256 setupBasicProposal(ContractParameter memeId, boolean create) throws Throwable {
        if (create) {
            return createProposal(memeId, "desc", "url", "imgHash");
        } else {
            return removeProposal(memeId);
        }
    }

    private Hash256 vote(ContractParameter memeId, Account a, boolean inFavor) throws Throwable {
        return governanceContract.invokeFunction(
                vote, memeId, hash160(a.getScriptHash()), bool(inFavor))
                .wallet(wallet)
                .signers(calledByEntry(a))
                .sign()
                .send()
                .getSendRawTransaction()
                .getHash();
    }

    private Hash256 execProp(ContractParameter memeId, Account a) throws Throwable {
        return governanceContract.invokeFunction(execute, memeId)
                .wallet(wallet)
                .signers(feeOnly(a))
                .sign()
                .send()
                .getSendRawTransaction()
                .getHash();
    }

    private void createMemeThroughVote(ContractParameter memeId, String description, String url,
            String imgHash) throws Throwable {
        createProposal(memeId, description, url, imgHash);

        Hash256 voteFor1 = vote(memeId, a1, true);
        Hash256 voteFor2 = vote(memeId, a2, true);
        Hash256 voteFor3 = vote(memeId, a3, true);
        waitUntilTransactionIsExecuted(voteFor1, neow3j);
        waitUntilTransactionIsExecuted(voteFor2, neow3j);
        waitUntilTransactionIsExecuted(voteFor3, neow3j);

        IntProposal proposal = getProposal(memeId);
        waitUntilBlockCountIsGreaterThan(neow3j, proposal.finalizationBlock.add(BigInteger.ONE));

        Hash256 exec = execProp(memeId, a1);
        waitUntilTransactionIsExecuted(exec, neow3j);
    }

    private void createMemeThroughVote(ContractParameter memeId) throws Throwable {
        createMemeThroughVote(memeId, "coolDescription", "AxLabsUrl", "awesomeImageHash");
    }

    private void waitUntilVotingIsClosed(ContractParameter memeId) throws IOException {
        waitUntilBlockCountIsGreaterThan(neow3j,
                getProposal(memeId).finalizationBlock.add(BigInteger.ONE));
    }

    private static IntProposal getProposal(ContractParameter memeId) throws IOException {
        List<StackItem> proposalItem = governanceContract
                .callInvokeFunction(getProposal, asList(memeId))
                .getInvocationResult().getStack().get(0).getList();
        IntMeme meme = getMemeFromStackItem(proposalItem.get(0));
        boolean create = proposalItem.get(1).getBoolean();
        boolean voteInProgress = proposalItem.get(2).getBoolean();
        BigInteger finalizationBlock = proposalItem.get(3).getInteger();
        BigInteger votesInFavor = proposalItem.get(4).getInteger();
        BigInteger votesAgainst = proposalItem.get(5).getInteger();
        return new IntProposal(meme, create, voteInProgress, finalizationBlock, votesInFavor,
                votesAgainst);
    }

    private static IntMeme getMemeFromStackItem(StackItem memeItem) {
        List<StackItem> meme = memeItem.getList();
        String id = meme.get(0).getString();
        String description = meme.get(1).getString();
        String url = meme.get(2).getString();
        String imageHash = meme.get(3).getString();
        return new IntMeme(id, description, url, imageHash);
    }

    public static class IntProposal {
        public IntMeme meme;
        public Boolean create;
        public Boolean voteInProgress;
        public BigInteger finalizationBlock;
        public BigInteger votesInFavor;
        public BigInteger votesAgainst;

        public IntProposal(IntMeme meme, Boolean create, Boolean voteInProgress,
                BigInteger finalizationBlock, BigInteger votesInFavor, BigInteger votesAgainst) {
            this.meme = meme;
            this.create = create;
            this.voteInProgress = voteInProgress;
            this.finalizationBlock = finalizationBlock;
            this.votesInFavor = votesInFavor;
            this.votesAgainst = votesAgainst;
        }
    }

    public static class IntMeme {
        public String id;
        public String description;
        public String url;
        public String imageHash;

        public IntMeme(String id, String description, String url, String imageHash) {
            this.id = id;
            this.description = description;
            this.url = url;
            this.imageHash = imageHash;
        }
    }

}
