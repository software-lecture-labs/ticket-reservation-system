CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) COLLATE "C" NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(5) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT users_email_format CHECK (
        length(split_part(email, '@', 1)) <= 64
        AND email ~ $email$^[a-z0-9!#$%&'*+/=?^_`{|}~-]+([.][a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?([.][a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$$email$
    )
);
