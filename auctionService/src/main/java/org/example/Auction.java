package org.example;

import java.util.*;

public class Auction {

    public UUID auctionID;
    public UUID itemID;
    public long startTime;
    public long endTime;
    public double currPrice;
    public UUID currWinner;
    public List<Bid> bidHistory;
    public AuctionStatus status;

    public enum AuctionStatus {
        PENDING,
        ACTIVE,
        CLOSED,
    }

    public Auction(UUID id, UUID itemID, long startTime, long endTime) {
        this.auctionID = id;
        this.itemID = itemID;
        this.startTime = startTime;
        this.endTime = endTime;
        this.currPrice = 0; /////////////
        this.currWinner = null;/////////////////
        this.bidHistory = new ArrayList<>();
        this.status = AuctionStatus.PENDING; //////////
    }

    public Auction(String auctionID, String itemID, long startTime, long endTime, double currPrice, String currWinner, String status) {
        this.auctionID = UUID.fromString(auctionID);
        this.itemID = UUID.fromString(itemID);
        this.startTime = startTime;
        this.endTime = endTime;
        this.currPrice = currPrice;
        this.currWinner = UUID.fromString(currWinner);
        this.bidHistory = new ArrayList<>();
        if (status.equals("ACTIVE"))
            this.status = AuctionStatus.ACTIVE;
        else if (status.equals("CLOSED"))
            this.status = AuctionStatus.CLOSED;
        else
            this.status = AuctionStatus.PENDING;
    }

    public boolean updateBid(Bid bid) {
        if (this.currPrice < bid.bid) {
            this.bidHistory.add(bid);
            this.currPrice = bid.bid;
            this.currWinner = bid.bidder;
            return true;
        }
        return false;
    }

    public boolean startAuction() {
        return false;
    }

    public boolean closeAuction() {
        return false;
    }

    public AuctionStatus getAuctionStatus() {
        return this.status;
    }

    public boolean setAuctionStatus(AuctionStatus status) {
        this.status = status;
        return true;
    }

    public UUID getAuctionID() {
        return auctionID;
    }

    public UUID getItemID() {
        return this.itemID;
    }

    public double getCurrPrice() {
        return currPrice;
    }

    public UUID getCurrWinner() {
        return currWinner;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public List<Bid> getBidHistory() {
        return bidHistory;
    }
}
