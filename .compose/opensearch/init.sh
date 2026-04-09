#!/bin/bash
set -e

OPENSEARCH_URL="http://opensearch:9200"
DASHBOARDS_URL="http://opensearch-dashboards:5601"

echo "Waiting for OpenSearch to be ready..."
until curl -fs "$OPENSEARCH_URL/_cluster/health" > /dev/null 2>&1; do
  sleep 2
done
echo "OpenSearch is ready."

echo "Creating index template 'events'..."
curl -fs -X PUT "$OPENSEARCH_URL/_index_template/events" \
  -H 'Content-Type: application/json' \
  -d @/templates/events-template.json

echo ""
echo "Index template 'events' created successfully."

echo "Waiting for OpenSearch Dashboards to be ready..."
until curl -fs "$DASHBOARDS_URL/api/status" > /dev/null 2>&1; do
  sleep 3
done
echo "OpenSearch Dashboards is ready."

echo "Importing reops-umami dashboard..."
curl -fs -X POST "$DASHBOARDS_URL/api/saved_objects/_import?overwrite=true" \
  -H 'osd-xsrf: true' \
  --form file=@/templates/reops-umami-dashboard.ndjson

echo ""
echo "Dashboard 'reops-umami' imported successfully."
