package ru.zahaand.lifesync.infrastructure.event.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic habitLogCompletedTopic() {
        return TopicBuilder.name("habit.log.completed")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic habitLogCompletedDlqTopic() {
        return TopicBuilder.name("habit.log.completed.dlq")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic goalProgressUpdatedTopic() {
        return TopicBuilder.name("goal.progress.updated")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic goalProgressUpdatedDlqTopic() {
        return TopicBuilder.name("goal.progress.updated.dlq")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
