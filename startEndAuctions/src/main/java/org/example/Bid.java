package org.example;

import java.time.Instant;
import java.util.UUID;

public class Bid {

    private UUID bidID;
    private UUID auctionID;
    private UUID itemID;
    private double bid;
    private UUID bidder;
    private long time;

    public Bid(UUID auctionID, UUID itemID, double bid, UUID bidder) {
        this.bidID = UUID.randomUUID();
        this.auctionID = auctionID;
        this.itemID = itemID;
        this.bid = bid;
        this.bidder = bidder;
        this.time = Instant.now().getEpochSecond();
    }

    public UUID getBidID() {
        return bidID;
    }

    public UUID getAuctionID() {
        return auctionID;
    }

    public UUID getItemID() {
        return itemID;
    }

    public double getBid() {
        return bid;
    }

    public UUID getBidder() {
        return bidder;
    }

    public long getTime() {
        return time;
    }

    public void setBid(double bid) {
        this.bid = bid;
    }

    public void setAuctionID(UUID auctionID) {
        this.auctionID = auctionID;
    }

    public void setBidder(UUID bidder) {
        this.bidder = bidder;
    }

    public void setBidID(UUID bidID) {
        this.bidID = bidID;
    }

    public void setItemID(UUID itemID) {
        this.itemID = itemID;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
