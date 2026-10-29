-- Existing installations: inspect before applying; skip if Hibernate added it.
ALTER TABLE booking_request ADD COLUMN retry_of_request_id BIGINT NULL;
