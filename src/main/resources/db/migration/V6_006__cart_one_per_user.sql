-- One durable cart per signed-in customer. Guest carts keep NULL user_id
-- (MySQL UNIQUE allows multiple NULLs). Concurrent get-or-create then
-- races into this unique key instead of creating two carts.
ALTER TABLE carts
    ADD UNIQUE KEY uk_carts_user (user_id);
