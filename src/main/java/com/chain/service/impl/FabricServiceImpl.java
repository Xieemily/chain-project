package com.chain.service.impl;

import com.chain.service.FabricService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.client.CommitStatusException;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.Network;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FabricServiceImpl implements FabricService {

    public final Network fabricNetwork;

    @SneakyThrows
    @Override
    public void addAsset() {
        Contract contract = fabricNetwork.getContract("events");
        var commit = contract.newProposal("CreateAsset")
                .addArguments("asset"+System.currentTimeMillis() % 10000, "blue", "10", "Sam", "100")
                .build()
                .endorse()
                .submitAsync();

        var status = commit.getStatus();
        if (!status.isSuccessful()) {
            throw new RuntimeException("failed to commit transaction with status code " + status.getCode());
        }

        log.info("\n*** CreateAsset committed successfully, status {}", status.getBlockNumber());
    }
}
