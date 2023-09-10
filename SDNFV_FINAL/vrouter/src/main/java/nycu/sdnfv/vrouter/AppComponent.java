/*
 * Copyright 2023-present Open Networking Foundation
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
package nycu.sdnfv.vrouter;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.IPv4;
import org.onlab.packet.TpPort;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onlab.packet.UDP;
import org.onlab.packet.IPacket;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.FilteredConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.Host;
import org.onosproject.net.host.HostService;
import org.onosproject.net.edge.EdgePortService;
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
import org.onosproject.net.intent.MultiPointToSinglePointIntent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.IntentState;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.config.Config;
import org.onosproject.net.intf.Interface;
import org.onosproject.net.intf.InterfaceService;
import org.onosproject.routeservice.RouteTableId;
import org.onosproject.routeservice.RouteInfo;
import org.onosproject.routeservice.ResolvedRoute;
import org.onosproject.routeservice.Route;
import org.onosproject.routeservice.RouteService;
import org.onosproject.routeservice.*;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.List;
import com.google.common.collect.Maps;
import com.google.common.collect.Lists;
import java.util.Set;
import java.util.HashSet;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent implements SomeInterface {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final QuaggaConfigListener cfgListener = new QuaggaConfigListener();
    private final ConfigFactory<ApplicationId, QuaggaConfig> factory = new ConfigFactory<ApplicationId, QuaggaConfig>(
        APP_SUBJECT_FACTORY, QuaggaConfig.class, "router") {
        @Override
        public QuaggaConfig createConfig() {
            return new QuaggaConfig();
        }
    };

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected EdgePortService edgePortService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected InterfaceService intfService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected RouteService routeService;

    private MyPacketProcessor processor = new MyPacketProcessor();

    private ApplicationId appId;

    private ConnectPoint quaggaCp;
    private MacAddress quaggaMac;
    private Ip4Address virtualIp;
    private MacAddress virtualMac;
    private List<Ip4Address> peersIp;
    //private List<Ip4Address> quaggaIp;
    private List<Route> routes;

    private boolean hasBuildTransitIntents;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nycu.sdnfv.vrouter");
        cfgService.addListener(cfgListener);
        cfgService.registerConfigFactory(factory);
        packetService.addProcessor(processor, PacketProcessor.director(6));

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
        
        hasBuildTransitIntents = false;

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {

        // Clear intents
        for (Intent i : intentService.getIntents()) {
            if (intentService.getIntentState(i.key()) == IntentState.INSTALLED) {
                intentService.withdraw(i);
            }
            if (intentService.getIntentState(i.key()) == IntentState.WITHDRAWN) {
                intentService.purge(i);
            }
        }

        cfgService.removeListener(cfgListener);
        cfgService.unregisterConfigFactory(factory);
        packetService.removeProcessor(processor);
        processor = null;

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);

        log.info("Stopped");
    }

    private class QuaggaConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
                && event.configClass().equals(QuaggaConfig.class)) {
                QuaggaConfig config = cfgService.getConfig(appId, QuaggaConfig.class);
                if (config != null) {
                    quaggaCp = ConnectPoint.deviceConnectPoint(config.information("quagga"));
                    quaggaMac = MacAddress.valueOf(config.information("quagga-mac"));
                    virtualIp = Ip4Address.valueOf(config.information("virtual-ip"));
                    virtualMac = MacAddress.valueOf(config.information("virtual-mac"));
                    peersIp = config.getPeers("peers");
                    
                    log.info("#################################");
                    log.info("quaggaCp: {}", quaggaCp.toString());
                    log.info("quaggaMac: {}", quaggaMac.toString());
                    log.info("virtualIp: {}", virtualIp.toString());
                    log.info("virtualMac: {}", virtualMac.toString());
                    for (Ip4Address ip : peersIp) {
                        log.info("peerIp: {}", ip.toString());
                    }
                    log.info("#################################");

                    /*
                    quaggaIp = Lists.newArrayList();
                    quaggaIp.add(Ip4Address.valueOf("172.30.1.1"));
                    quaggaIp.add(Ip4Address.valueOf("172.30.2.1"));
                    quaggaIp.add(Ip4Address.valueOf("172.30.3.1"));
                    */

                    ConnectPoint ingressPoint, egressPoint;
                    IpPrefix matchDstIp = IpPrefix.IPV4_LINK_LOCAL_PREFIX;  //default, won't be used
                    MacAddress newSrcMac = MacAddress.ZERO;                 //default, won't be used
                    MacAddress newDstMac = MacAddress.ZERO;                 //default, won't be used

                    /* BGP traffic */
                    for (Ip4Address peerIp : peersIp) {
                        Interface intf = intfService.getMatchingInterface(peerIp);
                        
                        // Outgoing eBGP
                        ingressPoint = quaggaCp;
                        egressPoint = intf.connectPoint();
                        matchDstIp = IpPrefix.valueOf(peerIp, 24);
                        buildBGPIntent(ingressPoint, egressPoint, matchDstIp);

                        // Incoming eBGP
                        ingressPoint = intf.connectPoint();
                        egressPoint = quaggaCp;
                        matchDstIp = intf.ipAddressesList().get(0).subnetAddress();
                        buildBGPIntent(ingressPoint, egressPoint, matchDstIp);
                    }
                }
            }
        }
    }

    private class MyPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            InboundPacket inPkt = context.inPacket();
            ConnectPoint inCp = inPkt.receivedFrom();

            Ethernet ethPkt = inPkt.parsed();
            MacAddress srcMac = ethPkt.getSourceMAC();
            MacAddress dstMac = ethPkt.getDestinationMAC();
            
            if (ethPkt.getEtherType() != Ethernet.TYPE_IPV4) {
                return;
            }

            /* Intra domain traffic */
            if (!dstMac.equals(virtualMac) && !dstMac.equals(quaggaMac)) {
                return;
            }

            IPv4 ipv4Pkt = (IPv4) ethPkt.getPayload();
            Ip4Address srcIp = Ip4Address.valueOf(ipv4Pkt.getSourceAddress());
            Ip4Address dstIp = Ip4Address.valueOf(ipv4Pkt.getDestinationAddress());

            log.info("srcMac: {}", srcMac.toString());
            log.info("dstMac: {}", dstMac.toString());
            log.info("srcIp: {}", srcIp.toString());
            log.info("dstIp: {}", dstIp.toString());

            ResolvedRoute route;
            Ip4Address nextHopIp = Ip4Address.ZERO;         //default, won't be used
            MacAddress nextHopMac = MacAddress.ZERO;        //default, won't be used
            
            ConnectPoint ingressPoint, egressPoint;
            IpPrefix matchDstIp = IpPrefix.IPV4_LINK_LOCAL_PREFIX;  //default, won't be used
            MacAddress newSrcMac = MacAddress.ZERO;                 //default, won't be used
            MacAddress newDstMac = MacAddress.ZERO;                 //default, won't be used
            
            /* Transit traffic */
            if (!hasBuildTransitIntents) {
                Set<FilteredConnectPoint> ingressPoints = new HashSet<FilteredConnectPoint>();
                for (Ip4Address peerIp : peersIp) {
                    ingressPoints.add(new FilteredConnectPoint(intfService.getMatchingInterface(peerIp).connectPoint()));
                }
                for (RouteTableId table : routeService.getRouteTables()) {
                    for (RouteInfo info : routeService.getRoutes(table)) {
                        hasBuildTransitIntents = true;       // marked as true when we get fpm information
                        route = info.bestRoute().get();
                        nextHopIp = route.nextHop().getIp4Address();
                        nextHopMac = route.nextHopMac();
                        log.info("nextHopIp: {}", nextHopIp.toString());
                        log.info("nextHopMac: {}", nextHopMac.toString());

                        egressPoint = intfService.getMatchingInterface(nextHopIp).connectPoint();
                        matchDstIp = info.prefix();
                        newSrcMac = quaggaMac;
                        newDstMac = nextHopMac;
                        ingressPoints.remove(new FilteredConnectPoint(egressPoint));
                        buildTransitIntent(ingressPoints, egressPoint, matchDstIp, newSrcMac, newDstMac);
                        ingressPoints.add(new FilteredConnectPoint(egressPoint));
                    }
                }
            }
            
            /* Find routes for packet-in packets */
            for (RouteTableId table : routeService.getRouteTables()) {
                for (RouteInfo info : routeService.getRoutes(table)) {
                    if (info.prefix().equals(IpPrefix.valueOf(dstIp, 24))) {
                        route = info.bestRoute().get();
                        nextHopIp = route.nextHop().getIp4Address();
                        nextHopMac = route.nextHopMac();
                        break;
                    }
                }
            }

            /* SDN-external traffic */
            if (dstMac.equals(virtualMac)) {
                ingressPoint = inCp;
                egressPoint = intfService.getMatchingInterface(nextHopIp).connectPoint();
                matchDstIp = IpPrefix.valueOf(dstIp, 24);
                newSrcMac = quaggaMac;
                newDstMac = nextHopMac;
                buildSDNExternalIntent(ingressPoint, egressPoint, matchDstIp, newSrcMac, newDstMac);
                
                ingressPoint = intfService.getMatchingInterface(nextHopIp).connectPoint();
                egressPoint = inCp;
                matchDstIp = IpPrefix.valueOf(srcIp, 24);
                newSrcMac = virtualMac;
                newDstMac = srcMac;
                buildSDNExternalIntent(ingressPoint, egressPoint, matchDstIp, newSrcMac, newDstMac);
            } 
            /* External-SDN traffic */ 
            else if (dstMac.equals(quaggaMac)) {
                for(Host host : hostService.getHostsByIp(dstIp)) {
                    log.info(host.mac().toString());

                    ingressPoint = inCp;
                    egressPoint = host.location();
                    matchDstIp = IpPrefix.valueOf(dstIp, 24);
                    newSrcMac = virtualMac;
                    newDstMac = host.mac();
                    buildSDNExternalIntent(ingressPoint, egressPoint, matchDstIp, newSrcMac, newDstMac);
                    
                    ingressPoint = host.location();
                    egressPoint = inCp;
                    matchDstIp = IpPrefix.valueOf(srcIp, 24);
                    newSrcMac = quaggaMac;
                    newDstMac = srcMac;
                    buildSDNExternalIntent(ingressPoint, egressPoint, matchDstIp, newSrcMac, newDstMac);
                }
            }
            context.block();
        }
    }

    private void buildBGPIntent(ConnectPoint ingressPoint, ConnectPoint egressPoint, IpPrefix matchDstIp) {
        
        TrafficSelector selector = DefaultTrafficSelector.builder()
            .matchIPDst(matchDstIp)
            .matchEthType(Ethernet.TYPE_IPV4)
            .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
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
        log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.", ingressPoint.deviceId(), ingressPoint.port(), egressPoint.deviceId(), egressPoint.port());
    }

    private void buildSDNExternalIntent(ConnectPoint ingressPoint, ConnectPoint egressPoint, IpPrefix matchDstIp, MacAddress newSrcMac, MacAddress newDstMac) {
        
        TrafficSelector selector = DefaultTrafficSelector.builder()
            .matchIPDst(matchDstIp)
            .matchEthType(Ethernet.TYPE_IPV4)
            .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
            .setOutput(egressPoint.port())
            .setEthSrc(newSrcMac)   // Layer 2 modification
            .setEthDst(newDstMac)   // Layer 2 modification
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
        log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.", ingressPoint.deviceId(), ingressPoint.port(), egressPoint.deviceId(), egressPoint.port());
    }

    private void buildTransitIntent(Set<FilteredConnectPoint> ingressPoints, ConnectPoint egressPoint, IpPrefix matchDstIp, MacAddress newSrcMac, MacAddress newDstMac) {
        
        TrafficSelector selector = DefaultTrafficSelector.builder()
            .matchIPDst(matchDstIp)
            .matchEthType(Ethernet.TYPE_IPV4)
            .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
            .setOutput(egressPoint.port())
            .setEthSrc(newSrcMac)   // Layer 2 modification
            .setEthDst(newDstMac)   // Layer 2 modification
            .build();

        MultiPointToSinglePointIntent intent = MultiPointToSinglePointIntent.builder()
            .appId(appId)
            .selector(selector)
            .treatment(treatment)
            .filteredIngressPoints(ingressPoints)
            .filteredEgressPoint(new FilteredConnectPoint(egressPoint))
            .priority(Intent.DEFAULT_INTENT_PRIORITY)
            .build();

        intentService.submit(intent);
        log.info("Transit intent => `{}`, port `{}` is submitted.", egressPoint.deviceId(), egressPoint.port());
    }
}