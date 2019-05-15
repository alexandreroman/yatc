#!/bin/sh
cf create-service p-service-registry trial service-registry
cf create-service p.redis cache-small redis
cf create-service cleardb spark posts-db
cf create-service cleardb spark connections-db
cf create-service cleardb spark feeds-db
cf create-service cloudamqp lemur message-broker
cf create-service -c '{"git": { "uri": "https://github.com/alexandreroman/yatc-config", "cloneOnStart": "true" }}' p-config-server trial config-server
