package com.axlabs;

import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.string;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Ignore;
import org.junit.Test;
import io.neow3j.contract.ContractManagement;
import io.neow3j.contract.NefFile;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.ContractManifest;
import io.neow3j.protocol.core.response.NeoInvokeFunction;
import io.neow3j.protocol.core.response.NeoSendRawTransaction;
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.protocol.http.HttpService;
import io.neow3j.transaction.Signer;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;
import io.neow3j.wallet.Wallet;

@Ignore
public class Scripts {

    private static Neow3j neow = Neow3j.build(new HttpService("http://localhost:40332"));
    private static ObjectMapper objectMapper = new ObjectMapper();

    private static final File MEMES_NEF_FILE =
            Paths.get("./build/neow3j/MemeContract.nef").toFile();
    private static final File MEMES_MANIFEST_FILE =
            Paths.get("./build/neow3j/MemeContract.manifest.json").toFile();
    private static final File GOVERNANCE_NEF_FILE =
            Paths.get("./build/neow3j/GovernanceContract.nef").toFile();
    private static final File GOVERNANCE_MANIFEST_FILE =
            Paths.get("./build/neow3j/GovernanceContract.manifest.json").toFile();

    private static ContractManagement contractMgmt = new ContractManagement(neow);

    private static Account alice = Account.fromWIF("L1eV34wPoj9weqhGijdDLtVQzUpWGHszXXpdU9dPuh2nRFFzFa7E");
    private static Wallet wallet = Wallet.withAccounts(alice);

    private static Hash160 memeContractHash = new Hash160("5abe202e2ef498cb72da496325d3edcab0e3bc68");
    private static Hash160 govContractHash = new Hash160("5a6b362f5565c9b35dade2fc694b278c5608a0ad");

    @Test
    public void deployMemeContract() throws Throwable {
        NefFile nef = NefFile.readFromFile(MEMES_NEF_FILE);
        ContractManifest manifest =
                objectMapper.readValue(MEMES_MANIFEST_FILE, ContractManifest.class);
        ContractParameter ownerHash = ContractParameter.hash160(alice);
        NeoSendRawTransaction response = contractMgmt.deploy(nef, manifest, ownerHash)
                .signers(Signer.calledByEntry(alice))
                .wallet(wallet)
                .sign()
                .send();
        if (response.hasError()) {
            response.throwOnError();
        }
        Hash256 txHash = response.getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(txHash, neow);
        Hash160 contractHash = SmartContract.calcContractHash(alice.getScriptHash(),
                nef.getCheckSumAsInteger(), manifest.getName());
        System.out.printf("MemeContract deployed (%s).\n", contractHash.toString());
    }

    @Test
    public void deployGovernanceContract() throws Throwable {
        NefFile nef = NefFile.readFromFile(GOVERNANCE_NEF_FILE);
        ContractManifest manifest =
                objectMapper.readValue(GOVERNANCE_MANIFEST_FILE, ContractManifest.class);
        ContractParameter ownerHash = ContractParameter.hash160(memeContractHash);
        Signer signer =
                new Signer.Builder().account(alice).allowedContracts(memeContractHash).build();

        NeoSendRawTransaction response = contractMgmt.deploy(nef, manifest, ownerHash)
                .signers(signer)
                .wallet(wallet)
                .sign()
                .send();
        if (response.hasError()) {
            response.throwOnError();
        }
        Hash256 txHash = response.getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(txHash, neow);
        Hash160 contractHash = SmartContract.calcContractHash(alice.getScriptHash(),
                nef.getCheckSumAsInteger(), manifest.getName());
        System.out.printf("GovernanceContract deployed (%s).\n", contractHash.toString());
    }

    @Test
    public void getOwnerOfMemeContract() throws IOException {
        SmartContract memeContract = new SmartContract(memeContractHash, neow);
        Hash160 ownerHash = memeContract.callFunctionReturningScriptHash("getOwner");
        System.out.println("Owner of the MemeContract has hash " + ownerHash.toString()
                + " and address " + ownerHash.toAddress());
    }

    @Test
    public void getContractThatGovernanceContractIsPointingAt() throws IOException {
        SmartContract govContract = new SmartContract(govContractHash, neow);
        Hash160 memeContractHash = govContract.callFunctionReturningScriptHash("getMemeContract");
        System.out.println("GovernanceContract is pointing to MemeContract with hash "
                + memeContractHash.toString());
    }

    @Test
    public void proposeNewMeme() throws Throwable {
        String memeId = "my new meme";
        String description = "The meme to rule them all.";
        String url = "https://i.redd.it/4evjbzf2b1011.jpg";
        String imageHash = "ae51b3d6f4876cd78e284c07003c41550741042b23b5bd13973cb16cac197275";
        SmartContract govContract = new SmartContract(govContractHash, neow);
        NeoSendRawTransaction response = govContract.invokeFunction("proposeNewMeme", 
                    string(memeId), string(description), string(url), byteArray(imageHash))
            .signers(Signer.feeOnly(alice))
            .wallet(wallet)
            .sign()
            .send();

        if (response.hasError()) {
            response.throwOnError();
        }
        Hash256 txHash = response.getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(txHash, neow);
        System.out.println("Proposed a new Meme in the transaction with hash " + txHash.toString());
    }

    @Test
    public void getProposals() throws IOException {
        SmartContract govContract = new SmartContract(govContractHash, neow);
        ContractParameter fromIdxZero = ContractParameter.integer(0);
        NeoInvokeFunction response = govContract.callInvokeFunction("getProposals", Arrays.asList(fromIdxZero));
        List<StackItem> proposal = response.getInvocationResult().getStack().get(0).getList().get(0).getList();
        List<StackItem> meme = proposal.get(0).getList();
        System.out.printf("Meme ID: %s, Description: %s, URL: %s, Hash: %s\n", 
                meme.get(0).getString(), meme.get(1).getString(), meme.get(2).getString(), meme.get(3).getHexString());
    }

}
