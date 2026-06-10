package com.cooperativesolutionism.nmsci.support;

import com.cooperativesolutionism.nmsci.constant.BlockConstants;
import com.cooperativesolutionism.nmsci.model.BlockInfo;
import com.cooperativesolutionism.nmsci.repository.BlockInfoRepository;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class DatabaseTestHelper {

    private static final String TRUNCATE_SQL = """
            TRUNCATE TABLE
                central_pubkey_empower_msgs,
                central_pubkey_locked_msgs,
                flow_node_locked_msgs,
                flow_node_register_msgs,
                transaction_mount_msgs,
                transaction_record_msgs,
                block_infos,
                msg_abstracts,
                consume_chains,
                consume_chain_edges
            RESTART IDENTITY CASCADE
            """;

    private final JdbcTemplate jdbcTemplate;
    private final BlockInfoRepository blockInfoRepository;

    public DatabaseTestHelper(JdbcTemplate jdbcTemplate, BlockInfoRepository blockInfoRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.blockInfoRepository = blockInfoRepository;
    }

    public void resetWithLatestBlock(int registerDifficultyNbits, int transactionDifficultyNbits, byte[] centralPubkey) {
        jdbcTemplate.execute(TRUNCATE_SQL);
        blockInfoRepository.save(latestBlock(registerDifficultyNbits, transactionDifficultyNbits, centralPubkey));
    }

    private static BlockInfo latestBlock(int registerDifficultyNbits, int transactionDifficultyNbits, byte[] centralPubkey) {
        BlockInfo blockInfo = new BlockInfo();
        byte[] blockId = new byte[32];
        Arrays.fill(blockId, (byte) 1);

        blockInfo.setId(blockId);
        blockInfo.setVersion(1);
        blockInfo.setHeight(0L);
        blockInfo.setSourceCodeZipHash(new byte[32]);
        blockInfo.setPreviousBlockHash(ByteArrayUtil.hexToBytes(BlockConstants.GENESIS_HASH));
        blockInfo.setMerkleRoot(new byte[32]);
        blockInfo.setMaxMsgTimestamp(0L);
        blockInfo.setRegisterDifficultyTarget(registerDifficultyNbits);
        blockInfo.setTransactionDifficultyTarget(transactionDifficultyNbits);
        blockInfo.setCentralPubkey(centralPubkey);
        blockInfo.setTimestamp(1L);
        blockInfo.setCentralSignature(new byte[64]);
        blockInfo.setDatFilepath("blk00000000.dat");
        blockInfo.setSourceCodeZipFilepath("source_code_v1.zip");
        blockInfo.setRawBytes(new byte[229]);
        return blockInfo;
    }
}
