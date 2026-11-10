#!/usr/bin/env bash
set -euo pipefail

CERT_DIR="${TLS_CERT_DIR:-certs}"
: "${TLS_KEYSTORE_PASSWORD:?Set TLS_KEYSTORE_PASSWORD in .env}"
: "${TLS_TRUSTSTORE_PASSWORD:?Set TLS_TRUSTSTORE_PASSWORD in .env}"
: "${KAFKA_SASL_USERNAME:?Set KAFKA_SASL_USERNAME in .env}"
: "${KAFKA_SASL_PASSWORD:?Set KAFKA_SASL_PASSWORD in .env}"

if [ -f "$CERT_DIR/.complete" ] && [ "${FORCE_CERT_REGEN:-0}" != "1" ]; then
  echo "TLS certificates already present in $CERT_DIR (set FORCE_CERT_REGEN=1 to regenerate)."
  exit 0
fi

umask 077
mkdir -p "$CERT_DIR"

echo "Generating local development CA..."
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out "$CERT_DIR/ca.key" 2>/dev/null
openssl req -x509 -new -key "$CERT_DIR/ca.key" -sha256 -days 3650 \
  -subj "/CN=Fitness Local Dev CA/O=Fitness Microservice Development" \
  -addext "basicConstraints=critical,CA:TRUE" \
  -addext "keyUsage=critical,keyCertSign,cRLSign" \
  -out "$CERT_DIR/ca.crt"

for service in eureka userservice activityservice aiservice kafka; do
  echo "Generating certificate for $service..."
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$CERT_DIR/$service.key" 2>/dev/null
  openssl req -new -key "$CERT_DIR/$service.key" -subj "/CN=$service/O=Fitness Microservice Development" \
    -out "$CERT_DIR/$service.csr"
  extfile="$(mktemp)"
  cat > "$extfile" <<EOF
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth,clientAuth
subjectAltName=DNS:$service,DNS:localhost,IP:127.0.0.1
EOF
  openssl x509 -req -in "$CERT_DIR/$service.csr" \
    -CA "$CERT_DIR/ca.crt" -CAkey "$CERT_DIR/ca.key" -CAcreateserial \
    -days 825 -sha256 -extfile "$extfile" -out "$CERT_DIR/$service.crt"
  rm -f "$extfile"
  openssl pkcs12 -export \
    -in "$CERT_DIR/$service.crt" \
    -inkey "$CERT_DIR/$service.key" \
    -certfile "$CERT_DIR/ca.crt" \
    -name "$service" \
    -out "$CERT_DIR/$service.p12" \
    -passout "pass:$TLS_KEYSTORE_PASSWORD"
  rm -f "$CERT_DIR/$service.csr"
done

echo "Creating truststore..."
rm -f "$CERT_DIR/truststore.p12"
keytool -importcert -noprompt -alias fitness-local-dev-ca \
  -file "$CERT_DIR/ca.crt" \
  -keystore "$CERT_DIR/truststore.p12" \
  -storetype PKCS12 \
  -storepass "$TLS_TRUSTSTORE_PASSWORD" >/dev/null

cat > "$CERT_DIR/kafka-client.properties" <<EOF
security.protocol=SASL_SSL
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="$KAFKA_SASL_USERNAME" password="$KAFKA_SASL_PASSWORD";
ssl.truststore.location=/etc/kafka/secrets/truststore.p12
ssl.truststore.password=$TLS_TRUSTSTORE_PASSWORD
ssl.truststore.type=PKCS12
ssl.endpoint.identification.algorithm=https
EOF

cat > "$CERT_DIR/kafka_server_jaas.conf" <<EOF
KafkaServer {
  org.apache.kafka.common.security.plain.PlainLoginModule required
  username="broker"
  password="$KAFKA_SASL_PASSWORD"
  user_broker="$KAFKA_SASL_PASSWORD"
  user_$KAFKA_SASL_USERNAME="$KAFKA_SASL_PASSWORD";
};
EOF

printf '%s' "$TLS_KEYSTORE_PASSWORD" > "$CERT_DIR/kafka.keystore.password"
printf '%s' "$TLS_KEYSTORE_PASSWORD" > "$CERT_DIR/kafka.key.password"

chmod 600 "$CERT_DIR"/*.key "$CERT_DIR"/*.p12 "$CERT_DIR/kafka-client.properties" \
  "$CERT_DIR/kafka_server_jaas.conf" "$CERT_DIR/kafka.keystore.password" "$CERT_DIR/kafka.key.password"
chmod 644 "$CERT_DIR"/*.crt
touch "$CERT_DIR/.complete"

echo "Certificates generated in $CERT_DIR."
echo "Load $CERT_DIR/ca.crt as the trusted CA when calling the services."
