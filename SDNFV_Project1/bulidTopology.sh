#!/bin/bash

cd $ONOS_ROOT
bazel run onos-local -- clean debug

onos localhost

apps -a -s

app activate org.onosproject.openflow
app activate org.onosproject.fwd

sudo mn -c

### Example
sudo mn --topo=linear,3 --controller=remote,127.0.0.1:6653 --switch=ovs,protocols=OpenFlow14
sudo mn --custom=sample.py --topo=mytopo --controller=remote,ip=127.0.0.1,port=6653 --switch=ovs,protocols=OpenFlow14

### Part2
sudo mn --custom=project1_part2_311552063.py --topo=topo_part2_311552063 --controller=remote,ip=127.0.0.1,port=6653 --switch=ovs,protocols=OpenFlow14

### Part3
sudo mn --custom=project1_part3_311552063.py --topo=topo_part3_311552063 --controller=remote,ip=127.0.0.1,port=6653 --switch=ovs,protocols=OpenFlow14
