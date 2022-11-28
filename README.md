## Containers Setup

**RabbitMQ**
```
docker run -d --hostname auction-service-rabbitmq --name auctionServiceRabbitMQ -p 15672:15672 -p 5672:5672 rabbitmq:3-management
docker exec -it auctionServiceRabbitMQ /bin/bash
```

**PostgreSQL**
```
docker import auctionServicePostgres.tar.gz postgres:auctionServicePostgres
docker run --name auctionServicePostgres -e POSTGRES_PASSWORD=abc123 -p 5432:5432 postgres:auctionServicePostgres
docker exec -it auctionServicePostgres /bin/bash
```

**PostgreSQL pgAdmin**  
(don't really need it)  
**Auction Service Server**
```
docker import auctionServiceMsgProducer.tar.gz ubuntu:auctionServiceMsgProducer
docker run -it --hostname auction-service-msgproducer --name auctionServiceMsgProducer ubuntu:auctionServiceMsgProducer /bin/bash
```

**Request Producer for Testing**
```
docker import auctionServiceMsgConsumer.tar.gz ubuntu:auctionServiceMsgConsumer
docker run -it --hostname auction-service-msgconsumer --name auctionServiceMsgConsumer ubuntu:auctionServiceMsgConsumer /bin/bash
```

## Useful Commands
**PostgreSQL**  
```
root@13e5329b2d5a:/# psql -h localhost -U postgres -d auction -W
auction=# \i auction.sql
```

**auction-service-msgconsumer**  
```
root@auction-service-msgconsumer:/src# java -jar auctionService-1.0-SNAPSHOT.jar
```

**auction-service-msgproducer**  
java auctionSendRPC [JSON request]
```
root@auction-service-msgproducer:/src# javac auctionSendRPC.java 
root@auction-service-msgproducer:/src# java auctionSendRPC "{"type":"getAuction","auctionID":"2948af85-6a10-4921-bc74-5573af7ce114"}"
root@auction-service-msgproducer:/src# java auctionSendRPC {"type":"seeActiveAuctions"}
root@auction-service-msgproducer:/src# java auctionSendRPC {"type":"seeClosedAuctions"}

# success
root@auction-service-msgproducer:/src# java auctionSendRPC '{"type":"bid", "auctionID":"12341234-6a10-4921-bc74-5573af7ce114", "bid": 6, "bidder":"cccccccc-6a10-4921-bc74-5573af7ce114"}'
# fail: The new bid is not higher than the current price
root@auction-service-msgproducer:/src# java auctionSendRPC '{"type":"bid", "auctionID":"12341234-6a10-4921-bc74-5573af7ce114", "bid": 6, "bidder":"cccccccc-6a10-4921-bc74-5573af7ce114"}'
# fail: Auction has not started yet
root@auction-service-msgproducer:/src# java auctionSendRPC '{"type":"bid", "auctionID":"aaaaaaaa-6a10-4921-bc74-5573af7ce114", "bid": 6, "bidder":"cccccccc-6a10-4921-bc74-5573af7ce114"}'

# seeAllUserAuctions
root@auction-service-msgproducer:/src# java auctionSendRPC '{"type":"bid", "auctionID":"34563456-6a10-4921-bc74-5573af7ce114", "bid": 4, "bidder":"cccccccc-6a10-4921-bc74-5573af7ce114"}'
root@auction-service-msgproducer:/src# java auctionSendRPC '{"type":"seeAllUserAuctions", "userID":"cccccccc-6a10-4921-bc74-5573af7ce114"}'
```
