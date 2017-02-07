package org.ow2.proactive.resourcemanager.core;

import static com.google.common.truth.Truth.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.security.Permission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.objectweb.proactive.core.node.Node;
import org.objectweb.proactive.core.node.NodeInformation;
import org.objectweb.proactive.core.util.wrapper.BooleanWrapper;
import org.ow2.proactive.resourcemanager.authentication.Client;
import org.ow2.proactive.resourcemanager.common.NodeState;
import org.ow2.proactive.resourcemanager.common.RMState;
import org.ow2.proactive.resourcemanager.core.properties.PAResourceManagerProperties;
import org.ow2.proactive.resourcemanager.db.RMDBManager;
import org.ow2.proactive.resourcemanager.exception.AddingNodesException;
import org.ow2.proactive.resourcemanager.frontend.RMMonitoringImpl;
import org.ow2.proactive.resourcemanager.nodesource.NodeSource;
import org.ow2.proactive.resourcemanager.rmnode.RMDeployingNode;
import org.ow2.proactive.resourcemanager.rmnode.RMNode;
import org.ow2.proactive.resourcemanager.rmnode.RMNodeHelper;
import org.ow2.proactive.resourcemanager.rmnode.RMNodeImpl;
import org.ow2.proactive.resourcemanager.selection.SelectionManager;
import org.ow2.proactive.topology.descriptor.TopologyDescriptor;
import org.ow2.proactive.utils.Criteria;
import org.ow2.proactive.utils.NodeSet;

import com.google.common.base.Function;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;


public class RMCoreTest {

    private RMCore rmCore;

    @Mock
    private Client mockedCaller;

    @Mock
    private NodeSource mockedNodeSource;

    @Mock
    private RMMonitoringImpl mockedMonitoring;

    @Mock
    private SelectionManager mockedSelectionManager;

    @Mock
    private RMDBManager dataBaseManager;

    @Mock
    private RMNode mockedRemovableNodeInDeploy;

    @Mock
    private RMNode mockedUnremovableNodeInDeploy;

    @Mock
    private RMNode mockedRemovableNode;

    @Mock
    private RMNode mockedUnremovableNode;

    @Mock
    private RMNode mockedBusyNode;

    @Mock
    private RMNode mockedFreeButLockedNode;

    private NodesLockRestorationManager nodesLockRestorationManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        populateRMCore();
    }

    /**
     * setUp() adds 6 nodes 3 free and 3 busy and 3 down.
     * maximumNumberOfNodes is null; limit is not enforced
     */
    @Test
    public void testThatUnlimitedNumberOfNodesWithSetToNull() throws NoSuchFieldException, IllegalAccessException {
        setMaxNumberOfNodesTo(null);
        rmCore.internalAddNodeToCore(mockedRemovableNode);
        assertThat(getNumberOfFreeNodes(), is(3));
    }

    /**
     * setUp() adds 6 nodes 3 free and 3 down.
     * maximumNumberOfNodes is null; limit is not enforced
     */
    @Test
    public void testThatUnlimitedNumberOfNodesWithSetToMinus1() throws NoSuchFieldException, IllegalAccessException {
        setMaxNumberOfNodesTo(-1L);
        rmCore.internalAddNodeToCore(mockedRemovableNode);
        assertThat(getNumberOfFreeNodes(), is(3));
    }

    /**
     * setUp() adds 6 nodes 3 free and 3 down.
     * maximumNumberOfNodes is 0; limit is enforced and exception is thrown
     */
    @Test(expected = org.ow2.proactive.resourcemanager.exception.AddingNodesException.class)
    public void testThatExceptionIsThrownIfSetTo0() throws NoSuchFieldException, IllegalAccessException {
        setMaxNumberOfNodesTo(0L);
        String nodeUrl = mockedRemovableNode.getNodeName();
        when(mockedRemovableNode.getNodeURL()).thenReturn(nodeUrl);
        when(mockedRemovableNode.getProvider()).thenReturn(new Client());
        rmCore.internalAddNodeToCore(mockedRemovableNode);
    }

    /**
     * setUp() adds 6 nodes 3 free and 3 down.
     * maximumNumberOfNodes is 1; limit is enforced and exception is thrown
     */
    @Test(expected = org.ow2.proactive.resourcemanager.exception.AddingNodesException.class)
    public void testThatExceptionIsThrownIfSetTo1() throws NoSuchFieldException, IllegalAccessException {
        setMaxNumberOfNodesTo(1L);
        String nodeUrl = mockedRemovableNode.getNodeName();
        when(mockedRemovableNode.getNodeURL()).thenReturn(nodeUrl);
        when(mockedRemovableNode.getProvider()).thenReturn(new Client());
        rmCore.internalAddNodeToCore(mockedRemovableNode);
    }

    /**
     * setUp() adds 6 nodes 3 free and 3 down.
     * maximumNumberOfNodes is 2; limit is not reached.
     * The limit is not reached because the 2 free nodes include the asking node.
     */
    @Test
    public void testThatNodeIsAddedIfSetTo3() throws NoSuchFieldException, IllegalAccessException {
        setMaxNumberOfNodesTo(3L);
        String nodeUrl = mockedRemovableNode.getNodeName();
        when(mockedRemovableNode.getNodeURL()).thenReturn(nodeUrl);
        when(mockedRemovableNode.getProvider()).thenReturn(new Client());
        rmCore.internalAddNodeToCore(mockedRemovableNode);
        assertThat(getNumberOfFreeNodes(), is(3));
    }

    /**
     * setUp() adds 6 nodes 3 free and 3 down.
     * maximumNumberOfNodes is 6; limit is not reached.
     * Previously down node is added and set to free; +1 free node
     */
    @Test
    public void testThatAddedNodeIsSetAsFree() throws NoSuchFieldException, IllegalAccessException {
        setMaxNumberOfNodesTo(3L);
        String nodeUrl = mockedUnremovableNodeInDeploy.getNodeName();
        when(mockedUnremovableNodeInDeploy.getNodeURL()).thenReturn(nodeUrl);
        when(mockedUnremovableNodeInDeploy.getProvider()).thenReturn(new Client());

        assertThat(getNumberOfFreeNodes(), is(3));
        rmCore.internalAddNodeToCore(mockedUnremovableNodeInDeploy);
        assertThat(getNumberOfFreeNodes(), is(4));
    }

    /**
     * Set the private value maximumNumberOfNodes (Long) to a certain value.
     * @param newValue
     */
    private void setMaxNumberOfNodesTo(Long newValue) throws NoSuchFieldException, IllegalAccessException {
        Field maxNumberOfNodesField = RMCore.class.getDeclaredField("maximumNumberOfNodes");
        maxNumberOfNodesField.setAccessible(true);
        maxNumberOfNodesField.set(rmCore, newValue);
        maxNumberOfNodesField.setAccessible(false);
    }

    /*
     * Non-existing URL (non-deployment)
     * Non preemptive
     */
    @Test
    public void testRemoveNode1() throws Throwable {
        boolean result = rmCore.removeNode("NON_EXISTING_URL_NON_DEPLOYMENT", false).getBooleanValue();
        assertEquals(false, result);
    }

    /*
     * Non-existing URL (non-deployment)
     * Preemptive
     */
    @Test
    public void testRemoveNode2() {
        boolean result = rmCore.removeNode("NON_EXISTING_URL_NON_DEPLOYMENT", true).getBooleanValue();
        assertEquals(false, result);
    }

    /*
     * existing URL (non-deployment)
     * Non preemptive
     */
    @Test
    public void testRemoveNode3() {

        boolean result = rmCore.removeNode("mockedRemovableNode", false).getBooleanValue();
        assertEquals(true, result);
    }

    /*
     * existing URL (non-deployment)
     * Preemptive
     */
    @Test
    public void testRemoveNode4() {

        boolean result = rmCore.removeNode("mockedRemovableNode", true).getBooleanValue();
        assertEquals(true, result);
    }

    /*
     * Non-existing URL (deployment)
     * Non preemptive
     */
    @Test
    public void testRemoveNode5() {

        boolean result = rmCore.removeNode(RMDeployingNode.PROTOCOL_ID + "://NON_EXISTING_URL_NON_DEPLOYMENT", false)
                               .getBooleanValue();
        assertEquals(false, result);
    }

    /*
     * Non-existing URL (deployment)
     * Preemptive
     */
    @Test
    public void testRemoveNode6() {

        boolean result = rmCore.removeNode(RMDeployingNode.PROTOCOL_ID + "://NON_EXISTING_URL_NON_DEPLOYMENT", true)
                               .getBooleanValue();
        assertEquals(false, result);
    }

    /*
     * existing URL (deployment)
     * Non preemptive
     */
    @Test
    public void testRemoveNode7() {

        boolean result = rmCore.removeNode(RMDeployingNode.PROTOCOL_ID + "://removableNode", false).getBooleanValue();
        assertEquals(false, result);
    }

    /*
     * existing URL (deployment)
     * Preemptive
     */
    @Test
    public void testRemoveNode8() {

        boolean result = rmCore.removeNode(RMDeployingNode.PROTOCOL_ID + "://removableNode", true).getBooleanValue();
        assertEquals(false, result);
    }

    @Test
    public void testReleaseNode1() {
        boolean result = rmCore.releaseNode(mockedRemovableNode.getNode()).getBooleanValue();
        assertEquals(true, result);
    }

    @Test
    public void testReleaseNode2() {
        boolean result = rmCore.releaseNode(mockedUnremovableNode.getNode()).getBooleanValue();
        assertEquals(true, result);
    }

    @Test
    public void testReleaseNode3() {
        boolean result = rmCore.releaseNode(mockedRemovableNodeInDeploy.getNode()).getBooleanValue();
        assertEquals(true, result);
    }

    @Test
    public void testReleaseNode4() {
        boolean result = rmCore.releaseNode(mockedRemovableNodeInDeploy.getNode()).getBooleanValue();
        assertEquals(true, result);
    }

    @Test
    public void testGetNodes() {
        NodeSet nodeSet = rmCore.getNodes(1, TopologyDescriptor.ARBITRARY, null, null, false);
        assertEquals(0, nodeSet.size()); // we don't check nodeSet as its content is also tested in SelectionManagerTest
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetNodesBadNodesNumber() {
        rmCore.getNodes(-1, TopologyDescriptor.ARBITRARY, null, null, false);
    }

    @Test
    public void testNodesRestorationManagerHandleInSetDownNode() {
        verify(nodesLockRestorationManager, never()).handle(Mockito.any(RMNode.class));
        rmCore.setDownNode(mockedBusyNode.getNodeURL());
        verify(nodesLockRestorationManager).handle(Mockito.any(RMNode.class));
    }

    @Test
    public void testNodesRestorationManagerHandleInSetLost() {
        verify(nodesLockRestorationManager, never()).handle(Mockito.any(RMNode.class));
        rmCore.setLost(mockedBusyNode);
        verify(nodesLockRestorationManager).handle(Mockito.any(RMNode.class));
    }

    @Test
    public void testNodesRestorationManagerHandleInInternalSetFree() {
        verify(nodesLockRestorationManager, never()).handle(Mockito.any(RMNode.class));
        rmCore.internalSetFree(mockedBusyNode);
        verify(nodesLockRestorationManager).handle(Mockito.any(RMNode.class));
    }

    @Test
    public void testGetNodeByUrlIncludingDeployingNodesKnownUrlDeployingNode() {
        RMNode rmNodeFound = rmCore.getNodeByUrlIncludingDeployingNodes(mockedBusyNode.getNodeURL());
        assertThat(rmNodeFound).isSameAs(mockedBusyNode);
    }

    @Test
    public void testGetNodeByUrlIncludingDeployingNodesKnownUrlNonDeployingNode() {
        RMNode rmNodeFound = rmCore.getNodeByUrlIncludingDeployingNodes(mockedRemovableNode.getNodeURL());
        assertThat(rmNodeFound).isSameAs(mockedRemovableNode);
    }

    @Test
    public void testGetNodeByUrlIncludingDeployingNodesUnknownNodeUrl() {
        RMDeployingNode rmNode = new RMDeployingNode("node", mockedNodeSource, "command", new Client());

        doReturn(rmNode).when(mockedNodeSource).getDeployingNode(rmNode.getNodeURL());

        RMNode rmNodeFound = rmCore.getNodeByUrlIncludingDeployingNodes(rmNode.getNodeURL());
        assertThat(rmNodeFound).isSameAs(rmNode);
    }

    @Test
    public void testGetAtMostNodes() {
        NodeSet nodeSet = rmCore.getAtMostNodes(1, TopologyDescriptor.ARBITRARY, null, null);
        assertEquals(0, nodeSet.size()); // we don't check nodeSet as its content is also tested in SelectionManagerTest
    }

    @Test
    public void testLockNodes1() {
        int numberOfNodesEligibleForSchedulingBeforeLocking = getNumberOfFreeNodes();

        Set<String> nodesToLock = ImmutableSet.of(mockedRemovableNode.getNodeURL());
        boolean result = rmCore.lockNodes(nodesToLock).getBooleanValue();

        assertThat(result).isTrue();
        assertThat(getNumberOfFreeNodes()).isEqualTo(numberOfNodesEligibleForSchedulingBeforeLocking - 1);
        verify(mockedRemovableNode).lock(Mockito.any(Client.class));
    }

    @Test
    public void testLockNodes2() {
        int numberOfNodesEligibleForSchedulingBeforeLocking = getNumberOfFreeNodes();

        Set<String> nodesToLock = ImmutableSet.of(mockedRemovableNode.getNodeURL(),
                                                  mockedRemovableNodeInDeploy.getNodeURL());

        boolean result = rmCore.lockNodes(nodesToLock).getBooleanValue();

        assertThat(result).isTrue();
        assertThat(getNumberOfFreeNodes()).isEqualTo(numberOfNodesEligibleForSchedulingBeforeLocking - 2);
        verify(mockedRemovableNode).lock(Mockito.any(Client.class));
        verify(mockedRemovableNodeInDeploy).lock(Mockito.any(Client.class));
    }

    @Test
    public void testLockNodes3() {
        int numberOfNodesEligibleForSchedulingBeforeLocking = getNumberOfFreeNodes();

        Set<String> nodesToLock = ImmutableSet.of(mockedUnremovableNode.getNodeURL());

        boolean result = rmCore.lockNodes(nodesToLock).getBooleanValue();

        assertThat(result).isTrue();
        assertThat(getNumberOfFreeNodes()).isEqualTo(numberOfNodesEligibleForSchedulingBeforeLocking);
        verify(mockedUnremovableNode).lock(Mockito.any(Client.class));
    }

    @Test
    public void testLockNodes4() {
        int numberOfNodesEligibleForSchedulingBeforeLocking = getNumberOfFreeNodes();

        Set<String> nodesToLock = ImmutableSet.of(mockedBusyNode.getNodeURL(), mockedRemovableNode.getNodeURL());

        boolean result = rmCore.lockNodes(nodesToLock).getBooleanValue();

        assertThat(result).isFalse();
        assertThat(getNumberOfFreeNodes()).isEqualTo(numberOfNodesEligibleForSchedulingBeforeLocking - 1);

        verify(mockedBusyNode, never()).lock(Mockito.any(Client.class));
        verify(mockedRemovableNode).lock(Mockito.any(Client.class));
    }

    @Test
    public void testUnlockNodes1() {
        int numberOfNodesEligibleForSchedulingBeforeLocking = getNumberOfFreeNodes();

        Set<String> nodesToUnlock = ImmutableSet.of(mockedBusyNode.getNodeURL());

        boolean result = rmCore.unlockNodes(nodesToUnlock).getBooleanValue();

        assertThat(result).isTrue();
        assertThat(getNumberOfFreeNodes()).isEqualTo(numberOfNodesEligibleForSchedulingBeforeLocking);

        verify(mockedBusyNode).unlock(Mockito.any(Client.class));
    }

    @Test
    public void testUnlockNodes2() {
        int numberOfNodesEligibleForSchedulingBeforeLocking = getNumberOfFreeNodes();

        Set<String> nodesToUnlock = ImmutableSet.of(mockedRemovableNode.getNodeURL());

        boolean result = rmCore.unlockNodes(nodesToUnlock).getBooleanValue();

        assertThat(result).isFalse();
        assertThat(getNumberOfFreeNodes()).isEqualTo(numberOfNodesEligibleForSchedulingBeforeLocking);

        verify(mockedBusyNode, never()).unlock(Mockito.any(Client.class));
    }

    @Test
    public void testUnlockNodes3() {
        int numberOfNodesEligibleForSchedulingBeforeLocking = getNumberOfFreeNodes();

        Set<String> nodesToUnlock = ImmutableSet.of(mockedRemovableNode.getNodeURL(), mockedBusyNode.getNodeURL());

        boolean result = rmCore.unlockNodes(nodesToUnlock).getBooleanValue();

        assertThat(result).isFalse();
        assertThat(getNumberOfFreeNodes()).isEqualTo(numberOfNodesEligibleForSchedulingBeforeLocking);

        verify(mockedRemovableNode, never()).unlock(Mockito.any(Client.class));
        verify(mockedBusyNode).unlock(Mockito.any(Client.class));
    }

    @Test
    public void testUnlockNodes4() {
        int numberOfNodesEligibleForSchedulingBeforeLocking = getNumberOfFreeNodes();

        Set<String> nodesToUnlock = ImmutableSet.of(mockedRemovableNode.getNodeURL(),
                                                    mockedFreeButLockedNode.getNodeURL());

        boolean result = rmCore.unlockNodes(nodesToUnlock).getBooleanValue();

        assertThat(result).isFalse();
        assertThat(getNumberOfFreeNodes()).isEqualTo(numberOfNodesEligibleForSchedulingBeforeLocking + 1);

        verify(mockedRemovableNode, never()).unlock(Mockito.any(Client.class));
        verify(mockedFreeButLockedNode).unlock(Mockito.any(Client.class));
    }

    /**
     * New node to existing nodesource
     */
    @Test
    public void testAddNodeNewNodeExistingNodeSource() {
        boolean result = rmCore.addNode("NODE-testAddNodeNewNodeExistingNodeSource", mockedNodeSource.getName())
                               .getBooleanValue();
        assertEquals(true, result);
    }

    /**
     * Existing node to existing nodesource
     */
    @Test
    public void testAddNodeExistingNodeExistingNodeSource() {
        boolean result = rmCore.addNode(mockedRemovableNode.getNodeName(), mockedNodeSource.getName())
                               .getBooleanValue();
        assertEquals(true, result);
    }

    /**
     * Existing node to new nodesource
     */
    @Test(expected = AddingNodesException.class)
    public void testAddNodeExistingNodeNewNodeSource() {
        rmCore.addNode(mockedRemovableNode.getNodeName(), "NEW-NODESOURCE-testAddNodeNewNodeNewNodeSource")
              .getBooleanValue();
    }

    /**
     * New node to new nodesource
     */
    @Test(expected = AddingNodesException.class)
    public void testAddNodeNewNodeNewNodeSource() {
        rmCore.addNode("NODE-testAddNodeNewNodeNewNodeSource", "NEW-NODESOURCE-testAddNodeNewNodeNewNodeSource")
              .getBooleanValue();
    }

    @Test
    public void testRemoveNodeSourceExistingNodeSourceNoPreempt() {
        boolean result = rmCore.removeNodeSource(mockedNodeSource.getName(), false).getBooleanValue();
        assertEquals(true, result);
    }

    @Test
    public void testRemoveNodeSourceExistingNodeSourcePreempt() {
        boolean result = rmCore.removeNodeSource(mockedNodeSource.getName(), true).getBooleanValue();
        assertEquals(true, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRemoveNodeSourceNonExistingNodeSource() {
        rmCore.removeNodeSource("NON-EXISTING-NODESOURCE", true).getBooleanValue();
    }

    @Test
    public void testGetState() {
        RMState rmState = rmCore.getState();
        assertEquals(6, rmState.getTotalNodesNumber());
        assertEquals(3, rmState.getFreeNodesNumber());
        // there are 6 nodes, 3 are down, as a consequence 3 are expected as alive
        assertEquals(3, rmState.getTotalAliveNodesNumber());
    }

    @Test
    public void testFreeNodeWithNodeThatIsAlreadyFree() {
        int numberOfNodeEligibleForSchedulingBeforeFreeing = getNumberOfFreeNodes();
        assertThat(rmCore.setFreeNodes(ImmutableList.of(mockedRemovableNode)).getBooleanValue()).isTrue();
        assertThat(getNumberOfFreeNodes()).isEqualTo(numberOfNodeEligibleForSchedulingBeforeFreeing);
        verify(mockedRemovableNode, never()).setFree();
    }

    private int getNumberOfFreeNodes() {
        return rmCore.getFreeNodes().size();
    }

    @Test
    public void testFreeNodeWithNodeThatIsNotFreeAndNotLocked() {
        int numberOfNodeEligibleForSchedulingBeforeFreeing = getNumberOfFreeNodes();
        assertThat(rmCore.setFreeNodes(ImmutableList.of(mockedUnremovableNode)).getBooleanValue()).isTrue();
        assertThat(getNumberOfFreeNodes()).isEqualTo(numberOfNodeEligibleForSchedulingBeforeFreeing + 1);
        verify(mockedUnremovableNode).setFree();
    }

    @Test
    public void testFreeNodeWithNodeThatIsNotFreeButLocked() {
        int numberOfNodeEligibleForSchedulingBeforeFreeing = getNumberOfFreeNodes();
        assertThat(rmCore.setFreeNodes(ImmutableList.of(mockedBusyNode)).getBooleanValue()).isTrue();
        assertThat(getNumberOfFreeNodes()).isEqualTo(numberOfNodeEligibleForSchedulingBeforeFreeing);
        verify(mockedBusyNode).setFree();
    }

    @Test
    public void testLockWhateverNodeStateIs() {
        for (NodeState nodeState : NodeState.values()) {
            testLockNodeState(nodeState);
        }
    }

    private void testLockNodeState(NodeState nodeState) {
        RMNodeImpl rmNode = Mockito.spy(RMNodeHelper.basicWithMockedInternals().getLeft());
        rmNode.setState(nodeState);

        HashMap<String, RMNode> allNodes = new HashMap<>();
        allNodes.put(rmNode.getNodeURL(), rmNode);

        ArrayList<RMNode> freeNodes = Lists.newArrayList((RMNode) rmNode);

        RMCore rmCore = new RMCore(new HashMap<String, NodeSource>(),
                                   new ArrayList<String>(),
                                   allNodes,
                                   Mockito.mock(Client.class),
                                   Mockito.mock(RMMonitoringImpl.class),
                                   Mockito.mock(SelectionManager.class),
                                   freeNodes,
                                   Mockito.mock(RMDBManager.class));

        BooleanWrapper lockResult = rmCore.lockNodes(ImmutableSet.of(rmNode.getNodeURL()));

        assertThat(lockResult.getBooleanValue()).isTrue();
        assertThat(rmNode.getState()).isEqualTo(nodeState);
        assertThat(rmNode.isLocked()).isTrue();
        assertThat(freeNodes).isEmpty();
    }

    @Test
    public void testInitNodesLockRestorationManagerDisabled() {
        PAResourceManagerProperties.RM_NODES_LOCK_RESTORATION.updateProperty("false");
        populateRMCore();

        assertThat(nodesLockRestorationManager).isNotNull();
        assertThat(nodesLockRestorationManager.isInitialized()).isFalse();

        verify(nodesLockRestorationManager, never()).initialize();
    }

    @Test
    public void testInitNodesLockRestorationManagerEnabled() {
        PAResourceManagerProperties.RM_NODES_LOCK_RESTORATION.updateProperty("true");
        populateRMCore();

        assertThat(nodesLockRestorationManager).isNotNull();
        assertThat(nodesLockRestorationManager.isInitialized()).isTrue();

        verify(nodesLockRestorationManager).initialize();
    }

    /**
     * 6 nodes (same nodesource).
     */
    private void populateRMCore() {

        when(mockedCaller.checkPermission(Matchers.any(Permission.class), Matchers.any(String.class))).thenReturn(true);
        when(mockedSelectionManager.selectNodes(Matchers.any(Criteria.class),
                                                Matchers.any(Client.class))).thenReturn(new NodeSet());

        HashMap<String, NodeSource> nodeSources = new HashMap<String, NodeSource>(1);
        configureNodeSource(mockedNodeSource, "NODESOURCE-test");
        nodeSources.put(mockedNodeSource.getName(), mockedNodeSource);

        // MockedRMNodeParameters(String url, boolean isFree, boolean isDown, boolean isLocked, NodeSource nodeSource, RMNode rmNode)
        configureRMNode(new MockedRMNodeParameters("mockedRemovableNode",
                                                   true,
                                                   true,
                                                   false,
                                                   mockedNodeSource,
                                                   "NODESOURCE-test",
                                                   mockedRemovableNode));
        configureRMNode(new MockedRMNodeParameters("mockedUnremovableNode",
                                                   false,
                                                   true,
                                                   false,
                                                   mockedNodeSource,
                                                   "NODESOURCE-test",
                                                   mockedUnremovableNode));
        configureRMNode(new MockedRMNodeParameters(RMDeployingNode.PROTOCOL_ID + "://removableNode",
                                                   true,
                                                   true,
                                                   false,
                                                   mockedNodeSource,
                                                   "NODESOURCE-test",
                                                   mockedRemovableNodeInDeploy));
        configureRMNode(new MockedRMNodeParameters(RMDeployingNode.PROTOCOL_ID + "://unRemovableNode",
                                                   false,
                                                   false,
                                                   false,
                                                   mockedNodeSource,
                                                   "NODESOURCE-test",
                                                   mockedUnremovableNodeInDeploy));
        configureRMNode(new MockedRMNodeParameters("mockedBusyNode",
                                                   false,
                                                   false,
                                                   true,
                                                   mockedNodeSource,
                                                   "NODESOURCE-test",
                                                   mockedBusyNode));

        configureRMNode(new MockedRMNodeParameters("mockedFreeButLockedNode",
                                                   true,
                                                   false,
                                                   true,
                                                   mockedNodeSource,
                                                   "NODESOURCE-test",
                                                   mockedFreeButLockedNode));

        HashMap<String, RMNode> nodes = new HashMap<>(6);
        nodes.put(mockedRemovableNodeInDeploy.getNodeName(), mockedRemovableNodeInDeploy);
        nodes.put(mockedUnremovableNodeInDeploy.getNodeName(), mockedUnremovableNodeInDeploy);
        nodes.put(mockedRemovableNode.getNodeName(), mockedRemovableNode);
        nodes.put(mockedUnremovableNode.getNodeName(), mockedUnremovableNode);
        nodes.put(mockedBusyNode.getNodeName(), mockedBusyNode);
        nodes.put(mockedFreeButLockedNode.getNodeName(), mockedFreeButLockedNode);

        ArrayList<RMNode> freeNodes = new ArrayList<>(3);
        freeNodes.add(mockedRemovableNodeInDeploy);
        freeNodes.add(mockedRemovableNode);
        freeNodes.add(mockedFreeButLockedNode);

        rmCore = new RMCore(nodeSources,
                            new ArrayList<String>(),
                            nodes,
                            mockedCaller,
                            mockedMonitoring,
                            mockedSelectionManager,
                            freeNodes,
                            dataBaseManager);

        rmCore = Mockito.spy(rmCore);

        nodesLockRestorationManager = null;

        doReturn(new Function<RMCore, NodesLockRestorationManager>() {
            @Override
            public NodesLockRestorationManager apply(RMCore rmCore) {
                nodesLockRestorationManager = new NodesLockRestorationManager(rmCore);
                nodesLockRestorationManager = Mockito.spy(nodesLockRestorationManager);

                doReturn(HashBasedTable.create()).when(nodesLockRestorationManager).findNodesLockedOnPreviousRun();

                return nodesLockRestorationManager;
            }
        }).when(rmCore).getNodesLockRestorationManagerBuilder();

        rmCore.initNodesRestorationManager();
    }

    private void configureRMNode(MockedRMNodeParameters param) {
        RMNode rmNode = param.getRmNode();
        Node mockedNode = Mockito.mock(Node.class);
        NodeInformation mockedNodeInformation = Mockito.mock(NodeInformation.class);

        when(mockedNode.getNodeInformation()).thenReturn(mockedNodeInformation);
        when(rmNode.getNode()).thenReturn(mockedNode);
        when(rmNode.getNodeName()).thenReturn(param.getUrl());
        when(rmNode.isDown()).thenReturn(param.isDown());
        when(rmNode.isFree()).thenReturn(param.isFree());
        when(rmNode.isLocked()).thenReturn(param.isLocked());
        when(mockedNodeInformation.getURL()).thenReturn(param.getUrl());
        when(mockedNodeInformation.getName()).thenReturn(param.getUrl());
        when(rmNode.getNodeSource()).thenReturn(param.getNodeSource());
        when(rmNode.getNodeSourceName()).thenReturn(param.getNodeSourceName());
        when(rmNode.getAdminPermission()).thenReturn(null);
        when(rmNode.getProvider()).thenReturn(new Client());

        Client client = Mockito.mock(Client.class);

        when(rmNode.getOwner()).thenReturn(client);
        when(client.getName()).thenReturn("test");

        when(rmNode.getNodeURL()).thenReturn(param.getUrl());
    }

    private void configureNodeSource(NodeSource nodeSource, String nodeSourceName) {
        when(nodeSource.getName()).thenReturn(nodeSourceName);
        when(nodeSource.acquireNode(Matchers.any(String.class),
                                    Matchers.any(Client.class))).thenReturn(new BooleanWrapper(true));
    }

    private class MockedRMNodeParameters {

        private String url;

        private boolean isFree;

        private boolean isDown;

        private boolean isLocked;

        private NodeSource nodeSource;

        private String nodeSourceName;

        private RMNode rmNode;

        MockedRMNodeParameters(String url, boolean isFree, boolean isDown, boolean isLocked, NodeSource nodeSource,
                String nodeSourceName, RMNode rmNode) {
            this.url = url;
            this.isFree = isFree;
            this.isDown = isDown;
            this.isLocked = isLocked;
            this.nodeSource = nodeSource;
            this.nodeSourceName = nodeSourceName;
            this.rmNode = rmNode;
        }

        protected String getUrl() {
            return url;
        }

        protected boolean isFree() {
            return isFree;
        }

        protected boolean isDown() {
            return isDown;
        }

        public NodeSource getNodeSource() {
            return nodeSource;
        }

        public String getNodeSourceName() {
            return nodeSourceName;
        }

        public RMNode getRmNode() {
            return rmNode;
        }

        public boolean isLocked() {
            return isLocked;
        }

    }

}
