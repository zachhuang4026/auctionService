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
    seller varchar(100) NOT NULL,
    status varchar(10) NOT NULL,
    check(listingType IN ('AUCTION', 'BUYNOW')),
    check(status IN ('PENDING', 'ACTIVE', 'CLOSED'))
);

INSERT INTO auctions VALUES ('2948af85-6a10-4921-bc74-5573af7ce114', 'AUCTION', 'c9f3d970-6c89-4c42-a0e2-93e4f510688c',
1669260250, 1669264890, 3.99, '56786785-6a10-4921-bc74-5573af7c7890', '00000000-6a10-4921-bc74-5573af7c7890', 'CLOSED');

INSERT INTO auctions VALUES ('34563456-6a10-4921-bc74-5573af7ce114', 'AUCTION', '005eec2a-cc7b-4d4d-8359-fee30c16815f',
1769260250, 1669264890, 3.99, '34563456-6a10-4921-bc74-5573af707890', '00000000-6a10-4921-bc74-5573af7c7890', 'ACTIVE');

-- for testing bid
INSERT INTO auctions VALUES ('12341234-6a10-4921-bc74-5573af7ce114', 'AUCTION', 'bb6cc00e-0506-471c-967f-2facd1f071f3',
1669260250, 1769264890, 3.99, '12341234-6a10-4921-bc74-5573af7ababa', '00000000-6a10-4921-bc74-5573af7c7890', 'ACTIVE');

-- for case "Auction has not started yet"
INSERT INTO auctions VALUES ('aaaaaaaa-6a10-4921-bc74-5573af7ce114', 'AUCTION', '0867ca8a-36ad-42d4-843c-58257fdf394f',
1869260250, 1969264890, 3.99, NULL, '10101010-6a10-4921-bc74-5573af7c7890', 'PENDING');

-- for end auction early
INSERT INTO auctions VALUES ('66666666-6a10-4921-bc74-5573af7ce114', 'AUCTION', 'ae9a7b04-cfba-4cff-a7c2-900bcc3a6b05',
1569260250, 1769264890, 1, '66666666-6a10-4921-bc74-5573af7ababa', '10101010-6a10-4921-bc74-5573af7c7890', 'ACTIVE');

INSERT INTO auctions VALUES ('11111111-6a10-4921-bc74-5573af7ce114', 'BUYNOW', '41b21438-e501-4ffa-97ed-fce6a680b8a3',
-1, -1, 10.0, NULL, '56786785-6a10-4921-bc74-5573af7c7890', 'ACTIVE');

------------------------------------------

CREATE TABLE bids (
    id SERIAL PRIMARY KEY,
    auctionID varchar(100) REFERENCES auctions(auctionID),
    bid jsonb
);

INSERT INTO bids (auctionID, bid) VALUES ('12341234-6a10-4921-bc74-5573af7ce114',
'{"bidID":"abcabcab-6a10-4921-bc74-5573af7ababa", "auctionID": "12341234-6a10-4921-bc74-5573af7ce114", "itemID":"bb6cc00e-0506-471c-967f-2facd1f071f3", "bid": 4.99, "bidder": "bbbbbbbb-6a10-4921-bc74-5573af7ababa", "time": 1669260280}');
