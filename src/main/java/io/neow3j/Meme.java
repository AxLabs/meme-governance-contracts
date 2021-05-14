package io.neow3j;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Helper;

public class Meme {
    public String description;
    public String url;
    public String imageHash;
    
    public Meme(String description, String url, String imageHash) {
        this.description = description;
        this.url = url;
        this.imageHash = imageHash;
    }

    public ByteString serialize() {
        return new ByteString(Helper.toByteArray(description + "," + url + "," + imageHash));
    }
}
