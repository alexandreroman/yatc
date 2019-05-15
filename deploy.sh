#!/bin/sh
cf push
cf add-network-policy yatc-connections --destination-app yatc-users
cf add-network-policy yatc-posts --destination-app yatc-users
cf add-network-policy yatc-feeds --destination-app yatc-posts
cf add-network-policy yatc-feeds --destination-app yatc-connections
cf add-network-policy yatc-feeds --destination-app yatc-users
cf add-network-policy yatc-gateway --destination-app yatc-connections
cf add-network-policy yatc-gateway --destination-app yatc-users
cf add-network-policy yatc-gateway --destination-app yatc-posts
cf add-network-policy yatc-gateway --destination-app yatc-feeds
cf add-network-policy yatc-gateway --destination-app yatc-search
