package org.example;

import com.google.gson.Gson;
import com.rabbitmq.client.*;
import org.json.JSONObject;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.sql.Types.VARCHAR;

public class App {

    private final static String QUEUE_NAME_RPC = "auctionServiceRPCQueue";

    private static java.sql.Connection getPostgresConnection() {
        try {
            Class.forName("org.postgresql.Driver");
            String url = "jdbc:postgresql://172.17.0.6:5432/auction";  // TODO: fix IP
            String user = "postgres";
            String password = "abc123";
            return DriverManager.getConnection(url, user, password);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    private static void closePostgresConnection() {
        // TODO (need this??)
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
                res = "buyNow todo"; // TODO
                break;
            case "startAuction":
                res = "startAuction todo"; // TODO
                break;
            case "endAuction":
                res = "endAuction todo"; // TODO
                break;
            case "createAuction":
                res = createAuction(json);
                break;
            case "getAuction":
                res = getAuction(json);
                break;
            case "seeAllUserAuctions":
                res = seeAllUserAuctions(json);
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
        }

        boolean updateBidSuccess = updateBid(auction, newBid);
        String updateBidMessage = "";

        if (updateBidSuccess) { // insert bid and update auction
            updateBidMessage = "success";

            //String insertSQL = "INSERT INTO bids (auctionID, bid) VALUES ('" + auctionID + "', '" + bidJSON + "');";
            String insertSQL = "INSERT INTO bids (auctionID, bid) VALUES (?, cast(? as json));";
            try (java.sql.Connection connection = getPostgresConnection();
                 PreparedStatement pst = connection.prepareStatement(insertSQL)) {
                pst.setString(1, auctionID.toString());
                pst.setString(2, bidJSON.toString());

                System.out.println(pst);////////////////////////
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

                System.out.println(pst);////////////////////////
                pst.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
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
            // TODO: bid history (here or in bid())
            auction.put("currPrice", bid.getBid());
            auction.put("currWinner", bid.getBidder().toString());
            return true;
        }
        return false;
    }


    private static String createAuction(JSONObject json) {
        String auctionID = UUID.randomUUID().toString();
        String listingType = json.getString("listingType");
        String itemID = json.getString("itemID");
        double startPrice = json.getDouble("startPrice");

        long startTime = -1;
        long endTime = -1;
        String status = "ACTIVE";
        if (listingType.equals("AUCTION")) {
            startTime = json.getLong("startTime");
            endTime = json.getLong("endTime");
            status = "PENDING";
        }

        String insertSQL = "INSERT INTO auctions VALUES (?, ?, ?, ?, ?, ?, ?, ?);";
        try (java.sql.Connection connection = getPostgresConnection();
             PreparedStatement pst = connection.prepareStatement(insertSQL)) {
            pst.setString(1, auctionID);
            pst.setString(2, listingType);
            pst.setString(3, itemID);
            pst.setLong(4, startTime);
            pst.setLong(5, endTime);
            pst.setDouble(6, startPrice);
            pst.setNull(7, VARCHAR);
            pst.setString(8, status);

            System.out.println(pst);////////////////////////
            pst.executeUpdate();

            // TODO: add auction to list
        } catch (Exception e) {
            e.printStackTrace();
        }

        JSONObject res = new JSONObject();
        res.put("success", true);
        res.put("auctionID", auctionID);
        return res.toString();
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
                String status = rs.getString("status");

                auction = new Auction(auctionID, listingType, itemID, startTime, endTime, currPrice, currWinner, status);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        assert auction != null;
        JSONObject res = new JSONObject(auction);

        //System.out.println(res);

        res.put("success", true);
        return res.toString();
    }


    private static String seeAllUserAuctions(JSONObject json) {
        String userID = json.getString("userID");
        List<Auction> auctions = new ArrayList<>();

        String selectSQL = "SELECT * from auctions WHERE auctionID IN (SELECT DISTINCT auctionID from bids WHERE bid->>'bidder' = '" + userID + "');";
        try (java.sql.Connection connection = getPostgresConnection();
             PreparedStatement pst = connection.prepareStatement(selectSQL)) {
            //System.out.println(pst);
            ResultSet rs = pst.executeQuery();

            while (rs.next()) {
                String auctionID = rs.getString("auctionID");
                String listingType = rs.getString("listingType");
                String itemID = rs.getString("itemID");
                long startTime = rs.getLong("startTime");
                long endTime = rs.getLong("endTime");
                double currPrice = rs.getDouble("currPrice");
                String currWinner = rs.getString("currWinner");
                String status = rs.getString("status");

                Auction auction = new Auction(auctionID, listingType, itemID, startTime, endTime, currPrice, currWinner, status);
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
                String status = rs.getString("status");

                Auction auction = new Auction(auctionID, listingType, itemID, startTime, endTime, currPrice, currWinner, status);
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
            //System.out.println(pst);
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


    public static void main( String[] args ) throws Exception {
        System.out.println("My Auction Service");

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("172.17.0.4");

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
