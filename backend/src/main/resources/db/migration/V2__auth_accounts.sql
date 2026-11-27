-- auth-accounts (US-AU-01, Decision 1.24): professor accounts. Emails are stored in lower case.
CREATE TABLE professor_account (
    id              UUID PRIMARY KEY,
    email           VARCHAR(320) NOT NULL UNIQUE CHECK (email = lower(email)),
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
