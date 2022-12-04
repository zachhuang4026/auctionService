package org.example;

import java.util.*;

public class Auction {

    private UUID auctionID;
    private String listingType;
    private UUID itemID;
    private long startTime;
    private long endTime;
    private double currPrice;
    private UUID currWinner;
    private UUID seller;
    private List<Bid> bidHistory;
    private AuctionStatus status;

    public enum AuctionStatus {
        PENDING,
        ACTIVE,
        CLOSED,
    }

    public Auction(String listingType, UUID itemID, long startTime, long endTime, double startPrice, UUID seller) {
        this.auctionID = UUID.randomUUID();
        this.listingType = listingType;
        this.itemID = itemID;
        this.startTime = startTime;
        this.endTime = endTime;
        this.currPrice = startPrice;
        this.currWinner = null;
        this.seller = seller;
        this.bidHistory = new ArrayList<>();
        this.status = AuctionStatus.PENDING;
    }

    public Auction(String auctionID, String listingType, String itemID, long startTime, long endTime, double currPrice, String currWinner, String seller, String status) {
        this.auctionID = UUID.fromString(auctionID);
        this.listingType = listingType;
        this.itemID = UUID.fromString(itemID);
        this.startTime = startTime;
        this.endTime = endTime;
        this.currPrice = currPrice;
        this.currWinner = currWinner == null ? null : UUID.fromString(currWinner);
        this.seller = UUID.fromString(seller);
        this.bidHistory = new ArrayList<>();
        if (status.equals("ACTIVE"))
            this.status = AuctionStatus.ACTIVE;
        else if (status.equals("CLOSED"))
            this.status = AuctionStatus.CLOSED;
        else
            this.status = AuctionStatus.PENDING;
    }

    public AuctionStatus getAuctionStatus() {
        return this.status;
    }

    public void setAuctionStatus(AuctionStatus status) {
        this.status = status;
    }

    public void setAuctionStatus(String status) {
        if (status.equals("ACTIVE"))
            this.status = AuctionStatus.ACTIVE;
        else if (status.equals("CLOSED"))
            this.status = AuctionStatus.CLOSED;
        else
            this.status = AuctionStatus.PENDING;
    }

    public UUID getAuctionID() {
        return auctionID;
    }

    public String getListingType() {
        return listingType;
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

    public UUID getSeller() {
        return seller;
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

    public void setBidHistory(List<Bid> bidHistory) {
        this.bidHistory = bidHistory;
    }
}
