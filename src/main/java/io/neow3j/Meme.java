package io.neow3j;

import io.neow3j.devpack.ByteString;

public class Meme {
    public ByteString description;
    public ByteString url;
    public ByteString imageHash;
    
    public Meme(ByteString description, ByteString url, ByteString imageHash) {
        this.description = description;
        this.url = url;
        this.imageHash = imageHash;
    }
}
