package io.neow3j;

import io.neow3j.compiler.CompilationUnit;
import io.neow3j.compiler.Compiler;
import io.neow3j.contract.ContractManagement;
import io.neow3j.contract.ContractParameter;
import io.neow3j.contract.GasToken;
import io.neow3j.contract.Hash160;
import io.neow3j.contract.Hash256;
import io.neow3j.contract.NefFile;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.methods.response.ContractManifest;
import io.neow3j.protocol.core.methods.response.StackItem;
import io.neow3j.protocol.http.HttpService;
import io.neow3j.transaction.exceptions.TransactionConfigurationException;
import io.neow3j.wallet.Account;
import io.neow3j.wallet.Wallet;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static io.neow3j.contract.ContractParameter.bool;
import static io.neow3j.contract.ContractParameter.byteArrayFromString;
import static io.neow3j.contract.ContractParameter.hash160;
import static io.neow3j.contract.ContractParameter.string;
import static io.neow3j.contract.ContractUtils.writeContractManifestFile;
import static io.neow3j.contract.ContractUtils.writeNefFile;
import static io.neow3j.contract.SmartContract.getContractHash;
import static io.neow3j.protocol.ObjectMapperFactory.getObjectMapper;
import static io.neow3j.transaction.Signer.calledByEntry;
import static io.neow3j.transaction.Signer.global;
import static io.neow3j.utils.Await.waitUntilBlockCountIsGreaterThan;
import static io.neow3j.utils.Await.waitUntilTransactionIsExecuted;
import static io.neow3j.wallet.Account.createMultiSigAccount;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
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
    private static final String proposeCreation = "proposeCreation";
    private static final String proposeRemoval = "proposeRemoval";
    private static final String execute = "execute";
    private static final String getFinalizationBlock = "getFinalizationBlock";
    private static final String getVotesFor = "getVotesFor";
    private static final String getVotesAgainst = "getVotesAgainst";
    private static final String getTotalVoteCount = "getTotalVoteCount";
    private static final String getVotingTime = "getVotingTime";
    private static final String voteOpen = "voteOpen";
    private static final String getMemeContract = "getMemeContract";
    private static final String setMemeContract = "setMemeContract";

    private static final BigInteger votingTime = BigInteger.TEN;

    // Meme contract methods
    private static final String getMeme = "getMeme";
    private static final String getOwner = "getOwner";
    private static final String getInitialOwner = "getInitialOwner";

    @BeforeClass
    public static void setUp() throws Throwable {
        neow3j = Neow3j.build(new HttpService("http://localhost:40332"));
        compileContracts();
        fundAccounts(defaultAccount, a1, a2, a3, a4, a5, a6, a7, a8);
        memeContract = deployMemeContract();
        System.out.println("MemeContract: " + memeContract.getScriptHash());
        governanceContract = deployMemeGovernance();
        System.out.println("MemeGovernance: " + governanceContract.getScriptHash());
        linkMemeContract();
    }

    private static void linkMemeContract() throws Throwable {
        Hash160 memeContractOnGovernance =
                governanceContract.callFunctionReturningScriptHash(getMemeContract);
        if (!memeContractOnGovernance.equals(memeContract.getScriptHash())) {
            System.out.println("setting new meme on gov");
            Hash256 txHash = governanceContract.invokeFunction(setMemeContract,
                    hash160(IntegrationTest.memeContract.getScriptHash()))
                    .wallet(committeeWallet)
                    .signers(global(committee))
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
                    .signers(calledByEntry(committee))
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
                    .signers(calledByEntry(committee))
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
        Hash256 hash = governanceContract.invokeFunction(proposeCreation, memeId,
                string(description), string(url), string(imgHash))
                .wallet(committeeWallet)
                .signers(calledByEntry(committee))
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
                .signers(calledByEntry(committee))
                .sign()
                .send()
                .getSendRawTransaction()
                .getHash();
        waitUntilTransactionIsExecuted(hash, neow3j);
        return hash;
    }

    private Hash256 basicProposal(ContractParameter memeId, boolean create) throws Throwable {
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
                .signers(global(a))
                .sign()
                .send()
                .getSendRawTransaction()
                .getHash();
    }

    private void createMemeThroughVote(ContractParameter memeId) throws Throwable {
        String description = "coolDescription";
        String url = "AxLabsUrl";
        String imgHash = "awesomeImageHash";
        createProposal(memeId, description, url, imgHash);

        Hash256 voteFor1 = vote(memeId, a1, true);
        Hash256 voteFor2 = vote(memeId, a2, true);
        Hash256 voteFor3 = vote(memeId, a3, true);
        waitUntilTransactionIsExecuted(voteFor1, neow3j);
        waitUntilTransactionIsExecuted(voteFor2, neow3j);
        waitUntilTransactionIsExecuted(voteFor3, neow3j);

        BigInteger finalizationBlock =
                governanceContract.callFuncReturningInt(getFinalizationBlock, memeId);
        waitUntilBlockCountIsGreaterThan(neow3j, finalizationBlock.add(BigInteger.ONE));

        Hash256 exec = execProp(memeId, a1);
        waitUntilTransactionIsExecuted(exec, neow3j);
    }

    @Test
    public void testGetInitialOwner() throws IOException {
        Hash160 memeOwner = memeContract.callFunctionReturningScriptHash(getInitialOwner);
        assertThat(memeOwner, is(committee.getScriptHash()));
    }

    @Test
    public void testGetOwner() throws IOException {
        Hash160 memeOwner = memeContract.callFunctionReturningScriptHash(getOwner);
        assertThat(memeOwner, is(governanceContract.getScriptHash()));
    }

    @Test
    public void testGetOwner_gov() throws IOException {
        Hash160 govOwner = governanceContract.callFunctionReturningScriptHash(getOwner);
        assertThat(govOwner, is(committee.getScriptHash()));
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
        assertThat(votingTime, is(BigInteger.TEN));
    }

    @Test
    public void testProposeCreation() throws Throwable {
        ContractParameter memeId = byteArrayFromString("testProposeCreation" + N);
        Hash256 hash = basicProposal(memeId, true);
        // Transaction height is 1 higher than the current index that was computed when executing
        // the script.
        BigInteger txHeight = neow3j.getTransactionHeight(hash).send().getHeight();
        // Finalization block is the last block any vote is allowed.
        BigInteger finalizationBlock =
                governanceContract.callFuncReturningInt(getFinalizationBlock, memeId);
        assertThat(finalizationBlock, is(txHeight.add(votingTime).subtract(BigInteger.ONE)));

        BigInteger votes = governanceContract.callFuncReturningInt(getTotalVoteCount, memeId);
        BigInteger votesFor = governanceContract.callFuncReturningInt(getVotesFor, memeId);
        BigInteger votesAgainst = governanceContract.callFuncReturningInt(getVotesAgainst, memeId);
        boolean open = governanceContract.callFuncReturningBool(voteOpen, memeId);

        assertThat(votes, is(BigInteger.ZERO));
        assertThat(votesFor, is(BigInteger.ZERO));
        assertThat(votesAgainst, is(BigInteger.ZERO));
        assertTrue(open);
    }

    @Test
    public void testVote() throws Throwable {
        ContractParameter memeId = byteArrayFromString("testVote" + N);
        basicProposal(memeId, true);

        Hash256 voteFor1 = vote(memeId, a1, true);
        Hash256 voteFor2 = vote(memeId, a2, true);
        Hash256 voteFor3 = vote(memeId, a3, true);
        waitUntilTransactionIsExecuted(voteFor1, neow3j);
        waitUntilTransactionIsExecuted(voteFor2, neow3j);
        waitUntilTransactionIsExecuted(voteFor3, neow3j);

        BigInteger votes = governanceContract.callFuncReturningInt(getTotalVoteCount, memeId);
        BigInteger votesFor = governanceContract.callFuncReturningInt(getVotesFor, memeId);
        BigInteger votesAgainst = governanceContract.callFuncReturningInt(getVotesAgainst, memeId);

        assertThat(votes, is(new BigInteger("3")));
        assertThat(votesFor, is(new BigInteger("3")));
        assertThat(votesAgainst, is(BigInteger.ZERO));

        Hash256 voteAgainst = vote(memeId, a4, false);
        waitUntilTransactionIsExecuted(voteAgainst, neow3j);
        try {
            vote(memeId, a4, false);
            fail();
        } catch (TransactionConfigurationException e) {
            assertThat(e.getMessage(), containsString("Already voted"));
        }

        votes = governanceContract.callFuncReturningInt(getTotalVoteCount, memeId);
        votesFor = governanceContract.callFuncReturningInt(getVotesFor, memeId);
        votesAgainst = governanceContract.callFuncReturningInt(getVotesAgainst, memeId);

        assertThat(votes, is(new BigInteger("4")));
        assertThat(votesFor, is(new BigInteger("3")));
        assertThat(votesAgainst, is(BigInteger.ONE));
    }

    @Test
    public void testExecuteCreation() throws Throwable {
        ContractParameter memeId = byteArrayFromString("testExecute" + N);
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

        BigInteger finalizationBlock =
                governanceContract.callFuncReturningInt(getFinalizationBlock, memeId);
        waitUntilBlockCountIsGreaterThan(neow3j, finalizationBlock.add(BigInteger.ONE));

        boolean isOpen = governanceContract.callFuncReturningBool(voteOpen, memeId);
        assertFalse(isOpen);

        Hash256 exec = execProp(memeId, a4);
        waitUntilTransactionIsExecuted(exec, neow3j);

        List<StackItem> meme = memeContract.callInvokeFunction(getMeme, asList(memeId))
                .getInvocationResult().getStack().get(0).getList();
        assertThat(meme, hasSize(3));
        assertThat(meme.get(0).getString(), is(description));
        assertThat(meme.get(1).getString(), is(url));
        assertThat(meme.get(2).getString(), is(imgHash));
    }

    @Test
    public void testExecuteRemoval() throws Throwable {
        ContractParameter memeId = byteArrayFromString("testRemoveMeme" + N);
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

        BigInteger finalizationBlock =
                governanceContract.callFuncReturningInt(getFinalizationBlock, memeId);
        waitUntilBlockCountIsGreaterThan(neow3j, finalizationBlock.add(BigInteger.ONE));

        boolean isOpen = governanceContract.callFuncReturningBool(voteOpen, memeId);
        assertFalse(isOpen);

        Hash256 exec = execProp(memeId, a6);
        waitUntilTransactionIsExecuted(exec, neow3j);

        String exception = memeContract.callInvokeFunction(getMeme, asList(memeId))
                .getInvocationResult().getException();
        assertThat(exception, containsString("No meme found for this id."));
    }

    private static final String N = "4";

}
