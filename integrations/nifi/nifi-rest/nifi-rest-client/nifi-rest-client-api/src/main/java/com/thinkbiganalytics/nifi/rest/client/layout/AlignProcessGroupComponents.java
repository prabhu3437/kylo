package com.thinkbiganalytics.nifi.rest.client.layout;

import com.thinkbiganalytics.nifi.rest.client.NiFiRestClient;

import org.apache.nifi.web.api.dto.ConnectionDTO;
import org.apache.nifi.web.api.dto.PortDTO;
import org.apache.nifi.web.api.dto.PositionDTO;
import org.apache.nifi.web.api.dto.ProcessGroupDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Align Nifi Components under a supplied ProcessGroupId
 *
 * Created by sr186054 on 11/8/16.
 */
public class AlignProcessGroupComponents {

    private static final Logger log = LoggerFactory.getLogger(AlignProcessGroupComponents.class);


    NiFiRestClient niFiRestClient;

    /**
     * Map storing the various LayoutGroups computed for the ProcessGroups within the supplied {@code parentProcessGroupId}
     */
    Map<String, LayoutGroup> layoutGroups = new HashMap<>();
    /**
     * The ProcessGroup to inspect
     */
    private String parentProcessGroupId;
    /**
     * Internal counter as to the number of {@code LayoutGroup}s created
     */
    private Integer groupNumber = 0;
    /**
     * Configuration as to the height, padding for the various components
     */
    private AlignComponentsConfig alignmentConfig;

    /**
     * The Group matching the supplied {@code parentProcessGroupId}
     */
    private ProcessGroupDTO parentProcessGroup;

    /**
     * map of all the groups under the supplied parent
     */
    private Map<String, ProcessGroupDTO> processGroupDTOMap;

    /**
     * map of all the outputs under the supplied parent
     */
    private Map<String, PortDTO> outputPortMap = new HashMap<>();

    /**
     * map of all the inputs under the supplied parent
     */
    private Map<String, PortDTO> inputPortMap = new HashMap<>();

    /**
     * Pointer to the last Positioned LayoutGroup
     */
    private LayoutGroup lastPositionedGroup;

    public AlignProcessGroupComponents(NiFiRestClient niFiRestClient, String parentProcessGroupId, AlignComponentsConfig alignmentConfig) {
        this.niFiRestClient = niFiRestClient;
        this.parentProcessGroupId = parentProcessGroupId;
        this.alignmentConfig = alignmentConfig;
    }

    public AlignProcessGroupComponents(NiFiRestClient niFiRestClient, String parentProcessGroupId) {
        this(niFiRestClient, parentProcessGroupId, new AlignComponentsConfig());
    }

    /**
     * For the passed in {@code parentProcessGroupId} it will group the items and then apply different
     */
    public ProcessGroupDTO autoLayout() {
        try {
            //find the parent and children
            if (parentProcessGroupId == "root") {
                parentProcessGroup = niFiRestClient.processGroups().findRoot();
            } else {
                parentProcessGroup = niFiRestClient.processGroups().findById(parentProcessGroupId, false, true).orElse(null);
            }
            final Set<ProcessGroupDTO> children = parentProcessGroup.getContents().getProcessGroups();
            processGroupDTOMap = new HashMap<>();
            children.stream().forEach(group -> processGroupDTOMap.put(group.getId(), group));

            parentProcessGroup.getContents().getOutputPorts().stream().forEach(portDTO -> outputPortMap.put(portDTO.getId(), portDTO));

            parentProcessGroup.getContents().getInputPorts().stream().forEach(portDTO -> inputPortMap.put(portDTO.getId(), portDTO));

            //map any ports to processgroups
            //group the items by their respective output ports
            createLayoutGroups();
            //organize each group of items on the screen
            layoutGroups.entrySet().stream().sorted(Map.Entry.<String, LayoutGroup>comparingByKey()).forEachOrdered(entry -> arrangeProcessGroup(entry.getValue()));
        } catch (Exception e) {
            log.error("Error Aligning items in Process Group {}. {}", parentProcessGroupId, e.getMessage());
        }
        return parentProcessGroup;
    }

    /**
     * Based upon the LayoutGroup apply the correct Rendering technique to layout the components
     */
    private void arrangeProcessGroup(LayoutGroup layoutGroup) {

        log.info("Arrange Group {}", layoutGroup.getClass().getSimpleName());
        //set the starting Y coords for this group
        Double start = lastPositionedGroup == null ? 0.0d : lastPositionedGroup.getBottomY() + alignmentConfig.getGroupPadding();
        layoutGroup.setTopAndBottom(start, new Double(layoutGroup.getHeight() + start));
        layoutGroup.setGroupNumber(groupNumber++);

        if (layoutGroup instanceof InputPortToProcessGroup) {
            arrangeInputPortToProcessGroupLayout((InputPortToProcessGroup) layoutGroup);
        } else if (layoutGroup instanceof ProcessGroupToOutputPort) {
            arrangeProcessGroupToOutputPortLayout((ProcessGroupToOutputPort) layoutGroup);
        } else if (layoutGroup instanceof ProcessGroupToProcessGroup) {
            arrangeProcessGroupLayout((ProcessGroupToProcessGroup) layoutGroup);
        } else if (layoutGroup instanceof ProcessGroupWithoutConnections) {
            arrangeProcessGroupWithoutConnectionsLayout((ProcessGroupWithoutConnections) layoutGroup);
        }

        lastPositionedGroup = layoutGroup;

    }

    private void arrangeProcessGroupWithoutConnectionsLayout(ProcessGroupWithoutConnections layout) {
        defaultProcessGroupLayoutArrangement(layout);
    }

    /**
     * Arrange top and bottom
     */
    private void arrangeProcessGroupLayout(ProcessGroupToProcessGroup layout) {

        //ClockRenderer clock = new ClockRenderer(layoutGroups,alignmentConfig);
        //arrange the dest in the center
        TopBottomRowsRenderer topBottomRowsRenderer = new TopBottomRowsRenderer(layout, alignmentConfig);

        SingleRowRenderer rowRenderer = new SingleRowRenderer(layout, alignmentConfig, layout.getMiddleY());
        alignProcessGroups(layout.getDestinations(), rowRenderer);

        alignProcessGroups(layout.getProcessGroupDTOs(), topBottomRowsRenderer);

    }

    private void arrangeProcessGroupToOutputPortLayout(ProcessGroupToOutputPort layout) {

        if (!layout.getPorts().isEmpty()) {
            Integer outputPortCount = layout.getPorts().size();

            ColumnRenderer columnRenderer = new ColumnRenderer(layout, alignmentConfig, (alignmentConfig.getCenterX() - (alignmentConfig.getPortWidth() / 2)), outputPortCount);
            columnRenderer
                .updateHeight((columnRenderer.getItemCount() * alignmentConfig.getPortHeight() + alignmentConfig.getProcessGroupHeight() + (2 * alignmentConfig.getProcessGroupPaddingTopBottom())));
            alignOutputPorts(layout, columnRenderer);
        }
        SingleRowRenderer rowRenderer = new SingleRowRenderer(layout, alignmentConfig, layout.getMiddleY(alignmentConfig.getProcessGroupHeight() / 2));
        alignProcessGroups(layout.getProcessGroupDTOs(), rowRenderer);

    }

    private void arrangeInputPortToProcessGroupLayout(InputPortToProcessGroup layout) {

        if (!layout.getPorts().isEmpty()) {
            Integer outputPortCount = layout.getPorts().size();

            ColumnRenderer columnRenderer = new ColumnRenderer(layout, alignmentConfig, (alignmentConfig.getCenterX() - (alignmentConfig.getPortWidth() / 2)), outputPortCount);
            columnRenderer
                .updateHeight((columnRenderer.getItemCount() * alignmentConfig.getPortHeight() + alignmentConfig.getProcessGroupHeight() + (2 * alignmentConfig.getProcessGroupPaddingTopBottom())));
            alignInputPorts(layout, columnRenderer);
        }
        SingleRowRenderer rowRenderer = new SingleRowRenderer(layout, alignmentConfig, layout.getMiddleY(alignmentConfig.getProcessGroupHeight() / 2));
        alignProcessGroups(layout.getProcessGroupDTOs(), rowRenderer);

    }


    private void alignOutputPorts(ProcessGroupToOutputPort layoutGroup, AbstractRenderer renderer) {
        layoutGroup.getPorts().values().stream().forEach(port -> {
            PortDTO positionPort = new PortDTO();
            positionPort.setId(port.getId());
            PositionDTO lastPosition = renderer.getLastPosition();
            PositionDTO newPosition = renderer.getNextPosition(lastPosition);
            positionPort.setPosition(newPosition);
            niFiRestClient.ports().updateOutputPort(parentProcessGroupId, positionPort);
            log.info("Aligned Port {} at {},{}", port.getName(), positionPort.getPosition().getX(), positionPort.getPosition().getY());
        });
    }

    private void alignInputPorts(InputPortToProcessGroup layoutGroup, AbstractRenderer renderer) {
        layoutGroup.getPorts().values().stream().forEach(port -> {
            PortDTO positionPort = new PortDTO();
            positionPort.setId(port.getId());
            PositionDTO lastPosition = renderer.getLastPosition();
            PositionDTO newPosition = renderer.getNextPosition(lastPosition);
            positionPort.setPosition(newPosition);
            niFiRestClient.ports().updateInputPort(parentProcessGroupId, positionPort);
            log.info("Aligned Port {} at {},{}", port.getName(), positionPort.getPosition().getX(), positionPort.getPosition().getY());
        });
    }


    private void alignProcessGroups(Set<ProcessGroupDTO> processGroups, AbstractRenderer renderer) {
        processGroups.stream().forEach(processGroupDTO -> {
            ProcessGroupDTO positionProcessGroup = new ProcessGroupDTO();
            positionProcessGroup.setId(processGroupDTO.getId());
            PositionDTO lastPosition = renderer.getLastPosition();
            PositionDTO newPosition = renderer.getNextPosition(lastPosition);
            positionProcessGroup.setPosition(newPosition);
            niFiRestClient.processGroups().update(positionProcessGroup);
            log.info("Aligned ProcessGroup {} at {},{}", processGroupDTO.getName(), positionProcessGroup.getPosition().getX(), positionProcessGroup.getPosition().getY());
        });
    }


    private void defaultProcessGroupLayoutArrangement(LayoutGroup layoutGroup) {
        SingleRowRenderer rowRenderer = new SingleRowRenderer(layoutGroup, alignmentConfig, layoutGroup.getMiddleY(alignmentConfig.getProcessGroupHeight() / 2));
        alignProcessGroups(layoutGroup.getProcessGroupDTOs(), rowRenderer);
    }


    private boolean isGroupToGroupConnection(ConnectionDTO connectionDTO) {
        return (processGroupDTOMap.containsKey(connectionDTO.getDestination().getGroupId()) && processGroupDTOMap.containsKey(connectionDTO.getSource().getGroupId()));
    }

    private boolean isOutputPortToGroupConnection(ConnectionDTO connectionDTO) {
        return (outputPortMap.containsKey(connectionDTO.getDestination().getId()) || outputPortMap.containsKey(connectionDTO.getSource().getId()))
               && (processGroupDTOMap.containsKey(connectionDTO.getDestination().getGroupId()) || processGroupDTOMap.containsKey(connectionDTO.getSource().getGroupId()));
    }

    private boolean isInputPortToGroupConnection(ConnectionDTO connectionDTO) {
        return (inputPortMap.containsKey(connectionDTO.getDestination().getId()) || inputPortMap.containsKey(connectionDTO.getSource().getId()))
               && (processGroupDTOMap.containsKey(connectionDTO.getDestination().getGroupId()) || processGroupDTOMap.containsKey(connectionDTO.getSource().getGroupId()));
    }

    /**
     * Group the items together to create the various LayoutGroups needed for different Rendering
     */
    private void createLayoutGroups() {
        Map<String, Set<ProcessGroupDTO>> outputPortIdToGroup = new HashMap<String, Set<ProcessGroupDTO>>();
        Map<String, Set<PortDTO>> groupIdToOutputPorts = new HashMap<>();

        Map<String, Set<ProcessGroupDTO>> inputPortIdToGroup = new HashMap<String, Set<ProcessGroupDTO>>();
        Map<String, Set<PortDTO>> groupIdToInputPorts = new HashMap<>();

        Map<String, Set<String>> groupIdToGroup = new HashMap<>();

        parentProcessGroup.getContents().getConnections().stream().filter(
            connectionDTO -> (isOutputPortToGroupConnection(connectionDTO) || isGroupToGroupConnection(connectionDTO) || isInputPortToGroupConnection(connectionDTO)))
            .forEach(connectionDTO -> {
                PortDTO
                    outputPort =
                    outputPortMap.get(connectionDTO.getDestination().getId()) == null ? outputPortMap.get(connectionDTO.getSource().getId())
                                                                                      : outputPortMap.get(connectionDTO.getDestination().getId());

                PortDTO
                    inputPort =
                    inputPortMap.get(connectionDTO.getSource().getId()) == null ? inputPortMap.get(connectionDTO.getDestination().getId())
                                                                                : inputPortMap.get(connectionDTO.getSource().getId());

                ProcessGroupDTO
                    destinationGroup =
                    processGroupDTOMap.get(connectionDTO.getDestination().getGroupId());

                ProcessGroupDTO
                    sourceGroup =
                    processGroupDTOMap.get(connectionDTO.getSource().getGroupId());

                if (outputPort != null) {
                    ProcessGroupDTO processGroup = destinationGroup == null ? sourceGroup : destinationGroup;
                    outputPortIdToGroup.computeIfAbsent(outputPort.getId(), (key) -> new HashSet<ProcessGroupDTO>()).add(processGroup);
                    groupIdToOutputPorts.computeIfAbsent(processGroup.getId(), (key) -> new HashSet<PortDTO>()).add(outputPort);
                }
                if (inputPort != null) {
                    ProcessGroupDTO processGroup = destinationGroup == null ? sourceGroup : destinationGroup;
                    inputPortIdToGroup.computeIfAbsent(inputPort.getId(), (key) -> new HashSet<ProcessGroupDTO>()).add(processGroup);
                    groupIdToInputPorts.computeIfAbsent(processGroup.getId(), (key) -> new HashSet<PortDTO>()).add(inputPort);
                } else if (destinationGroup != null && sourceGroup != null) {
                    groupIdToGroup.computeIfAbsent(sourceGroup.getId(), (key) -> new HashSet<String>()).add(destinationGroup.getId());
                }

            });

        // group port connections together
        groupIdToOutputPorts.entrySet().stream().forEach(entry -> {
            String processGroupId = entry.getKey();
            String portKey = entry.getValue().stream().map(portDTO -> portDTO.getId()).sorted().collect(Collectors.joining(","));
            portKey = "AAA" + portKey;
            layoutGroups.computeIfAbsent(portKey, (key) -> new ProcessGroupToOutputPort(entry.getValue())).add(processGroupDTOMap.get(processGroupId));
        });

        // group port connections together
        groupIdToInputPorts.entrySet().stream().forEach(entry -> {
            String processGroupId = entry.getKey();
            String portKey = entry.getValue().stream().map(portDTO -> portDTO.getId()).sorted().collect(Collectors.joining(","));
            portKey = "BBB" + portKey;
            layoutGroups.computeIfAbsent(portKey, (key) -> new InputPortToProcessGroup(entry.getValue())).add(processGroupDTOMap.get(processGroupId));
        });

        groupIdToGroup.entrySet().stream().forEach(entry -> {
            String sourceGroupId = entry.getKey();
            String processGroupKey = entry.getValue().stream().sorted().collect(Collectors.joining(","));
            processGroupKey = "CCC" + processGroupKey;

            layoutGroups.computeIfAbsent(processGroupKey, (key) -> new ProcessGroupToProcessGroup(entry.getValue())).add(processGroupDTOMap.get(entry.getKey()));
        });

        //add in any groups that dont have connections to ports
        processGroupDTOMap.values().stream().filter(
            processGroupDTO -> !groupIdToGroup.values().stream().flatMap(set -> set.stream()).collect(Collectors.toSet()).contains(processGroupDTO.getId()) && !groupIdToInputPorts
                .containsKey(processGroupDTO.getId()) && !groupIdToOutputPorts.containsKey(processGroupDTO.getId()) && !groupIdToGroup.containsKey(processGroupDTO.getId()))
            .forEach(group -> {
                layoutGroups.computeIfAbsent("NO_PORTS", (key) -> new ProcessGroupWithoutConnections()).add(group);
            });

    }


    public void setNiFiRestClient(NiFiRestClient niFiRestClient) {
        this.niFiRestClient = niFiRestClient;
    }


    /**
     * Layout where a ProcessGroup has no Connections
     */
    public class ProcessGroupWithoutConnections extends LayoutGroup {

        public ProcessGroupWithoutConnections() {

        }

        public Integer calculateHeight() {
            return (alignmentConfig.getProcessGroupHeight()) + (alignmentConfig.getProcessGroupPaddingTopBottom());
        }

    }

    /**
     * Layout Group where a ProcessGroup is connected directly to another ProcessGroup
     */
    public class ProcessGroupToProcessGroup extends LayoutGroup {

        private Set<ProcessGroupDTO> destinations = new HashSet<>();


        public ProcessGroupToProcessGroup(Set<String> destinations) {
            this.destinations = destinations.stream().map(groupId -> processGroupDTOMap.get(groupId)).collect(Collectors.toSet());
        }


        public Set<ProcessGroupDTO> getSources() {
            return super.getProcessGroupDTOs();
        }

        @Override
        public Integer calculateHeight() {
            return alignmentConfig.getProcessGroupHeight() + alignmentConfig.getProcessGroupPaddingTopBottom();
        }

        public Set<ProcessGroupDTO> getDestinations() {
            return destinations;
        }
    }


    /**
     * Layout Group where an Input Port is connected to a ProcessGroup
     */
    public class InputPortToProcessGroup extends ProcessGroupToOutputPort {

        public InputPortToProcessGroup(Set<PortDTO> inputPorts) {
            super(inputPorts);
        }
    }

    /**
     * Group where a ProcessGroup is connected to an OutputPort
     */
    public class ProcessGroupToOutputPort extends ProcessGroupToPort {

        public ProcessGroupToOutputPort(Set<PortDTO> outputPorts) {
            super(outputPorts);
        }
    }

    /**
     * LayoutGroup where a Port is connected to a Process Group
     */
    public abstract class ProcessGroupToPort extends LayoutGroup {

        private Map<String, PortDTO> ports = new HashMap<>();


        public ProcessGroupToPort(Set<PortDTO> ports) {
            if (ports != null) {
                ports.stream().forEach(portDTO -> {
                    this.ports.put(portDTO.getId(), portDTO);
                });
            }
        }


        public Integer calculateHeight() {
            Integer portCount = ports.size();
            return (alignmentConfig.getPortHeight() * portCount) + alignmentConfig.getProcessGroupHeight() + (alignmentConfig.getProcessGroupPaddingTopBottom() * portCount);
        }

        public Double getMiddleY() {
            return super.getMiddleY();
        }


        public Map<String, PortDTO> getPorts() {
            return ports;
        }


    }


}