all:docker-compose.yml
	sudo docker compose up -d
	sudo ovs-vsctl add-br ovsbr
	sudo ovs-docker add-port ovsbr eth1 R1 --ipaddress=172.20.0.2/16
	sudo ovs-docker add-port ovsbr eth1 R2 --ipaddress=172.20.0.3/16
	sudo ovs-docker add-port ovsbr eth1 R3 --ipaddress=172.20.0.4/16
clean:
	sudo docker compose down
	sudo ovs-vsctl del-br ovsbr