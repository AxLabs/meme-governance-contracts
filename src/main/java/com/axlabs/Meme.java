package com.axlabs;

import io.neow3j.devpack.ByteString;

public class Meme {
    public String id;
    public String description;
    public String url;
    public ByteString imageHash;
    
    public Meme(String id, String description, String url, ByteString imageHash) {
        this.id = id;
        this.description = description;
        this.url = url;
        this.imageHash = imageHash;
    }

}
