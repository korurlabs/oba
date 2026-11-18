#!/bin/sh
set -e

CONNECTION_STRING="DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://azurite:10000/devstoreaccount1"

CONTAINERS="dev-container"

echo "Initializing Azure Blob Storage containers..."

for container in $CONTAINERS; do
  if az storage container create --name "$container" --connection-string "$CONNECTION_STRING" --output none 2>/dev/null; then
    echo "  Created container: $container"
  else
    echo "  Container already exists or skipped: $container"
  fi
done

echo "Container initialization complete."
