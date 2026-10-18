#!/usr/bin/env bash

# Build a stub "DooSo" shared library for local test runs.
#
# DooTask normally loads a licensed FFI library (config: DOOTASK_LIBRARY_PATH)
# that is not distributed with the repository. Unit tests avoid it, but feature
# tests that boot the HTTP middleware or save models go through Doo::load().
# This script compiles a minimal stub with the same symbols so the suite can run
# on a clean local machine. It performs NO licensing, crypto or translation and
# must never be used outside automated tests.
#
# Usage: scripts/build-test-doo-so.sh [output-directory]
set -euo pipefail

output_dir="${1:-}"
if [[ -z "$output_dir" ]]; then
    output_dir="$(mktemp -d /tmp/dootask-doo-stub.XXXXXX)"
fi
mkdir -p "$output_dir"
source_file="$output_dir/doo_stub.c"
library_file="$output_dir/doo_stub.so"

cat > "$source_file" <<'C'
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static char buffer[65536];

void initialize(char *work, char *token, char *lang)
{
    (void)work;
    (void)token;
    (void)lang;
}

char *license(void)
{
    return "{\"ip\":\"\",\"domain\":\"\",\"mac\":\"\",\"email\":\"\",\"people\":0,\"sn\":\"TEST-SN\",\"expired_at\":\"forever\",\"type\":\"test\",\"version\":\"test\"}";
}

char *licenseDecode(char *license_text)
{
    (void)license_text;
    return "{}";
}

char *licenseSave(char *license_text)
{
    (void)license_text;
    return "success";
}

int userId(void)
{
    return 0;
}

char *userExpiredAt(void)
{
    return "forever";
}

char *userEmail(void)
{
    return "test@example.com";
}

char *userEncrypt(void)
{
    return "test";
}

char *userToken(void)
{
    return "";
}

char *userCreate(char *email, char *password)
{
    (void)password;
    snprintf(buffer, sizeof(buffer), "{\"email\":\"%s\"}", email ? email : "");
    return buffer;
}

char *tokenEncode(int userid, char *email, char *encrypt, int days)
{
    (void)encrypt;
    (void)days;
    snprintf(buffer, sizeof(buffer), "test-token:%d:%s", userid, email ? email : "");
    return buffer;
}

char *tokenDecode(char *value)
{
    int userid = 0;
    if (value && strncmp(value, "test-token:", 11) == 0) {
        userid = atoi(value + 11);
    }
    snprintf(buffer, sizeof(buffer),
        "{\"userid\":%d,\"email\":\"test@example.com\",\"encrypt\":\"test\",\"expired_at\":\"forever\"}",
        userid);
    return buffer;
}

char *translate(char *text, char *lang)
{
    (void)lang;
    return text;
}

char *md5s(char *text, char *password)
{
    (void)password;
    return text;
}

char *macs(void)
{
    return "";
}

char *dooSN(void)
{
    return "TEST-SN";
}

char *version(void)
{
    return "test";
}

char *pgpGenerateKeyPair(char *name, char *email, char *passphrase)
{
    (void)name;
    (void)email;
    (void)passphrase;
    return "{}";
}

char *pgpEncrypt(char *plainText, char *publicKey)
{
    (void)publicKey;
    return plainText;
}

char *pgpDecrypt(char *cipherText, char *privateKey, char *passphrase)
{
    (void)privateKey;
    (void)passphrase;
    return cipherText;
}
C

gcc -shared -fPIC -O2 -o "$library_file" "$source_file"
echo "$library_file"
