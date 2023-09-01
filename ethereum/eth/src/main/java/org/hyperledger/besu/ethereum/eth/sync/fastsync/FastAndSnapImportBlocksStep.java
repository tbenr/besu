package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import okhttp3.internal.http2.Header;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

public class FastAndSnapImportBlocksStep implements Function<List<BlockWithReceipts>, CompletableFuture<BlockHeader>> {

    private final MutableBlockchain blockchain;

    public FastAndSnapImportBlocksStep(MutableBlockchain blockchain) {
        this.blockchain = blockchain;
    }

    @Override

    public CompletableFuture<BlockHeader> apply(List<BlockWithReceipts> blocksWithReceipts) {

        for (BlockWithReceipts blockWithReceipts:blocksWithReceipts) {
            try {
                blockchain.unsafeImportBlock(
                        blockWithReceipts.getBlock(),
                        blockWithReceipts.getReceipts(),
                        Optional.empty());
            } catch (Exception ex) {
                return CompletableFuture.completedFuture(blockWithReceipts.getHeader());
            }

        }
    }
}
