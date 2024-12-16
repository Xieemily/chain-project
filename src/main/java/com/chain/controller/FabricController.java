package com.chain.controller;

import com.chain.service.FabricService;
import lombok.RequiredArgsConstructor;
import org.hyperledger.fabric.client.Network;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FabricController {

    public final FabricService fabricService;

    @PutMapping("/fabric/add")
    public void addAsset() {
        fabricService.addAsset();
    }
}
