-- Product offers (seller-configurable, separate from badges/highlights).
ALTER TABLE products
    ADD COLUMN offers JSON NULL AFTER badges;

-- One shipment per seller per order; reuse instead of duplicating.
DELETE s2
  FROM shipments s1
  JOIN shipments s2
    ON s1.seller_id = s2.seller_id
   AND s1.order_id = s2.order_id
   AND s2.id > s1.id;

ALTER TABLE shipments
    ADD UNIQUE KEY uk_shipments_seller_order (seller_id, order_id);

-- An order line may appear on only one shipment.
DELETE si2
  FROM shipment_items si1
  JOIN shipment_items si2
    ON si1.order_item_id = si2.order_item_id
   AND si2.id > si1.id;

ALTER TABLE shipment_items
    ADD UNIQUE KEY uk_shipment_items_order_item (order_item_id);

-- Prefix index for catalog search (existing LIKE query).
CREATE INDEX idx_products_name_prefix ON products (name(100));
