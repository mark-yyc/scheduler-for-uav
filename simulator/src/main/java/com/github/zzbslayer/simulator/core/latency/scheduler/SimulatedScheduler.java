package com.github.zzbslayer.simulator.core.latency.scheduler;

import com.github.zzbslayer.simulator.core.availability.graph.Graph;
import com.github.zzbslayer.simulator.core.availability.utils.ScenarioParamter;
import com.github.zzbslayer.simulator.core.latency.prediction.Prediction;
import com.github.zzbslayer.simulator.core.latency.record.AccessRecord;
import com.github.zzbslayer.simulator.core.latency.record.ServicePlacementRecord;
import com.github.zzbslayer.simulator.core.latency.prediction.mapper.AccessInstanceMapper;
import com.github.zzbslayer.simulator.core.latency.utils.LatencyAndWorkLoadStatistics;
import com.github.zzbslayer.simulator.core.strategy.AvailabilityAwared;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Map;

@Slf4j
public class SimulatedScheduler {
    ScenarioParamter scenarioParamter;

    int[] expectedServiceReplicaNumOfNow;
    int[] actualServiceReplicaNum;
    int[] expectedServiceReplicaNumOfFuture;

    ServicePlacementRecord servicePlacementRecord;
    AccessRecord currentCycleAccessRecord;
    Prediction prediction;

    LatencyAndWorkLoadStatistics latencyAndWorkLoadStatistics;

    AccessInstanceMapper mapper = AccessInstanceMapper.getMapper();

    public SimulatedScheduler(ScenarioParamter scenarioParamter, Prediction prediction) {
        this.scenarioParamter = scenarioParamter;

        this.expectedServiceReplicaNumOfNow = new int[scenarioParamter.getServiceNum()];
        this.expectedServiceReplicaNumOfFuture = new int[scenarioParamter.getServiceNum()];
        this.actualServiceReplicaNum = new int[scenarioParamter.getServiceNum()];
        Arrays.fill(this.expectedServiceReplicaNumOfFuture, 1);

        this.servicePlacementRecord = new ServicePlacementRecord(scenarioParamter.getGraph(), scenarioParamter.getServiceNum());
        this.currentCycleAccessRecord = new AccessRecord(scenarioParamter.getNodeNum(), scenarioParamter.getServiceNum());
        this.prediction = prediction;

        this.latencyAndWorkLoadStatistics = new LatencyAndWorkLoadStatistics();

        this.schedule();
    }

    public void putServiceReplicaNum(int service, int replica) {
        this.expectedServiceReplicaNumOfFuture[service] = replica;
    }

    /**
     * 从 srcNode 节点 访问 service 服务
     * 返回 srcNode 到 service 所在节点的跳数
     *
     * 记录并更新相应的各项指标
     */
    public void access(int srcNode, int service) {
        this.currentCycleAccessRecord.access(srcNode, service);
        /**
         * 访问最近的包含 service 的节点
         */
        int nearestNode = servicePlacementRecord.findNearestService(srcNode, service);
        //servicePlacementRecord.printServicePlacement();
        //System.out.println(srcNode + " visist " + nearestNode);

        int distance = Graph.calculateDistance(this.scenarioParamter.getGraph(), srcNode, nearestNode);

        /**
         * 更新平均跳数指标
         */
        latencyAndWorkLoadStatistics.addHop(distance);
    }

    public void predict() {
        log.info("SimulatedScheduler.predict: ");
        /**
         * 更新平均机器负载指标
         * TODO 更新 expectedReplicaNum; 节点访问量对于部署位置的影响
         */
        this.currentCycleAccessRecord.printServiceAccessMap("Current access record: ");

        // 更新当前访问量所需要的 replica
        Map<Integer, Integer> nowSvcAccessMap = this.currentCycleAccessRecord.getServiceAccessMap();
        for (int i = 0; i < expectedServiceReplicaNumOfNow.length; ++i) {
            expectedServiceReplicaNumOfNow[i] = mapper.accessToInstance(nowSvcAccessMap.get(i));
        }

        /**
         * Prediction 应直接返回 Service placement
         */
        int[] expectedServiceReplicaNumOfFuture = this.prediction.predict(this.currentCycleAccessRecord);
        this.expectedServiceReplicaNumOfFuture = expectedServiceReplicaNumOfFuture;
    }


    private void printActualServicePlacement() {
        StringBuilder sb = new StringBuilder();
        sb.append("Actual service placement: ");
        sb.append("[ ");
        for (int i: actualServiceReplicaNum) {
            sb.append(i);
            sb.append(", ");
        }
        sb.append("] ");
        log.info(sb.toString());
    }

    private void printExpectedServicePlacement() {
        StringBuilder sb = new StringBuilder();
        sb.append("Expected service placement: ");
        sb.append("[ ");
        for (int i: expectedServiceReplicaNumOfFuture) {
            sb.append(i);
            sb.append(", ");
        }
        sb.append("] ");
        log.info(sb.toString());
    }

    private void printNodeWorkLoad() {
        StringBuilder sb = new StringBuilder();
        sb.append("Node workload: ");
        sb.append("[ ");
        for (int i: servicePlacementRecord.getNodeWorkLoads()) {
            sb.append(i);
            sb.append(", ");
        }
        sb.append("] ");
        log.info(sb.toString());
    }

    public void reset() {
        this.currentCycleAccessRecord.reset();
    }
    /**
     * Place unscheduled services to some nodes
     */
    public void schedule() {

        log.info("Before schedule: ");
        printActualServicePlacement();
        printExpectedServicePlacement();
        printNodeWorkLoad();

        for (int service = 0; service < actualServiceReplicaNum.length; ++service) {
            int actualReplica = actualServiceReplicaNum[service];
            int expectedReplicaOfFuture = expectedServiceReplicaNumOfFuture[service];
            int expectedReplicaOfNow = expectedServiceReplicaNumOfNow[service];

            if (actualReplica < expectedReplicaOfFuture) {
                int newReplica = expectedReplicaOfFuture - actualReplica;
                for (int j = 0; j < newReplica; ++j) {
                    // Update nodeWorkLoad
                    // 一开始这块设计没做好，本来placeService 只是想找到最合适的节点，后来就直接更新 nodeWorkload 了，
                    int placedNode = AvailabilityAwared.placeService(this.scenarioParamter, this.servicePlacementRecord.getNodeWorkLoads());
                    // Record service placement
                    this.servicePlacementRecord.putServiceAtNode(service, placedNode);
                }
            }
            else if (actualReplica > expectedReplicaOfNow) {
                int removeReplica = expectedReplicaOfNow - actualReplica;
                for (int j = 0; j < removeReplica; ++j) {

                }
            }

            actualServiceReplicaNum[service] = expectedReplicaOfFuture;
        }

        log.info("After schedule: ");
        printActualServicePlacement();
        printNodeWorkLoad();

        // UPDATE statistics
        this.latencyAndWorkLoadStatistics.addMachineWorkLoad(this.servicePlacementRecord.getNodeWorkLoads());

        // Reset at the end of schedule
        this.reset();
    }

    public void print() {
        latencyAndWorkLoadStatistics.print();
    }

}
