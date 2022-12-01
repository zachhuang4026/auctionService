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
    private List<Bid> bidHistory;
    private AuctionStatus status;
    private boolean oneDayNotification;
    private boolean oneHourNotification;
    private boolean tenMinutesNotification;

    public enum AuctionStatus {
        PENDING,
        ACTIVE,
        CLOSED,
    }

    public Auction(String listingType, UUID itemID, long startTime, long endTime, double startPrice) {
        this.auctionID = UUID.randomUUID();
        this.listingType = listingType;
        this.itemID = itemID;
        this.startTime = startTime;
        this.endTime = endTime;
        this.currPrice = startPrice;
        this.currWinner = null;
        this.bidHistory = new ArrayList<>();
        this.status = AuctionStatus.PENDING;
        this.oneDayNotification = false;
        this.oneHourNotification = false;
        this.tenMinutesNotification = false;
    }

    public Auction(String auctionID, String listingType, String itemID, long startTime, long endTime, double currPrice, String currWinner, String status) {
        this.auctionID = UUID.fromString(auctionID);
        this.listingType = listingType;
        this.itemID = UUID.fromString(itemID);
        this.startTime = startTime;
        this.endTime = endTime;
        this.currPrice = currPrice;
        this.currWinner = currWinner == null ? null : UUID.fromString(currWinner);
        this.oneDayNotification = false;
        this.oneHourNotification = false;
        this.tenMinutesNotification = false;
        this.bidHistory = new ArrayList<>();
        if (status.equals("ACTIVE"))
            this.status = AuctionStatus.ACTIVE;
        else if (status.equals("CLOSED"))
            this.status = AuctionStatus.CLOSED;
        else
            this.status = AuctionStatus.PENDING;
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

    public void setOneDayNotification(boolean oneDayNotification) {
        this.oneDayNotification = oneDayNotification;
    }

    public void setOneHourNotification(boolean oneHourNotification) {
        this.oneHourNotification = oneHourNotification;
    }

    public void setTenMinutesNotification(boolean tenMinutesNotification) {
        this.tenMinutesNotification = tenMinutesNotification;
    }

    public boolean getOneDayNotification() {
        return oneDayNotification;
    }

    public boolean getOneHourNotification() {
        return oneHourNotification;
    }

    public boolean getTenMinutesNotification() {
        return tenMinutesNotification;
    }
}
