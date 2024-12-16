package com.chain.config.fabric;

import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.Hash;
import org.hyperledger.fabric.client.Network;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

@Configuration
public class FabricConfiguration {
    private static final String channelName = "mychannel";
    private static final String chaincodeName = "events";

    @Bean
    public Network FabricGateway() throws IOException, CertificateException, InvalidKeyException {
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
        var gateway = builder.connect();
        var network = gateway.getNetwork(channelName);
        return network;
    }
}
