DROP TABLE IF EXISTS bids;
DROP TABLE IF EXISTS auctions;

------------------------------------------

CREATE TABLE auctions (
    auctionID varchar(100) PRIMARY KEY,
    listingType varchar(100) NOT NULL,
    itemID varchar(100) NOT NULL,
    startTime INTEGER NOT NULL,
    endTime INTEGER NOT NULL,
    currPrice FLOAT NOT NULL,
    currWinner varchar(100),
    status varchar(10) NOT NULL,
--    bidHistory varchar(10000),
    check(listingType IN ('AUCTION', 'BUYNOW')),
    check(status IN ('PENDING', 'ACTIVE', 'CLOSED'))
);

INSERT INTO auctions VALUES ('2948af85-6a10-4921-bc74-5573af7ce114', 'AUCTION', 'd7750589-1d50-464c-9207-e9a96ad46c95',
1669260250, 1669264890, 3.99, '56786785-6a10-4921-bc74-5573af7c7890', 'CLOSED');

INSERT INTO auctions VALUES ('34563456-6a10-4921-bc74-5573af7ce114', 'AUCTION', '34563456-1d50-464c-9207-e9a96ad46c95',
1669260250, 1669264890, 3.99, '34563456-6a10-4921-bc74-5573af707890', 'ACTIVE');

-- for testing bid
INSERT INTO auctions VALUES ('12341234-6a10-4921-bc74-5573af7ce114', 'AUCTION', '12341234-1d50-464c-9207-e9a96ad46c95',
1669260250, 1769264890, 3.99, '12341234-6a10-4921-bc74-5573af7ababa', 'ACTIVE');

-- for case "Auction has not started yet"
INSERT INTO auctions VALUES ('aaaaaaaa-6a10-4921-bc74-5573af7ce114', 'AUCTION', 'aaaaaaaa-1d50-464c-9207-e9a96ad46c95',
1869260250, 1969264890, 3.99, NULL, 'PENDING');

------------------------------------------

CREATE TABLE bids (
    id SERIAL PRIMARY KEY,
    auctionID varchar(100) REFERENCES auctions(auctionID),
    bid jsonb
);

INSERT INTO bids (auctionID, bid) VALUES ('12341234-6a10-4921-bc74-5573af7ce114',
'{"bidID":"abcabcab-6a10-4921-bc74-5573af7ababa", "auctionID": "12341234-6a10-4921-bc74-5573af7ce114", "itemID":"12341234-1d50-464c-9207-e9a96ad46c95", "bid": 4.99, "bidder": "bbbbbbbb-6a10-4921-bc74-5573af7ababa", "time": 1669260280}');
