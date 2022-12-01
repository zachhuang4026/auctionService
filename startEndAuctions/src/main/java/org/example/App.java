package org.example;

import com.google.gson.Gson;
import com.rabbitmq.client.*;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class App {

    private final static String QUEUE_NAME_START_END_AUCTIONS = "auctionServiceStartEndAuctionsQueue";
    private static String POSTGRES_IP_ADDRESS;
    private static String RABBITMQ_IP_ADDRESS;

    private static Map<String, Auction> pendingAuctions;
    private static Map<String, Auction> activeAuctions;


    public static void main(String[] args) throws Exception {
        System.out.println( "Start and End Auctions" );
        POSTGRES_IP_ADDRESS = args[0];
        RABBITMQ_IP_ADDRESS = args[1];
        pendingAuctions = new ConcurrentHashMap<>();
        activeAuctions = new ConcurrentHashMap<>();

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBITMQ_IP_ADDRESS);
        com.rabbitmq.client.Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.queueDeclare(QUEUE_NAME_START_END_AUCTIONS, false, false, false, null);
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println(" [x] Received '" + message + "'");

            try {
                dispatch(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        channel.basicConsume(QUEUE_NAME_START_END_AUCTIONS, true, deliverCallback, consumerTag -> { });

        while (true) {
            startAuctionCheck();
            endAuctionCheck();
        }
    }


    private static java.sql.Connection getPostgresConnection() {
        try {
            Class.forName("org.postgresql.Driver");
            String url = "jdbc:postgresql://" + POSTGRES_IP_ADDRESS + ":2345/auction";

            String user = "postgres";
            String password = "abc123";
            return DriverManager.getConnection(url, user, password);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    private static void dispatch(String message) throws Exception {
        JSONObject json = new JSONObject(message);
        String method = json.getString("type");
        String auction = json.getJSONObject("auction").toString();

        switch (method) {
            case "create":
                create(auction);
                break;
            case "endEarly":
                endEarly(auction);
                break;
            default:
                throw new Exception("Method \"" + method + "\" not supported or invalid input");
        }
    }


    private static void create(String auctionJSON) {
        Auction auction = new Gson().fromJson(auctionJSON, Auction.class);
        pendingAuctions.put(auction.getAuctionID().toString(), auction);
    }


    private static void endEarly(String auctionJSON) {
        Auction auction = new Gson().fromJson(auctionJSON, Auction.class);
        activeAuctions.remove(auction.getAuctionID().toString());
    }


    private static void startAuctionCheck(){
        for (Auction auction : pendingAuctions.values()) {
            if (auction.getStartTime() <= Instant.now().getEpochSecond()) {
                startAuction(auction);
                String auctionID = auction.getAuctionID().toString();
                pendingAuctions.remove(auctionID);
                activeAuctions.put(auctionID, auction);
            }
        }
    }


    private static void startAuction(Auction auction){
        auction.setAuctionStatus(Auction.AuctionStatus.ACTIVE);

        String auctionID = auction.getAuctionID().toString();
        String updateSQL = "UPDATE auctions SET status = 'ACTIVE' WHERE auctionID = ?;";
        try (java.sql.Connection connection = getPostgresConnection();
             PreparedStatement pst = connection.prepareStatement(updateSQL)) {
            pst.setString(1, auctionID);
            System.out.println(pst);
            pst.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Auction " + auctionID + " started");
    }


    private static void endAuctionCheck() {
        for (Auction auction : activeAuctions.values()) {
            if (auction.getEndTime() <= Instant.now().getEpochSecond() + 24 * 60 * 60
                    && auction.getEndTime() > Instant.now().getEpochSecond() + 60 * 60
                    && auction.getStartTime() <= auction.getEndTime() - 24 * 60 * 60
                    && !auction.getOneDayNotification()) {
                auction.setOneDayNotification(true);

                // TODO: send notification to seller and bidders
                System.out.println("Auction ID: " + auction.getAuctionID() + " - 1 day til the auction ends");
            } else if (auction.getEndTime() <= Instant.now().getEpochSecond() + 60 * 60
                    && auction.getEndTime() > Instant.now().getEpochSecond() + 600
                    && auction.getStartTime() <= auction.getEndTime() - 60 * 60
                    && !auction.getOneHourNotification()) {
                auction.setOneHourNotification(true);

                // TODO: send notification to seller and bidders
                System.out.println("Auction ID: " + auction.getAuctionID() + " - 1 hour til the auction ends");

            } else if (auction.getEndTime() <= Instant.now().getEpochSecond() + 600
                    && auction.getEndTime() > Instant.now().getEpochSecond()
                    && auction.getStartTime() <= auction.getEndTime() - 600
                    && !auction.getTenMinutesNotification()) {
                auction.setTenMinutesNotification(true);

                // TODO: send notification to seller and bidders
                System.out.println("Auction ID: " + auction.getAuctionID() + " - 10 minutes til the auction ends");

            } else if (auction.getEndTime() <= Instant.now().getEpochSecond()) {
                endAuction(auction);
                String auctionID = auction.getAuctionID().toString();
                activeAuctions.remove(auctionID);

                // TODO: send notification to seller and bidders
                System.out.println("Auction ID: " + auction.getAuctionID() + " - The auction has ended");
            }
        }
    }


    private static void endAuction(Auction auction) {
        auction.setAuctionStatus(Auction.AuctionStatus.CLOSED);

        String auctionID = auction.getAuctionID().toString();
        String updateSQL = "UPDATE auctions SET status = 'CLOSED' WHERE auctionID = ?;";
        try (java.sql.Connection connection = getPostgresConnection();
             PreparedStatement pst = connection.prepareStatement(updateSQL)) {
            pst.setString(1, auctionID);
            System.out.println(pst);
            pst.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }

        //TODO: shopping cart

        System.out.println("Auction " + auctionID + " ended");
    }

}
