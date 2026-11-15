ALTER TABLE reservations
    ADD COLUMN user_id UUID REFERENCES users(id),
    ADD CONSTRAINT reservations_owner_required CHECK (user_id IS NOT NULL) NOT VALID;

CREATE INDEX idx_reservations_user ON reservations(user_id);
