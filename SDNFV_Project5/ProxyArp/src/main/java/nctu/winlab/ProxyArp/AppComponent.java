/*
 * Copyright 2022-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nctu.winlab.ProxyArp;

import org.onlab.packet.ARP;
import org.onlab.packet.IPv4;
import org.onlab.packet.IpAddress;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.PortNumber;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.host.HostService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import com.google.common.collect.Maps;
import com.google.common.collect.Lists;

@Component(immediate = true)

public class AppComponent implements SomeInterface {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected EdgePortService edgePortService;

    private MyPacketProcessor processor = new MyPacketProcessor();

    private ApplicationId appId;

    protected Map<Ip4Address, MacAddress> arpTable;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nctu.winlab.ProxyArp");
        packetService.addProcessor(processor, PacketProcessor.director(2));

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);

        arpTable = Maps.newConcurrentMap();

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        packetService.removeProcessor(processor);
        processor = null;

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);

        log.info("Stopped");
    }

    private class MyPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {

            if(context.isHandled()){
                return;
            }

            InboundPacket inPkt = context.inPacket();
            Ethernet ethPkt = inPkt.parsed();

            if (ethPkt.getEtherType() != Ethernet.TYPE_ARP) {
                return;
            }

            ARP arp = (ARP) ethPkt.getPayload();
            short op = arp.getOpCode();
            Ip4Address srcIp = Ip4Address.valueOf(arp.getSenderProtocolAddress());
            Ip4Address dstIp = Ip4Address.valueOf(arp.getTargetProtocolAddress());

            MacAddress srcMac = ethPkt.getSourceMAC();
            MacAddress dstMac = ethPkt.getDestinationMAC();

            ConnectPoint cp = inPkt.receivedFrom();
            log.info("Receive a packet from {}", cp);

            //Learns address mappings of the sender
            arpTable.putIfAbsent(srcIp, srcMac);

            //For request or Reply
            if (op == ARP.OP_REQUEST) {
                if (arpTable.containsKey(dstIp)) {
                    log.info("TABLE HIT. Requested MAC = {}", arpTable.get(dstIp));
                    TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(cp.port()).build();
                    Ethernet arpReply = ARP.buildArpReply(dstIp, arpTable.get(dstIp), ethPkt);
                    ByteBuffer byteBuffer = ByteBuffer.wrap(arpReply.serialize());
                    OutboundPacket outPkt = new DefaultOutboundPacket(cp.deviceId(), treatment, byteBuffer);
                    packetService.emit(outPkt);
                } else {
                    log.info("TABLE MISS. Send request to edge ports");
                    ByteBuffer byteBuffer = inPkt.unparsed();
                    List<ConnectPoint> points = Lists.newArrayList(edgePortService.getEdgePoints());
                    for (ConnectPoint point : points) {
                        if(point.compareTo(cp) != 0) {
                            //log.info("Packet out to {}", point);
                            TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(point.port()).build();
                            OutboundPacket outPkt = new DefaultOutboundPacket(point.deviceId(), treatment, byteBuffer);
                            packetService.emit(outPkt);
                        } 
                    }
                }
            } else if (op == ARP.OP_REPLY) {
                log.info("RECV REPLY. Requested MAC = {}", srcMac);
            }  
        }
    }
}
