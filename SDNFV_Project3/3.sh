#!/bin/bash

cd $ONOS_ROOT
bazel run onos-local -- clean debug

onos localhost

onos-create-app

mvn clean install -DskipTests

onos-app localhost deactivate nctu.winlab.bridge && onos-app localhost uninstall nctu.winlab.bridge && onos-app localhost install! target/bridge-063-1.0-SNAPSHOT.oar

sudo mn --controller=remote,127.0.0.1:6653 --topo=tree,depth=2 --switch=ovs,protocols=OpenFlow14

log.info("Add an entry to the port table of `{device ID}`. MAC address: `{MAC}` => Port: `{port}`.");
log.info("MAC address `{MAC}` is missed on `{device ID}`. Flood the packet.");
log.info("MAC address `{MAC}` is matched on `{device ID}`. Install a flow rule.");
