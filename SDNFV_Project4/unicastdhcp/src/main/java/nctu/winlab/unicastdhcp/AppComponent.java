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
package nctu.winlab.unicastdhcp;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import org.onlab.packet.IPv4;
import org.onlab.packet.TpPort;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onlab.packet.UDP;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.FilteredConnectPoint;
//import org.onosproject.net.DeviceId;
//import org.onosproject.net.PortNumber;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import com.google.common.collect.Maps;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent implements SomeInterface {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final DhcpConfigListener cfgListener = new DhcpConfigListener();
    private final ConfigFactory<ApplicationId, DhcpConfig> factory = new ConfigFactory<ApplicationId, DhcpConfig>(
        APP_SUBJECT_FACTORY, DhcpConfig.class, "UnicastDhcpConfig") {
        @Override
        public DhcpConfig createConfig() {
            return new DhcpConfig();
        }
    };

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;

    private MyPacketProcessor processor = new MyPacketProcessor();

    private ApplicationId appId;

    private ConnectPoint serverLocation;

    protected Map<MacAddress, ConnectPoint> macToCp;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nctu.winlab.unicastdhcp");
        cfgService.addListener(cfgListener);
        cfgService.registerConfigFactory(factory);
        packetService.addProcessor(processor, PacketProcessor.director(2));

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);

        macToCp = Maps.newConcurrentMap();

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.removeListener(cfgListener);
        cfgService.unregisterConfigFactory(factory);
        packetService.removeProcessor(processor);
        processor = null;

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);

        log.info("Stopped");
    }

    private class DhcpConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
                && event.configClass().equals(DhcpConfig.class)) {
                DhcpConfig config = cfgService.getConfig(appId, DhcpConfig.class);
                if (config != null) {
                    serverLocation = ConnectPoint.deviceConnectPoint(config.name());
                    log.info("DHCP server is connected to '{}', port '{}'",
                        serverLocation.deviceId(), serverLocation.port());
                }
            }
        }
    }

    private class MyPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt.getEtherType() != Ethernet.TYPE_IPV4) {
                return;
            }

            MacAddress srcMac = ethPkt.getSourceMAC();
            MacAddress dstMac = ethPkt.getDestinationMAC();

            ConnectPoint ingressPoint, ingressPoint2;
            ConnectPoint egressPoint, egressPoint2;

            TrafficSelector selector, selector2;
            TrafficTreatment treatment, treatment2;

            //If we've already add intents on this host-server connection , we don't add twice
            if (macToCp.containsKey(srcMac)) {
                context.treatmentBuilder().setOutput(serverLocation.port());
                context.send();
                return;
            }
            //Otherwise, at the beginning, we need to record the mac-cp information
            macToCp.putIfAbsent(srcMac, pkt.receivedFrom());


            ingressPoint = pkt.receivedFrom();
            egressPoint = serverLocation;
            selector = DefaultTrafficSelector.builder()
                .matchEthSrc(srcMac)
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_UDP)
                .matchUdpSrc(TpPort.tpPort(UDP.DHCP_CLIENT_PORT))
                .matchUdpDst(TpPort.tpPort(UDP.DHCP_SERVER_PORT))
                .build();
            treatment = DefaultTrafficTreatment.builder()
                .setOutput(egressPoint.port())
                .build();

            PointToPointIntent intent = PointToPointIntent.builder()
                .appId(appId)
                .selector(selector)
                .treatment(treatment)
                .filteredIngressPoint(new FilteredConnectPoint(ingressPoint))
                .filteredEgressPoint(new FilteredConnectPoint(egressPoint))
                .priority(Intent.DEFAULT_INTENT_PRIORITY)
                .build();

            intentService.submit(intent);
            log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
                ingressPoint.deviceId(), ingressPoint.port(), egressPoint.deviceId(), egressPoint.port());


            ingressPoint2 = serverLocation;
            egressPoint2 = pkt.receivedFrom();
            selector2 = DefaultTrafficSelector.builder()
                .matchEthDst(srcMac)
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_UDP)
                .matchUdpSrc(TpPort.tpPort(UDP.DHCP_SERVER_PORT))
                .matchUdpDst(TpPort.tpPort(UDP.DHCP_CLIENT_PORT))
                .build();
            treatment2 = DefaultTrafficTreatment.builder()
                .setOutput(egressPoint2.port())
                .build();

            PointToPointIntent intent2 = PointToPointIntent.builder()
                .appId(appId)
                .selector(selector2)
                .treatment(treatment2)
                .filteredIngressPoint(new FilteredConnectPoint(ingressPoint2))
                .filteredEgressPoint(new FilteredConnectPoint(egressPoint2))
                .priority(Intent.DEFAULT_INTENT_PRIORITY)
                .build();

            intentService.submit(intent2);
            log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
                ingressPoint2.deviceId(), ingressPoint2.port(), egressPoint2.deviceId(), egressPoint2.port());

            context.treatmentBuilder().setOutput(egressPoint.port());
            context.send();
        }
    }
}