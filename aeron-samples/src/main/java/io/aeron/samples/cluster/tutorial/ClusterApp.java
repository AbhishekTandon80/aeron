/*
 * Copyright 2023 Adaptive Financial Consulting
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.aeron.samples.cluster.tutorial;

import io.aeron.CommonContext;
import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.samples.cluster.ClusterConfig;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.SystemEpochClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.lang.Integer.parseInt;

/**
 * Sample cluster application
 */
public class ClusterApp
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterApp.class);

    private static final int PORT_BASE = 9000;
    private static final int PORTS_PER_NODE = 100;

    /**
     * The main method.
     * @param args command line args
     */
    public static void main(final String[] args)
    {

        System.setProperty("aeron.event.log", "all");
        System.setProperty("aeron.event.log", "admin,network,driver");

        System.out.println("Starting Basic Auction Clustered Service Node...");
        final int nodeId = parseInt(System.getProperty("aeron.cluster.tutorial.nodeId"));               // <1>
        final String[] hostnames = System.getProperty("aeron.cluster.tutorial.hostnames",
                "localhost,localhost,localhost").split(",");
        // <2>
        final String hostname = hostnames[nodeId];

        final File baseDir = new File(System.getProperty("user.dir"), "node" + nodeId);                 // <3>
        final String aeronDirName = CommonContext.getAeronDirectoryName() + "-" + nodeId + "-driver";



        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
        final int portBase = getBasePort(nodeId);
        //final int nodeId = getClusterNode();
//        final String hosts = getClusterAddresses();

        final List<String> hostAddresses = Arrays.stream(hostnames).toList(); // List.of(hosts.split(","));
        final ClusterConfig clusterConfig = ClusterConfig.create(nodeId, hostAddresses, hostAddresses, portBase,
            new BasicAuctionClusteredService());
        clusterConfig.consensusModuleContext().ingressChannel("aeron:udp");
        clusterConfig.baseDir(getBaseDir(nodeId));

        //this may need tuning for your environment.
        clusterConfig.consensusModuleContext().leaderHeartbeatTimeoutNs(TimeUnit.SECONDS.toNanos(3));

        //await DNS resolution of all the hostnames
        //hostAddresses.forEach(ClusterApp::awaitDnsResolution);

        try (
            ClusteredMediaDriver mediaDriver = ClusteredMediaDriver.launch(
                clusterConfig.mediaDriverContext(),
                clusterConfig.archiveContext(),
                clusterConfig.consensusModuleContext());
            ClusteredServiceContainer clusteredServiceContainer = ClusteredServiceContainer.launch(
                clusterConfig.clusteredServiceContext()))
        {
            mediaDriver.toString();
            clusteredServiceContainer.toString();
            LOGGER.info("Started Cluster Node...");
            barrier.await();
            LOGGER.info("Exiting");
        }
    }

    /***
     * Get the base directory for the cluster configuration
     * @param nodeId node id
     * @return base directory
     */
    private static File getBaseDir(final int nodeId)
    {
        final String baseDir = System.getenv("BASE_DIR");
        if (null == baseDir || baseDir.isEmpty())
        {
            return new File(System.getProperty("user.dir"), "node" + nodeId);
        }

        return new File(baseDir);
    }

    /**
     * Read the cluster addresses from the environment variable CLUSTER_ADDRESSES or the
     * system property cluster.addresses
     * @return cluster addresses
     */
    private static String getClusterAddresses()
    {
        String clusterAddresses = System.getenv("CLUSTER_ADDRESSES");
        if (null == clusterAddresses || clusterAddresses.isEmpty())
        {
            clusterAddresses = System.getProperty("cluster.addresses", "localhost");
        }
        return clusterAddresses;
    }

    /**
     * Get the cluster node id
     * @return cluster node id, default 0
     */
    private static int getClusterNode()
    {
        String clusterNode = System.getenv("CLUSTER_NODE");
        if (null == clusterNode || clusterNode.isEmpty())
        {
            clusterNode = System.getProperty("node.id", "0");
        }
        return parseInt(clusterNode);
    }

    /**
     * Get the base port for the cluster configuration
     * @return base port, default 9000
     */
    private static int getBasePort(int nodeId)
    {
        if ("TRUE".equalsIgnoreCase(System.getProperty("IS_CLOUD_NATIVE")))
        {
            String portBaseString = System.getenv("CLUSTER_PORT_BASE");
            if (null == portBaseString || portBaseString.isEmpty())
            {
                portBaseString = System.getProperty("port.base", "9000");
            }
            return parseInt(portBaseString);
        } else {
            // For local development, we can use a fixed port base
            return calculatePort(nodeId); // Example: 9000 for node 0, 9010 for node 1, etc.
        }

    }

    static int calculatePort(final int nodeId) {
        return PORT_BASE + (nodeId * PORTS_PER_NODE);
    }

    /**
     * Await DNS resolution of the given host. Under Kubernetes, this can take a while.
     * @param host of the node to resolve
     */
    private static void awaitDnsResolution(final String host)
    {
        if (applyDnsDelay())
        {
            LOGGER.info("Waiting 5 seconds for DNS to be registered...");
            quietSleep(5000);
        }

        final long endTime = SystemEpochClock.INSTANCE.time() + 60000;
        java.security.Security.setProperty("networkaddress.cache.ttl", "0");

        boolean resolved = false;
        while (!resolved)
        {
            if (SystemEpochClock.INSTANCE.time() > endTime)
            {
                LOGGER.error("cannot resolve name {}, exiting", host);
                System.exit(-1);
            }

            try
            {
                InetAddress.getByName(host);
                resolved = true;
            }
            catch (final UnknownHostException e)
            {
                LOGGER.warn("cannot yet resolve name {}, retrying in 3 seconds", host);
                quietSleep(3000);
            }
        }
    }

    /**
     * Sleeps for the given number of milliseconds, ignoring any interrupts.
     *
     * @param millis the number of milliseconds to sleep.
     */
    private static void quietSleep(final long millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (final InterruptedException ex)
        {
            LOGGER.warn("Interrupted while sleeping");
        }
    }

    /**
     * Apply DNS delay
     * @return true if DNS delay should be applied
     */
    private static boolean applyDnsDelay()
    {
        final String dnsDelay = System.getenv("DNS_DELAY");
        if (null == dnsDelay || dnsDelay.isEmpty())
        {
            return false;
        }
        return Boolean.parseBoolean(dnsDelay);
    }
}
