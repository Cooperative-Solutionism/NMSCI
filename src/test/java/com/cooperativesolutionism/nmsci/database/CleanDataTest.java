package com.cooperativesolutionism.nmsci.database;

import com.cooperativesolutionism.nmsci.NmsciApplication;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@Disabled
@SpringBootTest(classes = NmsciApplication.class)
public class CleanDataTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void cleanData() {
        // 删除所有表中的数据
        jdbcTemplate.execute("TRUNCATE TABLE flow_node_register_msgs CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE central_pubkey_empower_msgs CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE central_pubkey_locked_msgs CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE flow_node_locked_msgs CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE transaction_mount_msgs CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE transaction_record_msgs CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE block_infos CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE msg_abstracts CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE consume_chains CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE consume_chain_edges CASCADE");
    }
}
