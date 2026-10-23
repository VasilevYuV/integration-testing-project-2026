CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    product VARCHAR(200) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 1000),
    unit_price DECIMAL(10, 2) NOT NULL CHECK (unit_price > 0)
);
