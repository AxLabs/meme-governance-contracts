package io.neow3j;

import io.neow3j.devpack.ByteString;

public class Meme {
    public ByteString id;
    public ByteString description;
    public ByteString url;
    public ByteString imageHash;
    
    public Meme(ByteString id, ByteString description, ByteString url, ByteString imageHash) {
        this.id = id;
        this.description = description;
        this.url = url;
        this.imageHash = imageHash;
    }
}
