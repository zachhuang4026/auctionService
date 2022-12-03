package org.example;

import com.google.gson.Gson;
import com.rabbitmq.client.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.*;
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
    private static String API_GATEWAY_IP_ADDRESS_AND_PORT;

    private static Map<String, Auction> pendingAuctions;
    private static Map<String, Auction> activeAuctions;


    public static void main(String[] args) throws Exception {
        System.out.println( "Start and End Auctions" );
        POSTGRES_IP_ADDRESS = args[0];
        RABBITMQ_IP_ADDRESS = args[1];
        API_GATEWAY_IP_ADDRESS_AND_PORT = args[2];
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
            String auctionID = auction.getAuctionID().toString();
            String seller = auction.getSeller().toString();

            if (auction.getEndTime() <= Instant.now().getEpochSecond() + 24 * 60 * 60
                    && auction.getEndTime() > Instant.now().getEpochSecond() + 60 * 60
                    && auction.getStartTime() <= auction.getEndTime() - 24 * 60 * 60
                    && !auction.getOneDayNotification()) {
                auction.setOneDayNotification(true);

                String message = "Auction ID: " + auctionID + " - 1 day til the auction ends";
                try {
                    notify("endClosed", seller, message, getBidders(auctionID));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                System.out.println(message);

            } else if (auction.getEndTime() <= Instant.now().getEpochSecond() + 60 * 60
                    && auction.getEndTime() > Instant.now().getEpochSecond() + 600
                    && auction.getStartTime() <= auction.getEndTime() - 60 * 60
                    && !auction.getOneHourNotification()) {
                auction.setOneHourNotification(true);

                String message = "Auction ID: " + auctionID + " - 1 hour til the auction ends";
                try {
                    notify("endClosed", seller, message, getBidders(auctionID));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                System.out.println(message);

            } else if (auction.getEndTime() <= Instant.now().getEpochSecond() + 600
                    && auction.getEndTime() > Instant.now().getEpochSecond()
                    && auction.getStartTime() <= auction.getEndTime() - 600
                    && !auction.getTenMinutesNotification()) {
                auction.setTenMinutesNotification(true);

                String message = "Auction ID: " + auctionID + " - 10 minutes til the auction ends";
                try {
                    notify("endClosed", seller, message, getBidders(auctionID));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                System.out.println(message);

            } else if (auction.getEndTime() <= Instant.now().getEpochSecond()) {
                endAuction(auction);

                String message = "Auction ID: " + auctionID + " - The auction has ended";
                try {
                    notify("endClosed", seller, message, getBidders(auctionID));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                activeAuctions.remove(auctionID);
                System.out.println(message);
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

        JSONObject updatedAuction = getAuctionInfo(auctionID);
        String winner = null;
        if (updatedAuction.has("currWinner")) {
            winner = updatedAuction.getString("currWinner");
        }

        // Add item to shopping cart
        if (winner != null) {
            try {
                addToShoppingCart(winner, updatedAuction.getString("itemID"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        System.out.println("Auction " + auctionID + " ended");
    }


    private static List<String> getBidders(String auctionID) {
        String selectSQL = "SELECT DISTINCT bid->>'bidder' AS bidder from bids WHERE bid->>'auctionID' = '" + auctionID + "'";
        List<String> bidders = new ArrayList<>();
        try (java.sql.Connection connection = getPostgresConnection();
             PreparedStatement pst = connection.prepareStatement(selectSQL)) {
            ResultSet rs = pst.executeQuery();

            while (rs.next()) {
                String bidder = rs.getString("bidder");
                bidders.add(bidder);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return bidders;
    }


    private static JSONObject getAuctionInfo(String auctionID) {
        Auction auction = null;
        try (java.sql.Connection connection = getPostgresConnection();
             PreparedStatement pst = connection.prepareStatement("SELECT * FROM auctions WHERE auctionID = '" + auctionID + "';")) {
            ResultSet rs = pst.executeQuery();

            while (rs.next()) {
                String listingType = rs.getString("listingType");
                String itemID = rs.getString("itemID");
                long startTime = rs.getLong("startTime");
                long endTime = rs.getLong("endTime");
                double currPrice = rs.getDouble("currPrice");
                String currWinner = rs.getString("currWinner");
                String seller = rs.getString("seller");
                String status = rs.getString("status");

                auction = new Auction(auctionID, listingType, itemID, startTime, endTime, currPrice, currWinner, status, seller);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        assert auction != null;
        JSONObject res = new JSONObject(auction);
        return res;
    }


    private static void notify(String type, String seller, String message, List<String> bidders) throws Exception {
        JSONObject reqJSON = new JSONObject();

        switch (type) {
            case "endClosed":
                reqJSON.put("type", "endClosed");
                reqJSON.put("sellerId", seller);
                reqJSON.put("bidderId", bidders);
                reqJSON.put("message", message);
                break;
            default:
                throw new Exception("Type \"" + type + "\" not supported or input invalid");
        }
        System.out.println("API Gateway /sendEmail Request JSON: " + reqJSON);

        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("http://" + API_GATEWAY_IP_ADDRESS_AND_PORT + "/sendEmail");

        StringEntity entity = new StringEntity(reqJSON.toString());
        httpPost.setEntity(entity);
        httpPost.setHeader("Content-type", "application/json");

        CloseableHttpResponse res = client.execute(httpPost);
        System.out.println(res.getStatusLine());

        int statusCode = res.getStatusLine().getStatusCode();
        if (statusCode != 200) {
            Scanner sc = new Scanner(res.getEntity().getContent());
            while (sc.hasNext()) {
                System.out.println(sc.nextLine());
            }
            throw new Exception("An error occurred when calling sendEmail");
        } else {
            System.out.println("Email/Alert sent (type: " + type + ")");
        }

        client.close();
    }


    private static void addToShoppingCart(String userID, String itemID) throws Exception {
        JSONObject reqJSON = new JSONObject();
        reqJSON.put("account_id", userID);
        reqJSON.put("item_id", itemID);
        System.out.println("API Gateway /addToShoppingCart Request JSON: " + reqJSON);

        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("http://" + API_GATEWAY_IP_ADDRESS_AND_PORT + "/addToShoppingCart");

        StringEntity entity = new StringEntity(reqJSON.toString());
        httpPost.setEntity(entity);
        httpPost.setHeader("Content-type", "application/json");

        CloseableHttpResponse res = client.execute(httpPost);
        System.out.println(res.getStatusLine());

        int statusCode = res.getStatusLine().getStatusCode();
        if (statusCode != 200) {
            Scanner sc = new Scanner(res.getEntity().getContent());
            while(sc.hasNext()) {
                System.out.println(sc.nextLine());
            }
            throw new Exception("An error occurred when calling addToShoppingCart");
        } else {
            System.out.println("Added " + itemID + " to " + userID + "'s shopping cart");
        }

        client.close();
    }

}
