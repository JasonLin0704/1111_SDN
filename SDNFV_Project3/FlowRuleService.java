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
package nctu.winlab.bridge;

import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import java.util.Dictionary;
//import java.util.Properties;

//import static org.onlab.util.Tools.get;

//
import com.google.common.collect.Maps;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
//import org.onosproject.event.Event;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
//import org.onosproject.net.Host;
//import org.onosproject.net.HostId;
//import org.onosproject.net.Link;
//import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
//import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
//import org.onosproject.net.flow.TrafficTreatment;
//import org.onosproject.net.flow.criteria.Criterion;
//import org.onosproject.net.flow.criteria.EthCriterion;
//import org.onosproject.net.flow.instructions.Instruction;
//import org.onosproject.net.flow.instructions.Instructions;
//import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
//import org.onosproject.net.flowobjective.ForwardingObjective;
//import org.onosproject.net.host.HostService;
//import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
//import org.onosproject.net.topology.TopologyEvent;
//import org.onosproject.net.topology.TopologyListener;
//import org.onosproject.net.topology.TopologyService;
//import org.onosproject.store.service.EventuallyConsistentMap;
//import org.onosproject.store.service.MultiValuedTimestamp;
//import org.onosproject.store.service.StorageService;
//import org.onosproject.store.service.WallClockTimestamp;

//import java.util.HashMap;
//import java.util.List;
import java.util.Map;
//import java.util.Objects;
//import java.util.Set;
//import java.util.concurrent.ExecutorService;

/* Skeletal ONOS application component */
@Component(immediate = true,
           service = {SomeInterface.class}
           )

public class AppComponent implements SomeInterface {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    //@Reference(cardinality = ReferenceCardinality.MANDATORY)
    //protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    //@Reference(cardinality = ReferenceCardinality.MANDATORY)
    //protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    //@Reference(cardinality = ReferenceCardinality.MANDATORY)
    //protected StorageService storageService;

    private MyPacketProcessor processor = new MyPacketProcessor();

    private ApplicationId appId;

    protected Map<DeviceId, Map<MacAddress, PortNumber>> macAddressTables = Maps.newConcurrentMap();

    @Activate
    protected void activate() {
        // Register
        appId = coreService.registerApplication("nctu.winlab.bridge");
        packetService.addProcessor(processor, PacketProcessor.director(2));

        // Intercept packets
        requestIntercepts();

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        withdrawIntercepts();
        flowRuleService.removeFlowRulesById(appId);
        packetService.removeProcessor(processor);
        processor = null;

        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        /*
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            someProperty = get(properties, "someProperty");
        }
        */
        log.info("Reconfigured");
    }

    @Override
    public void someMethod() {
        log.info("Invoked");
    }

    /* Request packet in via packet service */
    private void requestIntercepts() {
        TrafficSelector.Builder selector;

        //ARP
        selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);

        //IPv4
        selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    /* Cancel request for packet in via packet service */
    private void withdrawIntercepts() {
        TrafficSelector.Builder selector;

        //ARP
        selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);

        //IPv4
        selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    private class MyPacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();
            ConnectPoint cp = pkt.receivedFrom();

            if (ethPkt.getEtherType() != Ethernet.TYPE_ARP && ethPkt.getEtherType() != Ethernet.TYPE_IPV4) {
                return;
            }

            /* Update the MAC address table of that switch */
            DeviceId switchId = cp.deviceId();
            macAddressTables.putIfAbsent(switchId, Maps.newConcurrentMap());
            Map<MacAddress, PortNumber> macAddressTable = macAddressTables.get(switchId);
            MacAddress srcMac = ethPkt.getSourceMAC();
            PortNumber srcPort = cp.port();
            macAddressTable.put(srcMac, srcPort);

            /* Forwarding */
            MacAddress dstMac = ethPkt.getDestinationMAC();
            PortNumber dstPort = macAddressTable.get(dstMac);
            if (dstPort == null) {
                context.treatmentBuilder().setOutput(PortNumber.FLOOD);
                context.send();
                log.info("MAC address '" + dstMac + "' is missed on '" + switchId + "'. Flood the packet.");
            } else {
                context.treatmentBuilder().setOutput(dstPort);
                context.send();
                log.info("Add an entry to the port table of '" + switchId + "'. "
                    + "MAC address: '" + dstMac + "' => Port: '" + dstPort + "'.");

                FlowRule flowRule = DefaultFlowRule.builder()
                    .withSelector(DefaultTrafficSelector.builder().matchEthSrc(srcMac).matchEthDst(dstMac).build())
                    .withTreatment(DefaultTrafficTreatment.builder().setOutput(dstPort).build())
                    .withPriority(30)
                    .forDevice(switchId)
                    .fromApp(appId)
                    .makeTemporary(30)
                    .build();

                flowRuleService.applyFlowRules(flowRule);

                log.info("MAC address '" + dstMac + "' is matched on '" + switchId + "'. Install a flow rule.");
            }
        }
    }
}
