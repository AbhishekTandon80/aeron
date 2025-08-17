#!/bin/sh
set -e

JVM_OPT='--add-exports java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/java.util.zip=ALL-UNNAMED -XX:+UseBiasedLocking -XX:BiasedLockingStartupDelay=0 -XX:+UnlockExperimentalVMOptions -XX:+TrustFinalNonStaticFields -XX:+UnlockDiagnosticVMOptions -XX:GuaranteedSafepointInterval=300000 -XX:+UseParallelGC -Daeron.event.cluster.log=all -Daeron.event.cluster.log.disable=CANVASS_POSITION,APPEND_POSITION,COMMIT_POSITION -Daeron.client.idle.sleep.duration=0 -Duser.dir=/data'

# Run the Java application with the specified JVM options
echo "Starting BasicAuctionClusteredServiceNode with JVM options: $JVM_OPT"

java $JVM_OPT -cp /app/aeron-all.jar io.aeron.samples.cluster.tutorial.BasicAuctionClusteredServiceNode