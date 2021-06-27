package com.axlabs;

public class Proposal {
    public Meme meme;
    public boolean create;
    public boolean voteInProgress;
    public int finalizationBlock;
    public int votesInFavor;
    public int votesAgainst;

    public Proposal(Meme meme, boolean create, boolean voteInProgress, int finalizationBlock,
            int votesInFavor, int votesAgainst) {
        this.meme = meme;
        this.create = create;
        this.voteInProgress = voteInProgress;
        this.finalizationBlock = finalizationBlock;
        this.votesInFavor = votesInFavor;
        this.votesAgainst = votesAgainst;
    }

}
