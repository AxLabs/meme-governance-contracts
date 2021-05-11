package io.neow3j;

import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StringLiteralHelper;
import io.neow3j.devpack.annotations.ManifestExtra;

// A simple smart contract with one method that returns a string and takes no arguments.
@ManifestExtra(key = "name", value = "Memes")
@ManifestExtra(key = "author", value = "AxLabs")
public class MemeContract {

    static Hash160 owner =
        StringLiteralHelper.addressToScriptHash("NM7Aky765FG8NhhwtxjXRx7jEL1cnw7PBP");
    
    static StorageContext ctx = Storage.getStorageContext();
    
    public static void setOwner(Hash160 newOwner) throws Exception {
        if (!Runtime.checkWitness(owner)) {
            throw new Exception("No authorization.");
        }
        owner = newOwner;
    }

    public static Hash160 getOwner() {
        return owner;
    }

    

}
