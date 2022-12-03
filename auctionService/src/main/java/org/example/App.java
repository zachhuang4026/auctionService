package org.example;

import com.google.gson.Gson;
import com.rabbitmq.client.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static java.sql.Types.VARCHAR;

public class App {

    private final static String QUEUE_NAME_RPC = "auctionServiceRPCQueue";
    private final static String QUEUE_NAME_START_END_AUCTIONS = "auctionServiceStartEndAuctionsQueue";
    private static String POSTGRES_IP_ADDRESS;
    private static String RABBITMQ_IP_ADDRESS;
    private static String API_GATEWAY_IP_ADDRESS_AND_PORT;

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


    private static String dispatch(String message) throws Exception {
        JSONObject json = new JSONObject(message);
        String method = json.getString("type");
        String res;

        switch (method) {
            case "bid":
                res = bid(json);
                break;
            case "buyNow":
                res = buyNow(json);
                break;
            case "endAuctionEarly":
                res = endAuctionEarly(json);
                break;
            case "createAuction":
                res = createAuction(json);
                break;
            case "getAuction":
                res = getAuction(json);
                break;
            case "getMultipleAuctions":
                res = getMultipleAuctions(json);
                break;
            case "seeAllAuctionsBuyer":
                res = seeAllAuctionsBuyer(json);
                break;
            case "seeAllAuctionsSeller":
                res = seeAllAuctionsSeller(json);
                break;
            case "seeActiveAuctions":
                res = seeAllAuctions("ACTIVE");
                break;
            case "seeClosedAuctions":
                res = seeAllAuctions("CLOSED");
                break;
            default:
                throw new Exception("Method \"" + method + "\" not supported or invalid input");
        }

        return res;
    }


    private static String bid(JSONObject json) {
        UUID auctionID = UUID.fromString(json.getString("auctionID"));
        JSONObject auction = new JSONObject(getAuction(json));
        UUID itemID = UUID.fromString(auction.getString("itemID"));
        double newPrice = json.getDouble("bid");
        UUID bidder = UUID.fromString(json.getString("bidder"));
        Bid newBid = new Bid(auctionID, itemID, newPrice, bidder);
        JSONObject bidJSON = new JSONObject(newBid);

        if (auction.getString("auctionStatus").equals("PENDING")) {  // It's "auctionStatus", not "status"
            JSONObject res = new JSONObject();
            res.put("success", false);
            res.put("message", "Auction has not started yet");
            return res.toString();
        } else if (auction.getString("auctionStatus").equals("CLOSED")) {
            JSONObject res = new JSONObject();
            res.put("success", false);
            res.put("message", "Auction has ended already");
            return res.toString();
        }

        String prevWinner = null;
        if (auction.has("currWinner")) {
            prevWinner = auction.getString("currWinner");
        }

        boolean updateBidSuccess = updateBid(auction, newBid);
        String updateBidMessage = "";

        if (updateBidSuccess) { // insert bid and update auction
            updateBidMessage = "success";

            String insertSQL = "INSERT INTO bids (auctionID, bid) VALUES (?, cast(? as json));";
            try (java.sql.Connection connection = getPostgresConnection();
                 PreparedStatement pst = connection.prepareStatement(insertSQL)) {
                pst.setString(1, auctionID.toString());
                pst.setString(2, bidJSON.toString());

                System.out.println(pst);
                pst.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }

            String updateSQL = "UPDATE auctions SET currPrice = ?, currWinner = ? WHERE auctionID = ?;";
            try (java.sql.Connection connection = getPostgresConnection();
                 PreparedStatement pst = connection.prepareStatement(updateSQL)) {
                pst.setDouble(1, newPrice);
                pst.setString(2, bidder.toString());
                pst.setString(3, auctionID.toString());

                System.out.println(pst);
                pst.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Alert seller when their item has been bid on
            try {
                notify("itemBid", auction.getString("seller"), null, null);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Alert buyer via email when someone has placed a higher bid on the item they had bid current high bid on
            if (prevWinner != null) {
                try {
                    List<String> prevBidder = new ArrayList<>();
                    prevBidder.add(prevWinner);
                    notify("higherBid", null, null, prevBidder);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        } else {
            updateBidMessage = "The new bid is not higher than the current price";
        }

        JSONObject res = new JSONObject();
        res.put("success", updateBidSuccess);
        res.put("message", updateBidMessage);
        return res.toString();
    }


    private static boolean updateBid(JSONObject auction, Bid bid) {
        double currPrice = auction.getDouble("currPrice");
        if (currPrice < bid.getBid()) {
            auction.put("currPrice", bid.getBid());
            auction.put("currWinner", bid.getBidder().toString());
            return true;
        }
        return false;
    }

    private static String buyNow(JSONObject json) {
        String auctionID = json.getString("auctionID");
        String userID = json.getString("userID");

        JSONObject auction = new JSONObject(getAuction(json));
        String status = auction.getString("auctionStatus");
        if (status.equals("CLOSED")) {
            JSONObject res = new JSONObject();
            res.put("success", false);
            res.put("message", "Item is no longer available");
            return res.toString();
        }

        String updateSQL = "UPDATE auctions SET status = 'CLOSED', endTime = ?, currWinner = ? WHERE auctionID = ?;";
        try (java.sql.Connection connection = getPostgresConnection();
             PreparedStatement pst = connection.prepareStatement(updateSQL)) {
            pst.setLong(1, Instant.now().getEpochSecond());
            pst.setString(2, userID);
            pst.setString(3, auctionID);
            System.out.println(pst);
            pst.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Add item to shopping cart
        String itemID = auction.getString("itemID");
        try {
            addToShoppingCart(userID, itemID);
        } catch (Exception e) {
            e.printStackTrace();
        }

        JSONObject res = new JSONObject();
        res.put("success", true);
        res.put("message", "success");
        return res.toString();
    }


    private static String createAuction(JSONObject json) {
        String auctionID = UUID.randomUUID().toString();
        String listingType = json.getString("listingType");
        String itemID = json.getString("itemID");
        String seller = json.getString("seller");
        double startPrice = json.getDouble("startPrice");

        long startTime = -1;
        long endTime = -1;
        String status = "ACTIVE";
        if (listingType.equals("AUCTION")) {
            startTime = json.getLong("startTime");
            endTime = json.getLong("endTime");
            status = "PENDING";
        }

        String insertSQL = "INSERT INTO auctions VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (java.sql.Connection connection = getPostgresConnection();
             PreparedStatement pst = connection.prepareStatement(insertSQL)) {
            pst.setString(1, auctionID);
            pst.setString(2, listingType);
            pst.setString(3, itemID);
            pst.setLong(4, startTime);
            pst.setLong(5, endTime);
            pst.setDouble(6, startPrice);
            pst.setNull(7, VARCHAR);
            pst.setString(8, seller);
            pst.setString(9, status);

            System.out.println(pst);
            pst.executeUpdate();

            // Check for start and end time (sent to queue and processed by another program)
            if (listingType.equals("AUCTION")) {
                Auction auction = new Auction(auctionID, listingType, itemID, startTime, endTime, startPrice, null, seller, status);
                JSONObject auctionJSON = new JSONObject(auction);

                JSONObject toSend = new JSONObject();
                toSend.put("type", "create");
                toSend.put("auction", auctionJSON);

                ConnectionFactory factory = new ConnectionFactory();
                factory.setHost(RABBITMQ_IP_ADDRESS);
                try (com.rabbitmq.client.Connection rabbitMQConnection = factory.newConnection();
                     Channel channel = rabbitMQConnection.createChannel()) {
                    channel.queueDeclare(QUEUE_NAME_START_END_AUCTIONS, false, false, false, null);
                    String message = toSend.toString();
                    channel.basicPublish("", QUEUE_NAME_START_END_AUCTIONS, null, message.getBytes(StandardCharsets.UTF_8));
                    System.out.println(" [x] Sent '" + message + "'");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        JSONObject res = new JSONObject();
        res.put("success", true);
        res.put("auctionID", auctionID);
        return res.toString();
    }


    private static String endAuctionEarly(JSONObject json) throws IOException, TimeoutException {
        JSONObject auction = new JSONObject(getAuction(json));
        String auctionID = auction.getString("auctionID");

        if (auction.getString("auctionStatus").equals("CLOSED")) {
            JSONObject res = new JSONObject();
            res.put("success", false);
            res.put("message", "Auction has already ended");
            return res.toString();
        }

        String updateSQL = "UPDATE auctions SET status = 'CLOSED', endTime = ? WHERE auctionID = ?;";
        try (java.sql.Connection connection = getPostgresConnection();
             PreparedStatement pst = connection.prepareStatement(updateSQL)) {
            pst.setLong(1, Instant.now().getEpochSecond());
            pst.setString(2, auctionID);
            System.out.println(pst);
            pst.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Remove from the check for end time list (processed by another program)
        JSONObject toSend = new JSONObject();
        toSend.put("type", "endEarly");
        toSend.put("auction", auction);
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBITMQ_IP_ADDRESS);
        try (com.rabbitmq.client.Connection rabbitMQConnection = factory.newConnection();
             Channel channel = rabbitMQConnection.createChannel()) {
            channel.queueDeclare(QUEUE_NAME_START_END_AUCTIONS, false, false, false, null);
            String message = toSend.toString();
            channel.basicPublish("", QUEUE_NAME_START_END_AUCTIONS, null, message.getBytes(StandardCharsets.UTF_8));
            System.out.println(" [x] Sent '" + message + "'");
        }

        String userID = null;
        if (auction.has("currWinner")) {
            userID = auction.getString("currWinner");
        }
        String itemID = auction.getString("itemID");
        String seller = auction.getString("seller");

        if (userID == null) {
            JSONObject res = new JSONObject();
            res.put("success", true);
            res.put("message", "No one has placed a bid on this auction");
            return res.toString();
        }

        // Add item to shopping cart
        try {
            addToShoppingCart(userID, itemID);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Notify seller
        try {
            notify("endClosed", seller, "Auction " + auctionID + " ended early", getBidders(auctionID));
        } catch (Exception e) {
            e.printStackTrace();
        }

        JSONObject res = new JSONObject();
        res.put("success", true);
        res.put("message", "success");
        return res.toString();
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


    private static String getAuction(JSONObject json) {
        UUID auctionID = UUID.fromString(json.getString("auctionID"));
        return getAuctionInfo(auctionID.toString());
    }


    private static String getAuctionInfo(String auctionID) {
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

                auction = new Auction(auctionID, listingType, itemID, startTime, endTime, currPrice, currWinner, seller, status);
                auction.setBidHistory(getBidHistory(auctionID));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        assert auction != null;
        JSONObject res = new JSONObject(auction);

        res.put("success", true);
        return res.toString();
    }


    private static String getMultipleAuctions(JSONObject json) {
        JSONArray auctionIDs = json.getJSONArray("auctionIDs");
        List<Auction> auctions = new ArrayList<>();

        for (Object s : auctionIDs) {
            String auctionStr = getAuctionInfo(s.toString());
            Auction auction = new Gson().fromJson(auctionStr, Auction.class);
            auctions.add(auction);
        }

        JSONObject res = new JSONObject();
        res.put("success", true);
        res.put("auctions", auctions);
        return res.toString();
    }


    private static String seeAllAuctionsBuyer(JSONObject json) {
        String userID = json.getString("userID");
        String selectSQL = "SELECT * from auctions WHERE auctionID IN (SELECT DISTINCT auctionID from bids WHERE bid->>'bidder' = '" + userID + "');";
        List<Auction> auctions = getAuctionsList(selectSQL);

        JSONObject res = new JSONObject();
        res.put("success", true);
        res.put("auctions", auctions);
        return res.toString();
    }


    private static String seeAllAuctionsSeller(JSONObject json) {
        String userID = json.getString("seller");
        String selectSQL = "SELECT * from auctions WHERE seller  = '" + userID + "');";
        List<Auction> auctions = getAuctionsList(selectSQL);

        JSONObject res = new JSONObject();
        res.put("success", true);
        res.put("auctions", auctions);
        return res.toString();
    }


    private static List<Auction> getAuctionsList(String selectSQL) {
        List<Auction> auctions = new ArrayList<>();
        try (java.sql.Connection connection = getPostgresConnection();
             PreparedStatement pst = connection.prepareStatement(selectSQL)) {
            ResultSet rs = pst.executeQuery();

            while (rs.next()) {
                String auctionID = rs.getString("auctionID");
                String listingType = rs.getString("listingType");
                String itemID = rs.getString("itemID");
                long startTime = rs.getLong("startTime");
                long endTime = rs.getLong("endTime");
                double currPrice = rs.getDouble("currPrice");
                String currWinner = rs.getString("currWinner");
                String seller = rs.getString("seller");
                String status = rs.getString("status");

                Auction auction = new Auction(auctionID, listingType, itemID, startTime, endTime, currPrice, currWinner, seller, status);
                auction.setBidHistory(getBidHistory(auctionID));
                auctions.add(auction);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return auctions;
    }


    private static String seeAllAuctions(String requestStatus) {
        List<Auction> auctions = new ArrayList<>();
        try (java.sql.Connection connection = getPostgresConnection();
             PreparedStatement pst = connection.prepareStatement("SELECT * FROM auctions WHERE listingType = 'AUCTION' AND status = '" + requestStatus + "';")) {
            ResultSet rs = pst.executeQuery();

            while (rs.next()) {
                String auctionID = rs.getString("auctionID");
                String listingType = rs.getString("listingType");
                String itemID = rs.getString("itemID");
                long startTime = rs.getLong("startTime");
                long endTime = rs.getLong("endTime");
                double currPrice = rs.getDouble("currPrice");
                String currWinner = rs.getString("currWinner");
                String seller = rs.getString("seller");
                String status = rs.getString("status");

                Auction auction = new Auction(auctionID, listingType, itemID, startTime, endTime, currPrice, currWinner, seller, status);
                auction.setBidHistory(getBidHistory(auctionID));
                auctions.add(auction);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        JSONObject res = new JSONObject();
        res.put("success", true);
        res.put("auctions", auctions);
        return res.toString();
    }


    private static List<Bid> getBidHistory(String auctionID) {
        List<Bid> bidHistory = new ArrayList<>();
        String selectSQL = "SELECT bid from bids WHERE auctionID = '" + auctionID + "' ORDER BY bid->>'startTime';";
        try (java.sql.Connection connection = getPostgresConnection();
             PreparedStatement pst = connection.prepareStatement(selectSQL)) {
            ResultSet rs = pst.executeQuery();

            while (rs.next()) {
                JSONObject json = new JSONObject(rs.getString(1));
                Bid bid = new Gson().fromJson(json.toString(), Bid.class);
                bidHistory.add(bid);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return bidHistory;
    }


    private static void notify(String type, String seller, String message, List<String> bidders) throws Exception {
        JSONObject reqJSON = new JSONObject();

        switch (type) {
            case "itemBid":
                reqJSON.put("type", "itemBid");
                reqJSON.put("userId", seller);
                break;
            case "higherBid":
                reqJSON.put("type", "higherBid");
                reqJSON.put("userId", bidders);
                break;
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


    public static void main( String[] args ) throws Exception {
        System.out.println("My Auction Service");

        POSTGRES_IP_ADDRESS = args[0];
        RABBITMQ_IP_ADDRESS = args[1];
        API_GATEWAY_IP_ADDRESS_AND_PORT = args[2];

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBITMQ_IP_ADDRESS);
        com.rabbitmq.client.Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.queueDeclare(QUEUE_NAME_RPC, false, false, false, null);
        channel.queuePurge(QUEUE_NAME_RPC);

        channel.basicQos(1);

        System.out.println(" [x] Awaiting RPC requests");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            AMQP.BasicProperties replyProps = new AMQP.BasicProperties
                    .Builder()
                    .correlationId(delivery.getProperties().getCorrelationId())
                    .build();

            String response = "";
            try {
                String message = new String(delivery.getBody(), "UTF-8");

                // Do things here
                System.out.println(" [x] Received: " + message);
                response += dispatch(message);

            } catch (Exception e) {
                System.out.println(" [.] " + e);
                e.printStackTrace();
            } finally {
                channel.basicPublish("", delivery.getProperties().getReplyTo(), replyProps, response.getBytes("UTF-8"));
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        };

        channel.basicConsume(QUEUE_NAME_RPC, false, deliverCallback, (consumerTag -> {}));
    }

}
