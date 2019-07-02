#!/bin/sh
cf delete -r -f yatc-webui
cf delete -r -f yatc-users
cf delete -r -f yatc-connections
cf delete -r -f yatc-feeds
cf delete -r -f yatc-search
cf delete -r -f yatc-posts
cf delete -r -f yatc-gateway
