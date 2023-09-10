#!/bin/bash

### ------ window 1 ------
cd $ONOS_ROOT
ok clean


### ------ window 2 ------
cd ProxyArp

mvn clean install -DskipTests

onos-app localhost deactivate nctu.winlab.ProxyArp && onos-app localhost uninstall nctu.winlab.ProxyArp && onos-app localhost install! target/ProxyArp-1.0-SNAPSHOT.oar


### ------ window 3 ------
sudo mn --controller=remote,127.0.0.1:6653 --topo=tree,depth=3,fanout=3 --switch=ovs,protocols=OpenFlow14
