package com.chain;/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import com.chain.config.fabric.Connections;
import com.chain.parser.BlockParser;
import com.chain.parser.NamespaceReadWriteSet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.Status;
import org.hyperledger.fabric.client.*;
import org.hyperledger.fabric.protos.common.Block;
import org.hyperledger.fabric.protos.common.Envelope;
import org.hyperledger.fabric.protos.common.Payload;
import org.hyperledger.fabric.protos.ledger.rwset.NsReadWriteSet;
import org.hyperledger.fabric.protos.ledger.rwset.TxReadWriteSet;
import org.hyperledger.fabric.protos.ledger.rwset.kvrwset.KVRWSet;
import org.hyperledger.fabric.protos.ledger.rwset.kvrwset.KVWrite;
import org.hyperledger.fabric.protos.peer.ChaincodeAction;
import org.hyperledger.fabric.protos.peer.ChaincodeActionPayload;
import org.hyperledger.fabric.protos.peer.Transaction;
import org.hyperledger.fabric.protos.peer.TransactionAction;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class FabricListener implements AutoCloseable {
    private static final String channelName = "mychannel";
    private static final String chaincodeName = "events";

    private final Network network;
    private final Contract contract;
    private final String assetId = "asset" + Instant.now().toEpochMilli();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public static void main(final String[] args) throws Exception {
        var grpcChannel = Connections.newGrpcConnection();
        var builder = Gateway.newInstance()
                .identity(Connections.newIdentity())
                .signer(Connections.newSigner())
                .hash(Hash.SHA256)
                .connection(grpcChannel)
                .evaluateOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                .endorseOptions(options -> options.withDeadlineAfter(15, TimeUnit.SECONDS))
                .submitOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                .commitStatusOptions(options -> options.withDeadlineAfter(1, TimeUnit.MINUTES));

        try (var gateway = builder.connect(); var app = new FabricListener(gateway)) {
            app.run();
        } finally {
            grpcChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    public FabricListener(final Gateway gateway) {
        network = gateway.getNetwork(channelName);
        contract = network.getContract(chaincodeName);
    }

    public void run() throws EndorseException, SubmitException, CommitStatusException, CommitException {
        // Listen for events emitted by subsequent transactions, stopping when the try-with-resources block exits
       //  try (var eventSession = startChaincodeEventListening()) {
       //      var firstBlockNumber = createAsset();
       //      updateAsset();
       //      transferAsset();
       //      deleteAsset();

       //      // Replay events from the block containing the first transaction
       //      replayChaincodeEvents(firstBlockNumber);
       //  }

       try (CloseableIterator<Block> blockEvents = network.getBlockEvents()) {
           var firstBlockNumber = createAsset();
           updateAsset();
           transferAsset();
           deleteAsset();
           blockEvents.forEachRemaining(blockProto -> {

               System.out.println("<------data------->");
               com.chain.parser.Block block = BlockParser.parseBlock(blockProto);
               try {
                   List<com.chain.parser.Transaction> transactions = block.getTransactions();
                   if (transactions.isEmpty()) return;

                   List<NamespaceReadWriteSet> namespaceReadWriteSets = transactions.get(0).getNamespaceReadWriteSets();
                   namespaceReadWriteSets.forEach(namespaceReadWriteSet -> {
                       try {
                           List<KVWrite> writesList = namespaceReadWriteSet.getReadWriteSet().getWritesList();
                           writesList.forEach(write -> {
                               System.out.println("key :" + write.getKey() + " val: " + write.getValue().toStringUtf8());
                           });
                       } catch (InvalidProtocolBufferException e) {
                           throw new RuntimeException(e);
                       }
                   });

                   System.out.println(transactions.get(0).getNamespaceReadWriteSets());
               } catch (InvalidProtocolBufferException e) {
                   // TODO Auto-generated catch block
                   e.printStackTrace();
               }
               System.out.println("<------end data------->");
           });
       }
    }

    public static void parseBlock(Block block) {
       for (int i = 0; i < block.getData().getDataCount(); i++) {
           try {
               // 解析 Envelope
               Envelope envelope = Envelope.parseFrom(block.getData().getData(i));
               Payload payload = Payload.parseFrom(envelope.getPayload());

               // 解析 Transaction
               Transaction transaction = Transaction.parseFrom(payload.getData());
               List<TransactionAction> actionsList = transaction.getActionsList();
               actionsList.forEach(action -> {
                   try {
                       // 获取 ChaincodeActionPayload
                       ChaincodeActionPayload chaincodeActionPayload = ChaincodeActionPayload.parseFrom(action.getPayload());

                       // 获取 ChaincodeAction
                       ChaincodeAction chaincodeAction = ChaincodeAction.parseFrom(chaincodeActionPayload.getAction().getProposalResponsePayload());

                       // 解析读写集
                       TxReadWriteSet rwSet = TxReadWriteSet.parseFrom(chaincodeAction.getResults());
                       for (NsReadWriteSet nsRwSet : rwSet.getNsRwsetList()) {
                           System.out.println("Namespace: " + nsRwSet.getNamespace());
                           KVRWSet kvRwSet = KVRWSet.parseFrom(nsRwSet.getRwset());
                           for (KVWrite write : kvRwSet.getWritesList()) {
                               String key = write.getKey();
                               String value = write.getValue().toStringUtf8();
                               System.out.println("Key: " + key + ", Value: " + value);
                           }
                       }
                   } catch (InvalidProtocolBufferException e) {
                       e.printStackTrace();
                   }
               });
           } catch (InvalidProtocolBufferException e) {
               e.printStackTrace();
           }
       }
   }

    private CloseableIterator<ChaincodeEvent> startChaincodeEventListening() {
        System.out.println("\n*** Start chaincode event listening");

        var eventIter = network.getChaincodeEvents(chaincodeName);
        executor.execute(() -> readEvents(eventIter));

        return eventIter;
    }

    private void readEvents(final CloseableIterator<ChaincodeEvent> eventIter) {
        try {
            eventIter.forEachRemaining(event -> {
                var payload = prettyJson(event.getPayload());
                System.out.println("\n<-- Chaincode event received: " + event.getEventName() + " - " + payload);
            });
        } catch (GatewayRuntimeException e) {
            if (e.getStatus().getCode() != Status.Code.CANCELLED) {
                throw e;
            }
        }
    }

    private String prettyJson(final byte[] json) {
        return prettyJson(new String(json, StandardCharsets.UTF_8));
    }

    private String prettyJson(final String json) {
        var parsedJson = JsonParser.parseString(json);
        return gson.toJson(parsedJson);
    }

    private long createAsset() throws EndorseException, SubmitException, CommitStatusException {
        System.out.println("\n--> Submit transaction: CreateAsset, " + assetId + " owned by Sam with appraised value 100");

        var commit = contract.newProposal("CreateAsset")
                .addArguments(assetId, "blue", "10", "Sam", "100")
                .build()
                .endorse()
                .submitAsync();

        var status = commit.getStatus();
        if (!status.isSuccessful()) {
            throw new RuntimeException("failed to commit transaction with status code " + status.getCode());
        }

        System.out.println("\n*** CreateAsset committed successfully");

        return status.getBlockNumber();
    }

    private void updateAsset() throws EndorseException, SubmitException, CommitStatusException, CommitException {
        System.out.println("\n--> Submit transaction: UpdateAsset, " + assetId + " update appraised value to 200");

        contract.submitTransaction("UpdateAsset", assetId, "blue", "10", "Sam", "200");

        System.out.println("\n*** UpdateAsset committed successfully");
    }

    private void transferAsset() throws EndorseException, SubmitException, CommitStatusException, CommitException {
        System.out.println("\n--> Submit transaction: TransferAsset, " + assetId + " to Mary");

        contract.submitTransaction("TransferAsset", assetId, "Mary");

        System.out.println("\n*** TransferAsset committed successfully");
    }

    private void deleteAsset() throws EndorseException, SubmitException, CommitStatusException, CommitException {
        System.out.println("\n--> Submit transaction: DeleteAsset, " + assetId);

        contract.submitTransaction("DeleteAsset", assetId);

        System.out.println("\n*** DeleteAsset committed successfully");
    }

    private void replayChaincodeEvents(final long startBlock) {
        System.out.println("\n*** Start chaincode event replay");

        var request = network.newChaincodeEventsRequest(chaincodeName)
                .startBlock(startBlock)
                .build();

        try (var eventIter = request.getEvents()) {
            while (eventIter.hasNext()) {
                var event = eventIter.next();
                var payload = prettyJson(event.getPayload());
                System.out.println("\n<-- Chaincode event replayed: " + event.getEventName() + " - " + payload);

                if (event.getEventName().equals("DeleteAsset")) {
                    // Reached the last submitted transaction so break to close the iterator and stop listening for events
                    break;
                }
            }
        }
    }

    @Override
    public void close() throws Exception {
        executor.shutdownNow();
    }
}
