INSERT INTO events (title, venue, starts_at, capacity, available_seats, ticket_price)
VALUES ('Java Topluluğu Konseri', 'İstanbul Kongre Merkezi', CURRENT_TIMESTAMP + INTERVAL '30 days', 100, 100, 350.00),
       ('Bir Yaz Gecesi Rüyası', 'Ankara Sahne', CURRENT_TIMESTAMP + INTERVAL '45 days', 60, 60, 220.00),
       ('Yazılım ve Mimari Buluşması', 'İzmir Teknoloji Salonu', CURRENT_TIMESTAMP + INTERVAL '60 days', 150, 150, 0.00);
