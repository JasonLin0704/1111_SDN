#!/bin/bash

### ------ window 1 ------
sudo wireshark

### ------ window 2 ------
cd $ONOS_ROOT
ok clean

### ------ window 3 ------
sudo mn --controller=remote,127.0.0.1:6653 --switch=ovs,protocols=OpenFlow14
h1 ping h2 -c 5

sudo mn --custom=topo_311552063.py --topo=mytopo --controller=remote,ip=127.0.0.1,port=6653 --switch=ovs,protocols=OpenFlow14

### ------ window 4 ------

# activate/deactivate onos APPs 
onos localhost
apps -a -s
app activate fwd

# add example flow
curl -u onos:rocks -X POST -H 'Content-Type: application/json' -d @flows1.json 'http://localhost:8181/onos/v1/flows/of:0000000000000001'

# add flow
curl -u onos:rocks -X POST -H 'Content-Type: application/json' -d @flows_s1-1_311552063.json 'http://localhost:8181/onos/v1/flows/of:0000000000000001'

curl -u onos:rocks -X POST -H 'Content-Type: application/json' -d @flows_s2-1_311552063.json 'http://localhost:8181/onos/v1/flows/of:0000000000000002'

curl -u onos:rocks -X POST -H 'Content-Type: application/json' -d @flows_s3-1_311552063.json 'http://localhost:8181/onos/v1/flows/of:0000000000000003'




