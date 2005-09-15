/*
 * ################################################################
 *
 * ProActive: The Java(TM) library for Parallel, Distributed,
 *            Concurrent computing with Security and Mobility
 *
 * Copyright (C) 1997-2002 INRIA/University of Nice-Sophia Antipolis
 * Contact: proactive-support@inria.fr
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://www.inria.fr/oasis/ProActive/contacts.html
 *  Contributor(s):
 *
 * ################################################################
 */
package org.objectweb.proactive.core.runtime;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;

import org.objectweb.proactive.Body;
import org.objectweb.proactive.core.ProActiveException;
import org.objectweb.proactive.core.UniqueRuntimeID;
import org.objectweb.proactive.core.body.BodyAdapterForwarder;
import org.objectweb.proactive.core.body.BodyForwarderImpl;
import org.objectweb.proactive.core.body.RemoteBodyForwarder;
import org.objectweb.proactive.core.body.UniversalBody;
import org.objectweb.proactive.core.body.ft.checkpointing.Checkpoint;
import org.objectweb.proactive.core.body.rmi.RmiRemoteBodyForwarderImpl;
import org.objectweb.proactive.core.descriptor.data.ProActiveDescriptor;
import org.objectweb.proactive.core.descriptor.data.VirtualNode;
import org.objectweb.proactive.core.mop.ConstructorCall;
import org.objectweb.proactive.core.mop.ConstructorCallExecutionFailedException;
import org.objectweb.proactive.core.node.NodeException;
import org.objectweb.proactive.core.process.ExternalProcess;
import org.objectweb.proactive.core.process.HierarchicalProcess;
import org.objectweb.proactive.core.process.UniversalProcess;
import org.objectweb.proactive.core.ssh.rmissh.SshRMIClientSocketFactory;
import org.objectweb.proactive.core.ssh.rmissh.SshRMIServerSocketFactory;
import org.objectweb.proactive.core.util.UrlBuilder;
import org.objectweb.proactive.ext.security.Communication;
import org.objectweb.proactive.ext.security.ProActiveSecurityManager;
import org.objectweb.proactive.ext.security.SecurityContext;
import org.objectweb.proactive.ext.security.crypto.KeyExchangeException;
import org.objectweb.proactive.ext.security.exceptions.RenegotiateSessionException;
import org.objectweb.proactive.ext.security.exceptions.SecurityNotAvailableException;


public class ProActiveRuntimeForwarderImpl extends ProActiveRuntimeImpl
    implements ProActiveRuntimeForwarder, LocalProActiveRuntime {
    protected HashMap registeredRuntimes;
    private HashMap hierarchicalProcesses;
    private ProActiveRuntime parentRuntime = null;
    private BodyForwarderImpl bodyForwarder = null;
    private BodyAdapterForwarder bodyAdapterForwarder = null;
    private RemoteBodyForwarder remoteBodyForwarder = null;

    protected ProActiveRuntimeForwarderImpl() {
        super();
        registeredRuntimes = new HashMap();

        hierarchicalProcesses = new HashMap();

        bodyForwarder = new BodyForwarderImpl();

        if ("ibis".equals(System.getProperty("proactive.communication.protocol"))) {
            if (logger.isDebugEnabled()) {
                logger.debug("Factory is ibis");
            }

            logger.info("Ibis forwarding not yet implemented");

            // TODO support Ibis forwarding
        } else if ("http".equals(System.getProperty(
                        "proactive.communication.protocol"))) {
            if (logger.isDebugEnabled()) {
                logger.debug("Factory is http");
            }

            logger.info("Http forwarding not yet implemented");

            // TODO support Http forwarding
        } else if ("rmissh".equals(System.getProperty(
                        "proactive.communication.protocol"))) {
            if (logger.isDebugEnabled()) {
                logger.debug("Factory is rmissh");
            }

            try {
                remoteBodyForwarder = new RmiRemoteBodyForwarderImpl(bodyForwarder,
                        new SshRMIServerSocketFactory(),
                        new SshRMIClientSocketFactory());
            } catch (RemoteException e) {
                logger.info("Local forwarder cannot be created.");
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Factory is rmi");
            }

            try {
                remoteBodyForwarder = new RmiRemoteBodyForwarderImpl(bodyForwarder);
            } catch (RemoteException e) {
                logger.info("Local forwarder cannot be created.");
            }
        }

        bodyAdapterForwarder = new BodyAdapterForwarder(remoteBodyForwarder);
    }

    public BodyAdapterForwarder getBodyAdapterForwarder() {
        return bodyAdapterForwarder;
    }

    public BodyForwarderImpl getBodyForwarder() {
        return bodyForwarder;
    }

    public RemoteBodyForwarder getRemoteBodyForwarder() {
        return remoteBodyForwarder;
    }

    //
    // --- OVERIDING SOME METHODS
    //
    public void setParent(String parentRuntimeName) {
        try {
            parentRuntime = RuntimeFactory.getRuntime(parentRuntimeName,
                    UrlBuilder.getProtocol(parentRuntimeName));
        } catch (ProActiveException e) {
            logger.warn(
                "Cannot retreive my parent runtime. All will go wrong !");
            parentRuntime = null;
        }

        super.setParent(parentRuntimeName);
    }

    public synchronized void register(ProActiveRuntime proActiveRuntimeDist,
        String proActiveRuntimeName, String creatorID, String creationProtocol,
        String vmName) {
        try {
            // erk ! 
            ProActiveRuntimeAdapterForwarderImpl adapter = new ProActiveRuntimeAdapterForwarderImpl((ProActiveRuntimeAdapterForwarderImpl) RuntimeFactory.getDefaultRuntime(),
                    proActiveRuntimeDist);

            if (parentRuntime != null) {
                parentRuntime.register(adapter, proActiveRuntimeName,
                    creatorID, creationProtocol, vmName);
            } else {
                logger.warn(
                    "setParent() as not yet be called. Cannot forward the registration");
            }
        } catch (ProActiveException e) {
            e.printStackTrace();
            logger.warn("Cannot register this runtime: " +
                proActiveRuntimeName);
        }
    }

    static private Object buildKey(String padURL, String vmName) {
        return padURL + "??" + vmName;
    }

    public ExternalProcess getProcessToDeploy(
        ProActiveRuntime proActiveRuntimeDist, String creatorID, String vmName,
        String padURL) {
        HierarchicalProcess hp = (HierarchicalProcess) hierarchicalProcesses.get(buildKey(
                    padURL, vmName));

        if (hp != null) {
            return hp.getHierarchicalProcess();
        } else {
            return null;
        }
    }

    /**
     * Add process to process list to be hierarchically deployed,
     * if we launched a forwarder it will ask for it using register().
     * @param padURL  URL of the ProActive Descriptor
     * @param urid  Virtual Machine associated to process
     * @param process The process
     */
    protected void setProcessesToDeploy(String padURL, String vmName,
        ExternalProcess process) {
        hierarchicalProcesses.put(buildKey(padURL, vmName), process);
    }

    public UniversalBody createBody(UniqueRuntimeID urid, String nodeName,
        ConstructorCall bodyConstructorCall, boolean isNodeLocal)
        throws ProActiveException, ConstructorCallExecutionFailedException, 
            InvocationTargetException {
        if (urid == null) {
            return this.createBody(nodeName, bodyConstructorCall, isNodeLocal);
        }

        ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

        if (part == null) {
            logger.warn("No runtime associated to this urid (" + urid + ")");

            return null;
        }

        // create the body on the remote node and get a reference on it
        UniversalBody rBody = part.createBody(nodeName, bodyConstructorCall,
                isNodeLocal);

        // Create a forwarder on it
        //        bodyForwarder.add(rBody);
        //        BodyAdapterForwarder baf =  new BodyAdapterForwarder(bodyAdapterForwarder, (BodyAdapterImpl)rBody, rBody.getID());
        return rBody;
    }

    //
    // --- MULTIPLEXER ---
    //
    public String createLocalNode(UniqueRuntimeID urid, String nodeName,
        boolean replacePreviousBinding, ProActiveSecurityManager psm,
        String vnName, String jobId) throws NodeException {
        if (urid == null) {
            return this.createLocalNode(nodeName, replacePreviousBinding, psm,
                vnName, jobId);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.createLocalNode(nodeName, replacePreviousBinding,
                    psm, vnName, jobId);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public void killAllNodes(UniqueRuntimeID urid) throws ProActiveException {
        if (urid == null) {
            this.killAllNodes();
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                part.killAllNodes();
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }
    }

    public void killNode(UniqueRuntimeID urid, String nodeName)
        throws ProActiveException {
        if (urid == null) {
            this.killNode(nodeName);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                part.killNode(nodeName);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }
    }

    public void createVM(UniqueRuntimeID urid, UniversalProcess remoteProcess)
        throws IOException, ProActiveException {
        if (urid == null) {
            this.createVM(remoteProcess);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                part.createVM(remoteProcess);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }
    }

    public String[] getLocalNodeNames(UniqueRuntimeID urid)
        throws ProActiveException {
        if (urid == null) {
            return this.getLocalNodeNames();
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.getLocalNodeNames();
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public VMInformation getVMInformation(UniqueRuntimeID urid) {
        if (urid == null) {
            return this.getVMInformation();
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.getVMInformation();
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public void register(UniqueRuntimeID urid,
        ProActiveRuntime proActiveRuntimeDist, String proActiveRuntimeUrl,
        String creatorID, String creationProtocol, String rurid)
        throws ProActiveException {
        if (urid == null) {
            this.register(proActiveRuntimeDist, proActiveRuntimeUrl, creatorID,
                creationProtocol, rurid);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                part.register(proActiveRuntimeDist, proActiveRuntimeUrl,
                    creatorID, creationProtocol, rurid);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }
    }

    public void unregister(UniqueRuntimeID urid,
        ProActiveRuntime proActiveRuntimeDist, String proActiveRuntimeUrl,
        String creatorID, String creationProtocol, String rurid)
        throws ProActiveException {
        if (urid == null) {
            this.unregister(proActiveRuntimeDist, proActiveRuntimeUrl,
                creatorID, creationProtocol, rurid);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                part.unregister(proActiveRuntimeDist, proActiveRuntimeUrl,
                    creatorID, creationProtocol, rurid);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }
    }

    public ProActiveRuntime[] getProActiveRuntimes(UniqueRuntimeID urid)
        throws ProActiveException {
        if (urid == null) {
            return this.getProActiveRuntimes();
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.getProActiveRuntimes();
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public ProActiveRuntime getProActiveRuntime(UniqueRuntimeID urid,
        String proActiveRuntimeName) throws ProActiveException {
        if (urid == null) {
            return this.getProActiveRuntime(proActiveRuntimeName);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.getProActiveRuntime(proActiveRuntimeName);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public void addAcquaintance(UniqueRuntimeID urid,
        String proActiveRuntimeName) throws ProActiveException {
        if (urid == null) {
            this.addAcquaintance(proActiveRuntimeName);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                part.addAcquaintance(proActiveRuntimeName);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }
    }

    public String[] getAcquaintances(UniqueRuntimeID urid)
        throws ProActiveException {
        if (urid == null) {
            return this.getAcquaintances();
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.getAcquaintances();
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public void rmAcquaintance(UniqueRuntimeID urid, String proActiveRuntimeName)
        throws ProActiveException {
        if (urid == null) {
            this.rmAcquaintance(proActiveRuntimeName);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                part.rmAcquaintance(proActiveRuntimeName);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }
    }

    public void killRT(UniqueRuntimeID urid, boolean softly)
        throws Exception {
        if (urid == null) {
            this.killRT(softly);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                part.killRT(softly);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }
    }

    public String getURL(UniqueRuntimeID urid) {
        if (urid == null) {
            return this.getURL();
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.getURL();
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public ArrayList getActiveObjects(UniqueRuntimeID urid, String nodeName)
        throws ProActiveException {
        if (urid == null) {
            return this.getActiveObjects(nodeName);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.getActiveObjects(nodeName);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public ArrayList getActiveObjects(UniqueRuntimeID urid, String nodeName,
        String className) throws ProActiveException {
        if (urid == null) {
            return this.getActiveObjects(nodeName, className);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.getActiveObjects(nodeName, className);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public VirtualNode getVirtualNode(UniqueRuntimeID urid,
        String virtualNodeName) throws ProActiveException {
        if (urid == null) {
            return this.getVirtualNode(virtualNodeName);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.getVirtualNode(virtualNodeName);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public void registerVirtualNode(UniqueRuntimeID urid,
        String virtualNodeName, boolean replacePreviousBinding)
        throws ProActiveException {
        if (urid == null) {
            this.registerVirtualNode(virtualNodeName, replacePreviousBinding);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                part.registerVirtualNode(virtualNodeName, replacePreviousBinding);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }
    }

    public void unregisterVirtualNode(UniqueRuntimeID urid,
        String virtualNodeName) throws ProActiveException {
        if (urid == null) {
            this.unregisterVirtualNode(virtualNodeName);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                part.unregisterVirtualNode(virtualNodeName);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }
    }

    public void unregisterAllVirtualNodes(UniqueRuntimeID urid)
        throws ProActiveException {
        if (urid == null) {
            this.unregisterAllVirtualNodes();
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                part.unregisterAllVirtualNodes();
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }
    }

    public String getJobID(UniqueRuntimeID urid, String nodeUrl)
        throws ProActiveException {
        if (urid == null) {
            return this.getJobID(nodeUrl);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.getJobID(nodeUrl);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public UniversalBody receiveBody(UniqueRuntimeID urid, String nodeName,
        Body body) throws ProActiveException {
        if (urid == null) {
            return this.receiveBody(nodeName, body);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.receiveBody(nodeName, body);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public UniversalBody receiveCheckpoint(UniqueRuntimeID urid,
        String nodeName, Checkpoint ckpt, int inc) throws ProActiveException {
        if (urid == null) {
            return this.receiveCheckpoint(nodeName, ckpt, inc);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.receiveCheckpoint(nodeName, ckpt, inc);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public ExternalProcess getProcessToDeploy(UniqueRuntimeID urid,
        ProActiveRuntime proActiveRuntimeDist, String creatorID, String vmName,
        String padURL) throws ProActiveException {
        if (urid == null) {
            return this.getProcessToDeploy(proActiveRuntimeDist, creatorID,
                vmName, padURL);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.getProcessToDeploy(proActiveRuntimeDist, creatorID,
                    vmName, padURL);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public String getVNName(UniqueRuntimeID urid, String Nodename)
        throws ProActiveException {
        if (urid == null) {
            return this.getVNName(Nodename);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.getVNName(Nodename);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public ArrayList getEntities(UniqueRuntimeID urid)
        throws IOException, SecurityNotAvailableException {
        if (urid == null) {
            return this.getEntities();
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.getEntities();
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public SecurityContext getPolicy(UniqueRuntimeID urid, SecurityContext sc)
        throws SecurityNotAvailableException, IOException {
        if (urid == null) {
            return this.getPolicy(sc);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.getPolicy(sc);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public byte[] getClassDataFromThisRuntime(UniqueRuntimeID urid,
        String className) throws ProActiveException {
        if (urid == null) {
            return this.getClassDataFromThisRuntime(className);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.getClassDataFromThisRuntime(className);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public byte[] getClassDataFromParentRuntime(UniqueRuntimeID urid,
        String className) throws ProActiveException {
        if (urid == null) {
            return this.getClassDataFromParentRuntime(className);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.getClassDataFromParentRuntime(className);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public X509Certificate getCertificate(UniqueRuntimeID urid)
        throws SecurityNotAvailableException, IOException {
        if (urid == null) {
            return this.getCertificate();
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.getCertificate();
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public byte[] getCertificateEncoded(UniqueRuntimeID urid)
        throws SecurityNotAvailableException, IOException {
        if (urid == null) {
            return this.getCertificateEncoded();
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.getCertificateEncoded();
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public PublicKey getPublicKey(UniqueRuntimeID urid)
        throws SecurityNotAvailableException, IOException {
        if (urid == null) {
            return this.getPublicKey();
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.getPublicKey();
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public byte[][] publicKeyExchange(UniqueRuntimeID urid, long sessionID,
        byte[] myPublicKey, byte[] myCertificate, byte[] signature)
        throws SecurityNotAvailableException, RenegotiateSessionException, 
            KeyExchangeException, IOException {
        if (urid == null) {
            return this.publicKeyExchange(sessionID, myPublicKey,
                myCertificate, signature);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.publicKeyExchange(sessionID, myPublicKey,
                    myCertificate, signature);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public byte[] randomValue(UniqueRuntimeID urid, long sessionID,
        byte[] clientRandomValue)
        throws SecurityNotAvailableException, RenegotiateSessionException, 
            IOException {
        if (urid == null) {
            return this.randomValue(sessionID, clientRandomValue);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.randomValue(sessionID, clientRandomValue);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public byte[][] secretKeyExchange(UniqueRuntimeID urid, long sessionID,
        byte[] encodedAESKey, byte[] encodedIVParameters,
        byte[] encodedClientMacKey, byte[] encodedLockData,
        byte[] parametersSignature)
        throws SecurityNotAvailableException, RenegotiateSessionException, 
            IOException {
        if (urid == null) {
            return this.secretKeyExchange(sessionID, encodedAESKey,
                encodedIVParameters, encodedClientMacKey, encodedLockData,
                parametersSignature);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.secretKeyExchange(sessionID, encodedAESKey,
                    encodedIVParameters, encodedClientMacKey, encodedLockData,
                    parametersSignature);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }

    public long startNewSession(UniqueRuntimeID urid, Communication policy)
        throws SecurityNotAvailableException, RenegotiateSessionException, 
            IOException {
        if (urid == null) {
            return this.startNewSession(policy);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.startNewSession(policy);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return 0;
    }

    public void terminateSession(UniqueRuntimeID urid, long sessionID)
        throws SecurityNotAvailableException, IOException {
        if (urid == null) {
            this.terminateSession(sessionID);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                part.terminateSession(sessionID);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }
    }

    public ProActiveDescriptor getDescriptor(UniqueRuntimeID urid, String url,
        boolean isHierarchicalSearch) throws IOException, ProActiveException {
        if (urid == null) {
            return this.getDescriptor(url, isHierarchicalSearch);
        } else {
            ProActiveRuntime part = (ProActiveRuntime) registeredRuntimes.get(urid);

            if (part != null) {
                return part.getDescriptor(url, isHierarchicalSearch);
            } else {
                logger.warn("No runtime associated to this urid (" + urid +
                    ")");
            }
        }

        return null;
    }
}
