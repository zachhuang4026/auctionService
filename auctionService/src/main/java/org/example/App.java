package org.example;

import com.rabbitmq.client.*;
import org.json.JSONObject;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class App {

    private final static String QUEUE_NAME = "auctionServiceTestQueue";
    private final static String QUEUE_NAME_RPC = "auctionServiceTestQueueRPC";

    private static java.sql.Connection getPostgresConnection() {
        try {
            Class.forName("org.postgresql.Driver");
            String url = "jdbc:postgresql://172.17.0.2:5432/auction";
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
                res = "bid todo";
                break;
            case "startAuction":
                res = "startAuction todo";
                break;
            case "endAuction":
                res = "endAuction todo";
                break;
            case "getAuction":
                res = getAuction(json);
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

    private static String getAuction(JSONObject json) {
        UUID auctionID = UUID.fromString(json.getString("auctionID"));
        Auction auction = null;
        try {
            java.sql.Connection connection = getPostgresConnection();
            PreparedStatement pst = connection.prepareStatement("SELECT * FROM auctions WHERE auctionID = '" + auctionID + "';");
            ResultSet rs = pst.executeQuery();

            while (rs.next()) {
                String itemID = rs.getString("itemID");
                long startTime = rs.getLong("startTime");
                long endTime = rs.getLong("endTime");
                double currPrice = rs.getDouble("currPrice");
                String currWinner = rs.getString("currWinner");
                String status = rs.getString("status");

                auction = new Auction(auctionID.toString(), itemID, startTime, endTime, currPrice, currWinner, status);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        assert auction != null;
        JSONObject res = new JSONObject(auction);
        res.put("success", true);
        return res.toString();
    }

    private static String seeAllAuctions(String requestStatus) {
        List<Auction> auctions = new ArrayList<>();
        try {
            java.sql.Connection connection = getPostgresConnection();
            PreparedStatement pst = connection.prepareStatement("SELECT * FROM auctions WHERE status = '" + requestStatus + "';");
            ResultSet rs = pst.executeQuery();

            while (rs.next()) {
                String auctionID = rs.getString("auctionID");
                String itemID = rs.getString("itemID");
                long startTime = rs.getLong("startTime");
                long endTime = rs.getLong("endTime");
                double currPrice = rs.getDouble("currPrice");
                String currWinner = rs.getString("currWinner");
                String status = rs.getString("status");

                Auction auction = new Auction(auctionID, itemID, startTime, endTime, currPrice, currWinner, status);
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

    public static void main( String[] args ) throws Exception {
        System.out.println( "My Auction Service" );

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

/*
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("172.17.0.4");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.queueDeclare(QUEUE_NAME, false, false, false, null);
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println(" [x] Received '" + message + "'");
            System.out.println("process msg here");
        };

        channel.basicConsume(QUEUE_NAME, true, deliverCallback, consumerTag -> { });
 */


//        JSONObject jo = new JSONObject();
//        jo.put("name", "jon doe");
//        jo.put("age", "22");
//        jo.put("city", "chicago");
//
//        System.out.println(jo.toString());
//
//        JSONObject jo2 = new JSONObject(
//                "{\"itemID\":\"2948af85-6a10-4921-bc74-5573af7ce114\",\"startTime\":1669264255,\"endTime\":1669523455,\"startPrice\":22}"
//        );
//        System.out.println(jo2);
//        System.out.println(jo2.get("itemID"));
//
//        UUID uuid = UUID.randomUUID();
//        System.out.println(uuid);


    }
}
