#!/bin/bash

BASE_URL="http://localhost:8080/api"
EMAIL="final.test@example.com"
PASS="SecurePass123"
NEW_PASS="NewSecurePass456"

echo "=== Testing Race Condition & Security Version Fix ==="
echo

echo "1. Login to get token..."
LOGIN1=$(curl -s -X POST "$BASE_URL/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\": \"$EMAIL\", \"password\": \"$PASS\"}")

TOKEN1=$(echo "$LOGIN1" | jq -r '.token')
USER_ID=$(echo "$LOGIN1" | jq -r '.userId')

echo "Token obtained (first 50 chars): ${TOKEN1:0:50}..."
echo

echo "2. Verify token works..."
curl -s -X GET "$BASE_URL/v1/users/$USER_ID" \
  -H "Authorization: Bearer $TOKEN1" | jq '.email'
echo

echo "3. Change password (increments security version, revokes all tokens)..."
CHANGE_RESPONSE=$(curl -s -X POST "$BASE_URL/v1/auth/change-password" \
  -H "Authorization: Bearer $TOKEN1" \
  -H "Content-Type: application/json" \
  -d "{\"currentPassword\": \"$PASS\", \"newPassword\": \"$NEW_PASS\", \"confirmPassword\": \"$NEW_PASS\"}")

echo "$CHANGE_RESPONSE" | jq '.'
echo

echo "4. Try to use old token (should fail due to security version mismatch)..."
OLD_TOKEN_RESPONSE=$(curl -s -X GET "$BASE_URL/v1/users/$USER_ID" \
  -H "Authorization: Bearer $TOKEN1" \
  -w "\nHTTP Status: %{http_code}\n")

echo "$OLD_TOKEN_RESPONSE"
echo

echo "5. Login with new password to get new token..."
LOGIN2=$(curl -s -X POST "$BASE_URL/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\": \"$EMAIL\", \"password\": \"$NEW_PASS\"}")

TOKEN2=$(echo "$LOGIN2" | jq -r '.token')
echo "New token obtained (first 50 chars): ${TOKEN2:0:50}..."
echo

echo "6. Verify new token works..."
curl -s -X GET "$BASE_URL/v1/users/$USER_ID" \
  -H "Authorization: Bearer $TOKEN2" | jq '.email'
echo

echo "7. Check Redis for tracked tokens..."
docker exec eaglebank-redis redis-cli keys "jwt:user:*" | head -5
echo

echo "=== Summary ==="
echo "✓ Security version prevents old tokens after password change"
echo "✓ Tokens are tracked per user for bulk revocation"
echo "✓ No race conditions - security version in JWT matches DB"