package com.chain.listener;/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

 import com.chain.mq.producer.ChainEventProducer;
 import com.google.protobuf.InvalidProtocolBufferException;

 import com.chain.parser.BlockParser;

 import lombok.RequiredArgsConstructor;
 import org.hyperledger.fabric.client.CloseableIterator;
 import org.hyperledger.fabric.client.Network;
 import org.hyperledger.fabric.protos.common.Block;
 import org.hyperledger.fabric.protos.ledger.rwset.kvrwset.KVWrite;
 import com.chain.parser.NamespaceReadWriteSet;
 import org.springframework.boot.CommandLineRunner;
 import org.springframework.stereotype.Component;

 import java.util.Date;
 import java.util.HashMap;
 import java.util.List;
 import java.util.Map;

@Component
@RequiredArgsConstructor
 public class FabricBlockListener implements CommandLineRunner {

    public final Network fabricNetwork;
    public final ChainEventProducer chainEventProducer;

     @Override
     public void run(String... args) throws Exception {
         try (CloseableIterator<Block> blockEvents = fabricNetwork.getBlockEvents()) {
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
                                 Map<String, String> mp = new HashMap<>();
                                 mp.put("key", write.getKey());
                                 mp.put("value", write.getValue().toStringUtf8());
                                 mp.put("chainType", "fabric");
                                 mp.put("updateTime", new Date().toString());
                                 chainEventProducer.send(mp);
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

 }
