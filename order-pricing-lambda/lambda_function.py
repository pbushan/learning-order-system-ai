from decimal import Decimal

def lambda_handler(event, context):
    total_amount = Decimal(str(event.get("totalAmount", "0")))

    if total_amount < Decimal("50"):
        return {
            "shippingType": "STANDARD",
            "estimatedDeliveryDays": 5
        }

    if total_amount < Decimal("100"):
        return {
            "shippingType": "PRIORITY",
            "estimatedDeliveryDays": 2
        }

    return {
        "shippingType": "EXPRESS",
        "estimatedDeliveryDays": 1
    }
