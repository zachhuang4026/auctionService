package org.example;

import java.time.Instant;
import java.util.UUID;

public class Bid {

    public UUID bidID;
    public UUID item;
    public double bid;
    public UUID bidder;
    public long time;

    public Bid(UUID bidId, UUID itemID, double bid, UUID bidder) {
        this.bidID = bidId;
        this.item = itemID;
        this.bid = bid;
        this.bidder = bidder;
        this.time = Instant.now().getEpochSecond();
    }
}
