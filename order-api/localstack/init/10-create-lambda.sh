#!/bin/bash
set -e

echo "Creating local Lambda function..."

mkdir -p /tmp/lambda-package
cp /opt/lambda-src/lambda_function.py /tmp/lambda-package/
cd /tmp/lambda-package
zip -r function.zip lambda_function.py >/dev/null

awslocal lambda create-function \
  --function-name shipping-rule-lambda \
  --runtime python3.12 \
  --handler lambda_function.lambda_handler \
  --role arn:aws:iam::000000000000:role/lambda-role \
  --zip-file fileb:///tmp/lambda-package/function.zip \
  >/tmp/create-lambda-output.txt 2>&1 || cat /tmp/create-lambda-output.txt

echo "Lambda setup complete."
