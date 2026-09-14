-- ===========================================================================
--  Repeatable seed: roles + permissions. Idempotent, safe to re-run on every
--  deploy. Adding a permission = edit this file, Flyway reapplies it.
-- ===========================================================================

INSERT INTO roles (code, name, is_system) VALUES
    ('customer', 'Customer',      TRUE),
    ('seller',   'Seller',        TRUE),
    ('admin',    'Administrator', TRUE),
    ('support',  'Support Agent', TRUE)
ON DUPLICATE KEY UPDATE name = VALUES(name), is_system = VALUES(is_system);

INSERT INTO permissions (code, module, description) VALUES
    -- customer surface
    ('profile.read',            'identity',  'Read own profile'),
    ('profile.edit',            'identity',  'Edit own profile'),
    ('address.manage',          'identity',  'Manage own addresses'),
    ('order.place',             'ordering',  'Place an order'),
    ('order.read.own',          'ordering',  'Read own orders'),
    ('review.write',            'review',    'Write a review for a purchased item'),
    -- seller surface
    ('seller.profile.edit',     'seller',    'Edit own storefront'),
    ('seller.kyc.submit',       'seller',    'Submit KYC documents'),
    ('product.create',          'catalog',   'Create a product'),
    ('product.edit',            'catalog',   'Edit own product'),
    ('product.publish',         'catalog',   'Publish own product'),
    ('inventory.adjust',        'inventory', 'Adjust own stock'),
    ('order.read.seller',       'ordering',  'Read orders for own storefront'),
    ('order.fulfil',            'ordering',  'Pack and ship own order items'),
    ('payout.read.own',         'ledger',    'Read own payouts'),
    -- admin surface
    ('user.read',               'identity',  'Read any user'),
    ('user.suspend',            'identity',  'Suspend or reactivate a user'),
    ('role.assign',             'identity',  'Grant or revoke roles'),
    ('seller.approve',          'seller',    'Approve or reject seller onboarding'),
    ('seller.suspend',          'seller',    'Suspend a seller'),
    ('kyc.verify',              'seller',    'Verify KYC documents'),
    ('product.moderate',        'catalog',   'Moderate any product'),
    ('price.override',          'catalog',   'Override any price'),
    ('order.read.all',          'ordering',  'Read any order'),
    ('order.cancel.any',        'ordering',  'Cancel any order'),
    ('refund.initiate',         'payment',   'Initiate a refund'),
    ('payout.approve',          'ledger',    'Approve a seller payout'),
    ('ledger.read',             'ledger',    'Read the financial ledger'),
    ('review.moderate',         'review',    'Approve or reject a review'),
    ('config.publish',          'sdui',      'Publish theme or screen config'),
    ('flag.manage',             'flags',     'Manage feature flags'),
    ('audit.read',              'platform',  'Read audit logs')
ON DUPLICATE KEY UPDATE module = VALUES(module), description = VALUES(description);

-- customer
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.code IN ('profile.read','profile.edit','address.manage','order.place','order.read.own','review.write')
 WHERE r.code = 'customer'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- seller (a seller is also a customer of the platform, so include the basics)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.code IN ('profile.read','profile.edit','address.manage',
                'seller.profile.edit','seller.kyc.submit',
                'product.create','product.edit','product.publish',
                'inventory.adjust','order.read.seller','order.fulfil','payout.read.own')
 WHERE r.code = 'seller'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- support: read-heavy, no money movement
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.code IN ('user.read','order.read.all','review.moderate','audit.read')
 WHERE r.code = 'support'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- admin: everything
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
 WHERE r.code = 'admin'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
