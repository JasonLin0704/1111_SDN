#!/bin/bash

sudo ln -s /etc/apparmor.d/usr.sbin.dhcpd /etc/apparmor.d/disable/
sudo apparmor_parser -R /etc/apparmor.d/usr.sbin.dhcpd

sudo /etc/init.d/apparmor stop
sudo sed -i '30i /var/lib/dhcp{,3}/dhcpclient* lrw,' /etc/apparmor.d/sbin.dhclient
sudo /etc/init.d/apparmor start

### ------ window 1 ------
cd $ONOS_ROOT
ok clean


### ------ window 2 ------
cd unicastdhcp

mvn clean install -DskipTests

onos-app localhost deactivate nctu.winlab.unicastdhcp && onos-app localhost uninstall nctu.winlab.unicastdhcp && onos-app localhost install! target/unicastdhcp-1.0-SNAPSHOT.oar


### ------ window 3 ------
cd SDNFV_Project4/topo
sudo ./topo.py

h1 dhclient -v h1-eth0


### ------ window 4 ------
cd SDNFV_Project4
onos-netcfg localhost unicastdhcp.json


### ------ window 5 ------
onos localhost
intents


