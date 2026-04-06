# order-pricing-lambda

This is the separate Lambda repo used by the teaching project.

It receives an order payload and returns:

- shippingType
- estimatedDeliveryDays

Business rule:

- totalAmount < 50 -> STANDARD, 5 days
- totalAmount < 100 -> PRIORITY, 2 days
- totalAmount >= 100 -> EXPRESS, 1 day
