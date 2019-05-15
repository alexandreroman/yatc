#!/bin/sh
cf create-service p-service-registry standard service-registry
cf create-service p.redis cache-small redis
cf create-service p.mysql db-small posts-db
cf create-service p.mysql db-small connections-db
cf create-service p.mysql db-small feeds-db
cf create-service p.rabbitmq single-node-3.7 message-broker
cf create-service -c '{"git": { "uri": "https://github.com/alexandreroman/yatc-config", "cloneOnStart": "true" }}' p-config-server standard config-server
