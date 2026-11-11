UPDATE inventory SET product_name = 'Everyday canvas tote', category = 'Accessories' WHERE product_id = 1;
INSERT INTO inventory (product_id, available, product_name, category, reorder_point) VALUES
    (2, 24, 'Studio ceramic mug', 'Home & living', 8),
    (3, 36, 'Pocket notebook set', 'Stationery', 10),
    (4, 8, 'Ribbed cotton socks', 'Accessories', 5)
ON CONFLICT (product_id) DO NOTHING;
