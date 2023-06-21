#!/bin/bash

while IFS= read -r id; do
  url="https://eu-build-cache.gradle.org/cache/$id"
  status_code=$(curl -sL -o /dev/null -w "%{http_code}" "$url")
  if [ "$status_code" -eq 200 ]; then
    echo "$id"
  fi
done
