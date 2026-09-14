-- Manual pickup identifiers shown to the customer on tracking.
ALTER TABLE shipments
    ADD COLUMN tracking_number     VARCHAR(80) NULL AFTER awb_number,
    ADD COLUMN consignment_number  VARCHAR(80) NULL AFTER tracking_number,
    ADD COLUMN docket_number       VARCHAR(80) NULL AFTER consignment_number;
