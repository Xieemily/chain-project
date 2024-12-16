/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.chain.parser;

public final class BlockParser {
    public static Block parseBlock(final org.hyperledger.fabric.protos.common.Block block) {
        return new ParsedBlock(block);
    }

    private BlockParser() { }
}
