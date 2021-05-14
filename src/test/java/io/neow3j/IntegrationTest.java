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
import java.util.concurrent.TimeUnit;

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
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class IntegrationTest {

    private static Neow3j neow3j;
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
    private static final Account a9 =
            Account.fromWIF("L4zdc4z3DNmJ29UiCxQkjBemyEiFjDSUsw9XQz7rgDcgWzxtLjxs");

    private static final String vote = "vote";
    private static final String proposeCreation = "proposeCreation";
    private static final String proposeRemoval = "proposeRemoval";
    private static final String execute = "execute";
    private static final String getFinalizationBlock = "getFinalizationBlock";
    private static final String getVotesFor = "getVotesFor";
    private static final String getVotesAgainst = "getVotesAgainst";
    private static final String getTotalVoteCount = "getTotalVoteCount";
    private static final String voteOpen = "voteOpen";
    private static final String getMeme = "getMeme";

    private static final Wallet wallet = Wallet.withAccounts(a1, a2, a3, a4, a5, a6, a7);

    private static final Path MEMES_NEF_FILE = Paths.get("./build/neow3j/Memes.nef");
    private static final Path MEMES_MANIFEST_FILE = Paths.get("./build/neow3j/Memes.manifest.json");

    private static final Path GOVERNANCE_NEF_FILE = Paths.get("./build/neow3j/MemeGovernance.nef");
    private static final Path GOVERNANCE_MANIFEST_FILE =
            Paths.get("./build/neow3j/MemeGovernance.manifest.json");

    private static SmartContract memeContract;
    private static SmartContract gov;

    @BeforeClass
    public static void setUp() throws Throwable {
        neow3j = Neow3j.build(new HttpService("http://localhost:40332"));
        compileContracts();
        fundAccounts(defaultAccount, a1, a2, a3, a4, a5, a6, a7, a8, a9);
        memeContract = deployMemeContract();
        System.out.println("MemeContract: " + memeContract.getScriptHash());
        gov = deployMemeGovernance();
        setMemeContract();
        System.out.println("MemeGovernance: " + gov.getScriptHash());
    }

    private static void setMemeContract() throws Throwable {
        Hash160 memeContractOnGovernance =
                gov.callFunctionReturningScriptHash("getOwner");
        if (!memeContractOnGovernance.equals(memeContract.getScriptHash())) {
            Hash256 txHash = gov.invokeFunction("setMemeContract",
                    hash160(IntegrationTest.memeContract.getScriptHash()))
                    .wallet(committeeWallet)
                    .signers(calledByEntry(committee))
                    .sign()
                    .send()
                    .getSendRawTransaction()
                    .getHash();
            waitUntilTransactionIsExecuted(txHash, neow3j);
        }
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
        Hash256 txHash;
        for (Account a : accounts) {
            if (gasToken.getBalanceOf(a).compareTo(minAmount) < 0) {
                txHash = gasToken
                        .transferFromSpecificAccounts(committeeWallet, a.getScriptHash(),
                                amount, committee.getScriptHash())
                        .sign()
                        .send()
                        .getSendRawTransaction()
                        .getHash();
                waitUntilTransactionIsExecuted(txHash, neow3j);
                System.out.println("Funded account " + a.getAddress());
            }
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
        Hash256 hash = gov.invokeFunction(proposeCreation, memeId,
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
        Hash256 hash = gov.invokeFunction(proposeRemoval, memeId)
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
        return gov.invokeFunction(
                vote, memeId, hash160(a.getScriptHash()), bool(inFavor))
                .wallet(wallet)
                .signers(calledByEntry(a))
                .sign()
                .send()
                .getSendRawTransaction()
                .getHash();
    }

    private Hash256 execProp(ContractParameter memeId, Account a) throws Throwable {
        return gov.invokeFunction(execute, memeId)
                .wallet(wallet)
                .signers(global(a))
                .sign()
                .send()
                .getSendRawTransaction()
                .getHash();
    }

    @Test
    public void testGetOwner() throws IOException {
        Hash160 govOwner = gov.callFunctionReturningScriptHash("getOwner");
        assertThat(govOwner, is(committee.getScriptHash()));
    }

    @Test
    public void testGetVotingTime() throws IOException {
        BigInteger votingTime = gov.callFuncReturningInt("getVotingTime");
        assertThat(votingTime, is(BigInteger.TEN));
    }

    @Test
    public void testProposeCreation() throws Throwable {
        ContractParameter memeId = byteArrayFromString("pc1MemeId12" + N);
        Hash256 hash = basicProposal(memeId, true);
        BigInteger creationBlockIndex = neow3j.getTransactionHeight(hash).send().getHeight();
        BigInteger finalizationBlock = gov.callFuncReturningInt("getFinalizationBlock", memeId);
        assertThat(finalizationBlock, is(creationBlockIndex.add(BigInteger.TEN)));

        BigInteger votes = gov.callFuncReturningInt(getTotalVoteCount, memeId);
        BigInteger votesFor = gov.callFuncReturningInt(getVotesFor, memeId);
        BigInteger votesAgainst = gov.callFuncReturningInt(getVotesAgainst, memeId);
        boolean open = gov.callFuncReturningBool(voteOpen, memeId);

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

        BigInteger votes = gov.callFuncReturningInt(getTotalVoteCount, memeId);
        BigInteger votesFor = gov.callFuncReturningInt(getVotesFor, memeId);
        BigInteger votesAgainst = gov.callFuncReturningInt(getVotesAgainst, memeId);

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

        votes = gov.callFuncReturningInt(getTotalVoteCount, memeId);
        votesFor = gov.callFuncReturningInt(getVotesFor, memeId);
        votesAgainst = gov.callFuncReturningInt(getVotesAgainst, memeId);

        assertThat(votes, is(new BigInteger("4")));
        assertThat(votesFor, is(new BigInteger("3")));
        assertThat(votesAgainst, is(BigInteger.ONE));
    }

    private static final String N = "27";

    @Test
    public void testExecute() throws Throwable {
        ContractParameter memeId = byteArrayFromString("testExecute" + N);
        String description = "desc";
        String url = "url";
        String imgHash = "imgHash";
        Hash256 proposal = createProposal(memeId, description, url, imgHash);

        Hash256 voteFor1 = vote(memeId, a1, true);
        Hash256 voteFor2 = vote(memeId, a2, true);
        Hash256 voteFor3 = vote(memeId, a3, true);
        waitUntilTransactionIsExecuted(voteFor1, neow3j);
        waitUntilTransactionIsExecuted(voteFor2, neow3j);
        waitUntilTransactionIsExecuted(voteFor3, neow3j);

        BigInteger finalizationBlock = gov.callFuncReturningInt(getFinalizationBlock, memeId);
        waitUntilBlockCountIsGreaterThan(neow3j, finalizationBlock);

        boolean isOpen = gov.callFuncReturningBool(voteOpen, memeId);
        assertFalse(isOpen);

        Hash256 exec = execProp(memeId, a1);
        waitUntilTransactionIsExecuted(exec, neow3j);

        String response = memeContract.callInvokeFunction(getMeme)
                .getInvocationResult().getStack().get(0).toString();
        System.out.println(response);
        String[] split = response.split(",");
        assertThat(split[0], is(description));
        assertThat(split[1], is(url));
        assertThat(split[2], is(imgHash));
    }

}
