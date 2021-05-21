package io.neow3j;

public class Proposal {
    public Meme meme;
    public Boolean create;
    public Boolean voteInProgress;
    public Integer finalizationBlock;
    public Integer votesInFavor;
    public Integer votesAgainst;

    public Proposal(Meme meme, Boolean create, Boolean voteInProgress, Integer finalizationBlock,
            Integer votesInFavor, Integer votesAgainst) {
        this.meme = meme;
        this.create = create;
        this.voteInProgress = voteInProgress;
        this.finalizationBlock = finalizationBlock;
        this.votesInFavor = votesInFavor;
        this.votesAgainst = votesAgainst;
    }

}
