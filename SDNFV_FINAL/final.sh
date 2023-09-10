#!/bin/bash

mvn clean install -DskipTests

# Setup demo environment
cd $ONOS_ROOT
ok clean

sudo ./build_topo.sh && onos-netcfg localhost config.json && sudo ./dhcp_start.sh

onos-app localhost install! target/bridge-1.0-SNAPSHOT.oar &&
onos-app localhost install! target/proxyarp-1.0-SNAPSHOT.oar &&
onos-app localhost install! target/unicastdhcp-1.0-SNAPSHOT.oar &&
onos-app localhost install! vrouter/target/vrouter-1.0-SNAPSHOT.oar

onos-app localhost deactivate nycu.sdnfv.vrouter && onos-app localhost uninstall nycu.sdnfv.vrouter && onos-app localhost install! vrouter/target/vrouter-1.0-SNAPSHOT.oar

# Test
sudo docker exec -it h01 ping 192.168.50.2 -c 3
sudo docker exec -it h01 ping 192.168.51.1 -c 3

onos localhost
intents
routes
interfaces

# Stop
sudo ./clean_topo.sh && sudo killall dhcpd