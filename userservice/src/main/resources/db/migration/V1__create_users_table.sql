CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(255) NOT NULL,
    email VARCHAR(320),
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_role_check CHECK (role IN ('USER', 'ADMIN'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email ON users (email);
