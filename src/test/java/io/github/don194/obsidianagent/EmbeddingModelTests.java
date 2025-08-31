package io.github.don194.obsidianagent;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用于测试 EmbeddingModel 是否配置成功并能正常工作的单元测试。
 */
@SpringBootTest
@ActiveProfiles("dev")
class EmbeddingModelTests {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingModelTests.class);

    @Autowired
    private EmbeddingModel embeddingModel;

    @Test
    void embeddingModelShouldWork() {
        assertThat(embeddingModel).isNotNull();
        logger.info("EmbeddingModel bean successfully injected.");

        String testText = "你好，这是一个测试。";
        logger.info("Testing embedding for text: '{}'", testText);

        EmbeddingResponse embeddingResponse = embeddingModel.embedForResponse(List.of(testText));

        assertThat(embeddingResponse).isNotNull();
        assertThat(embeddingResponse.getResults()).isNotNull().hasSize(1);

        Embedding embedding = embeddingResponse.getResult();

        // BUG 修复：将 List<Double> 修改为 float[]
        float[] vector = embedding.getOutput();

        assertThat(vector).isNotNull();

        // BUG 修复：将 vector.size() 修改为 vector.length
        logger.info("Successfully received vector with dimension: {}", vector.length);

        // 断言向量维度大于0，证明成功生成
        assertThat(vector.length).isGreaterThan(0);

        logger.info("✅ EmbeddingModel test passed! Your mirror site API supports embedding.");
    }
}