#!/bin/bash

### ------ window 1 ------
cd $ONOS_ROOT
ok clean

### ------ window 2 ------
make clean && make

sudo docker exec h1 ifconfig

onos localhost 
app activate fwd
app activate fpm
routes

sudo docker exec -it h1 ping 172.19.0.3 -c 3
sudo docker exec -it h1 ping 172.22.0.3 -c 3
sudo docker exec -it h2 ping 172.22.0.3 -c 3

sudo docker exec -it R1 bash 
telnet localhost 2605
show ip bgp
exit
